package kg.aidarbek.smpp.codec;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import kg.aidarbek.smpp.protocol.PduHeader;

/**
 * Thread-confined, incremental SMPP frame assembly without command-body parsing or socket ownership.
 *
 * <p>Retains at most one configured-size frame. Only four length octets are collected before validating the unsigned
 * length; body storage is allocated only after that check. A read emits at most one frame, so callers control admission
 * and never receive a hidden queue of coalesced frames. Accepted bytes are copied; completed arrays belong to the caller.
 * A malformed length or end of input permanently terminates this instance.
 */
public final class PduFramer {
    private final int maximumFrameLength;
    private byte[] buffer = new byte[4];
    private int received;
    private boolean terminated;

    /**
     * Creates a framer with an explicit allocation limit.
     *
     * @param maximumFrameLength maximum total PDU size in octets, including the header
     * @throws IllegalArgumentException if the maximum is below 16
     */
    public PduFramer(int maximumFrameLength) {
        if (maximumFrameLength < PduHeader.LENGTH) {
            throw new IllegalArgumentException("maximumFrameLength must be at least 16 octets");
        }
        this.maximumFrameLength = maximumFrameLength;
    }

    /**
     * Consumes bytes until one frame completes or the source reaches its limit.
     *
     * <p>Repeat with the same source to retrieve further complete frames. An empty result means all available bytes were
     * consumed and retained for the next read. Source byte order and limit are unchanged; read-only and direct buffers
     * are supported. Invalid length consumes exactly through its fourth octet and releases retained storage.
     *
     * @param source non-null buffer whose position advances by the bytes consumed
     * @return an independently owned complete frame, including its header, or empty for incomplete input
     * @throws IllegalArgumentException if the length is below 16 or above the configured maximum
     * @throws IllegalStateException if this framer has terminated
     * @throws NullPointerException if source is null
     */
    public Optional<byte[]> read(ByteBuffer source) {
        Objects.requireNonNull(source, "source");
        if (terminated) {
            throw new IllegalStateException("framer is terminated");
        }
        if (buffer.length == 4) {
            transfer(source);
            if (received < 4) {
                return Optional.empty();
            }
            long length = Integer.toUnsignedLong(ByteBuffer.wrap(buffer).getInt());
            if (length < PduHeader.LENGTH || length > maximumFrameLength) {
                terminated = true;
                buffer = null;
                throw new IllegalArgumentException("command_length is outside the configured frame bounds: " + length);
            }
            buffer = Arrays.copyOf(buffer, (int) length);
        }
        transfer(source);
        if (received < buffer.length) {
            return Optional.empty();
        }
        byte[] frame = buffer;
        buffer = new byte[4];
        received = 0;
        return Optional.of(frame);
    }

    /**
     * Terminates and releases retained storage; repeated calls are harmless.
     *
     * <p>Call only after draining all supplied buffers. An empty stream or complete frame boundary ends successfully.
     *
     * @throws IllegalArgumentException if the first termination encounters an incomplete header or body
     */
    public void endOfInput() {
        if (terminated) {
            return;
        }
        terminated = true;
        buffer = null;
        if (received != 0) {
            throw new IllegalArgumentException("input ended with an incomplete PDU");
        }
    }

    private void transfer(ByteBuffer source) {
        int count = Math.min(source.remaining(), buffer.length - received);
        source.get(buffer, received, count);
        received += count;
    }
}
