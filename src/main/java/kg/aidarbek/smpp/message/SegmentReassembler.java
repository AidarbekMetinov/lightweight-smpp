package kg.aidarbek.smpp.message;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import kg.aidarbek.smpp.protocol.OctetString;

/**
 * Thread-safe bounded fragment owner with caller-controlled monotonic time. Incomplete and completed
 * groups expire from their first admission; duplicates do not extend that deadline. Completed parts
 * remain retained for duplicate suppression and consume the same group, segment and byte bounds.
 * There is no background work, decoding, reference allocation or network access. The clock must be
 * prompt and must obey System.nanoTime subtraction semantics within the configured lifetime.
 */
public final class SegmentReassembler implements AutoCloseable {
    private final Map<ReassemblyKey, Assembly> groups = new LinkedHashMap<>();
    private final int maximumGroups;
    private final int maximumSegments;
    private final long maximumBytes;
    private final int maximumMessageBytes;
    private final long lifetimeNanos;
    private final LongSupplier nanoClock;
    private int segments;
    private long bytes;
    private boolean closed;
    /**
     * Creates explicit global and per-message bounds.
     *
     * @param maximumGroups retained reference groups, including completed deduplication groups
     * @param maximumSegments retained fragment count across groups
     * @param maximumBytes retained fragment octets across groups
     * @param maximumMessageBytes maximum assembled payload size
     * @param lifetime fixed lifetime from first admission, without refresh on duplicates
     * @param nanoClock monotonic signed-wrap clock
     */
    public SegmentReassembler(
            int maximumGroups,
            int maximumSegments,
            long maximumBytes,
            int maximumMessageBytes,
            Duration lifetime,
            LongSupplier nanoClock) {
        if (maximumGroups < 1 || maximumSegments < 1 || maximumBytes < 1 || maximumMessageBytes < 1)
            throw new IllegalArgumentException("Reassembly limits must be positive");
        Objects.requireNonNull(lifetime, "lifetime");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        try {
            lifetimeNanos = lifetime.toNanos();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Reassembly lifetime exceeds monotonic range", overflow);
        }
        if (lifetimeNanos <= 0 || lifetimeNanos > Long.MAX_VALUE / 4)
            throw new IllegalArgumentException("Reassembly lifetime must be positive and within the monotonic range");
        this.maximumGroups = maximumGroups;
        this.maximumSegments = maximumSegments;
        this.maximumBytes = maximumBytes;
        this.maximumMessageBytes = maximumMessageBytes;
    }

    /**
     * Accepts one part and emits its complete raw payload at most once within the group's lifetime.
     *
     * @param key caller-scoped reference
     * @param segment immutable fragment
     * @return complete ordered payload only on first completion, otherwise empty
     * @throws IllegalStateException for closed admission or exhausted global capacity
     * @throws IllegalArgumentException for conflicting metadata/content or an exceeded message limit
     * @throws NullPointerException for missing key or segment
     */
    public synchronized Optional<OctetString> accept(ReassemblyKey key, MessageSegment segment) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(segment, "segment");
        if (key.reference() != segment.reference())
            throw new IllegalArgumentException("Reference does not match reassembly key");
        if (closed) throw new IllegalStateException("Reassembler is closed");
        long now = nanoClock.getAsLong();
        expireAt(now);
        Assembly assembly = groups.get(key);
        int index = segment.number() - 1;
        if (assembly != null) {
            if (assembly.parts.length != segment.total())
                throw new IllegalArgumentException("Conflicting segment total");
            if (assembly.parts[index] != null) {
                if (!assembly.parts[index].equals(segment.payload()))
                    throw new IllegalArgumentException("Conflicting duplicate fragment");
                return Optional.empty();
            }
        }
        int existingBytes = assembly == null ? 0 : assembly.bytes;
        if ((long) existingBytes + segment.payload().length() > maximumMessageBytes)
            throw new IllegalArgumentException("Assembled message exceeds its byte limit");
        if ((assembly == null && groups.size() >= maximumGroups)
                || segments >= maximumSegments
                || segment.payload().length() > maximumBytes - bytes)
            throw new IllegalStateException("Reassembly capacity is full");
        if (assembly == null) {
            assembly = new Assembly(segment.total(), now + lifetimeNanos);
            groups.put(key, assembly);
        }
        assembly.parts[index] = segment.payload();
        assembly.count++;
        assembly.bytes += segment.payload().length();
        segments++;
        bytes += segment.payload().length();
        if (assembly.count != assembly.parts.length) return Optional.empty();
        byte[] joined = new byte[assembly.bytes];
        int offset = 0;
        for (OctetString part : assembly.parts) {
            byte[] content = part.value();
            System.arraycopy(content, 0, joined, offset, content.length);
            offset += content.length;
        }
        return Optional.of(new OctetString(joined));
    }

    /**
     * Removes expired incomplete and completed groups. Acceptance also expires groups before admission.
     * The passage of time alone performs no work.
     * @return removed groups
     */
    public synchronized int expire() {
        return expireAt(nanoClock.getAsLong());
    }

    private int expireAt(long now) {
        int removed = 0;
        var iterator = groups.values().iterator();
        while (iterator.hasNext()) {
            Assembly assembly = iterator.next();
            if (now - assembly.deadline >= 0) {
                segments -= assembly.count;
                bytes -= assembly.bytes;
                iterator.remove();
                removed++;
            }
        }
        return removed;
    }
    /**
     * Returns retained groups, including completion deduplication.
     * @return group count
     */
    public synchronized int retainedGroups() {
        return groups.size();
    }
    /**
     * Returns retained fragments.
     * @return fragment count
     */
    public synchronized int retainedSegments() {
        return segments;
    }
    /**
     * Returns retained fragment octets.
     * @return byte count
     */
    public synchronized long retainedBytes() {
        return bytes;
    }
    /** Drops all retained state and closes admission idempotently. */
    @Override
    public synchronized void close() {
        closed = true;
        groups.clear();
        segments = 0;
        bytes = 0;
    }

    /** Retains one fixed-size group, including its completed deduplication history. */
    private static final class Assembly {
        private final OctetString[] parts;
        private final long deadline;
        private int count;
        private int bytes;

        Assembly(int total, long deadline) {
            parts = new OctetString[total];
            this.deadline = deadline;
        }
    }
}
