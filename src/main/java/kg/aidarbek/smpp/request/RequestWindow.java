package kg.aidarbek.smpp.request;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.ControlCommand;
import kg.aidarbek.smpp.protocol.Pdu;

/**
 * Thread-safe, bounded local request correlation for one immutable connection generation.
 *
 * <p>The endpoint must allocate a fresh generation for every connection and create exactly one local
 * window for it. Peer-initiated requests occupy a separate namespace and are never admitted here.
 * Sequence numbers increase from one through {@code 0x7fffffff}; exhaustion requires a new connection.
 * This type neither enforces session permissions nor encodes, writes, retries or schedules requests.
 *
 * <p>The supplied clock must be monotonic, thread-safe, fast and nonblocking, normally
 * {@link System#nanoTime()}. Signed clock values and wraparound are supported; every observed elapsed
 * interval must remain below half the unsigned long range. The endpoint must call {@link #expire()}
 * regularly, or deadlines with no other activity cannot be delivered. Due deadlines take precedence
 * at matching response, before-write, cancellation, write-failure and disconnect transitions.
 *
 * <p>Terminal settlement releases pending count and byte capacity atomically. Application completion
 * runs outside the window lock on the bounded notification dispatcher. Its reservation remains held
 * until synchronous dependent callbacks return. A shared dispatcher also bounds retained completion
 * work across connections. No method waits for application callbacks except the explicitly bounded
 * {@link #awaitNotifications(Duration)}. Invalid arguments fail before reserving capacity.
 */
public final class RequestWindow implements AutoCloseable {
    private final UUID generation;
    private final int maxPending;
    private final long maxBytes;
    private final BoundedNotifications notifications;
    private final boolean ownsNotifications;
    private final LongSupplier nanoClock;
    private final Map<Long, RequestHandle<?>> pending = new LinkedHashMap<>();
    private long nextSequence = 1;
    private long pendingBytes;
    private boolean closed;

    /**
     * Creates a window using caller-owned shared completion workers.
     * @param generation connection identity
     * @param maxPending positive pending count limit
     * @param maxBytes pending frame-byte limit, at least 16
     * @param notifications shared dispatcher
     * @param nanoClock monotonic nonblocking time source
     * @throws NullPointerException if a reference is null
     * @throws IllegalArgumentException if a capacity is outside its documented range
     */
    public RequestWindow(
            UUID generation,
            int maxPending,
            long maxBytes,
            BoundedNotifications notifications,
            LongSupplier nanoClock) {
        this(generation, maxPending, maxBytes, notifications, nanoClock, false);
    }

    RequestWindow(
            UUID generation,
            int maxPending,
            long maxBytes,
            BoundedNotifications notifications,
            LongSupplier nanoClock,
            long initialSequence) {
        this(generation, maxPending, maxBytes, notifications, nanoClock, false);
        new RequestIdentity(generation, initialSequence);
        nextSequence = initialSequence;
    }
    /**
     * Creates a window with one owned completion worker.
     * @param generation connection identity
     * @param maxPending positive pending count limit
     * @param maxBytes pending frame-byte limit, at least 16
     * @param maxNotifications notification reservations
     * @param nanoClock monotonic nonblocking time source
     * @throws NullPointerException if a reference is null
     * @throws IllegalArgumentException if a capacity is outside its documented range
     */
    public RequestWindow(UUID generation, int maxPending, long maxBytes, int maxNotifications, LongSupplier nanoClock) {
        this(
                generation,
                maxPending,
                maxBytes,
                ownedNotifications(generation, maxPending, maxBytes, maxNotifications, nanoClock),
                nanoClock,
                true);
    }

    private RequestWindow(
            UUID generation,
            int maxPending,
            long maxBytes,
            BoundedNotifications notifications,
            LongSupplier nanoClock,
            boolean ownsNotifications) {
        validateConfiguration(generation, maxPending, maxBytes, nanoClock);
        Objects.requireNonNull(notifications, "notifications");
        this.generation = generation;
        this.maxPending = maxPending;
        this.maxBytes = maxBytes;
        this.notifications = notifications;
        this.nanoClock = nanoClock;
        this.ownsNotifications = ownsNotifications;
    }

    private static BoundedNotifications ownedNotifications(
            UUID generation, int maxPending, long maxBytes, int maxNotifications, LongSupplier nanoClock) {
        validateConfiguration(generation, maxPending, maxBytes, nanoClock);
        return new BoundedNotifications(maxNotifications, 1);
    }

    private static void validateConfiguration(UUID generation, int maxPending, long maxBytes, LongSupplier nanoClock) {
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(nanoClock, "nanoClock");
        if (maxPending <= 0 || maxBytes < 16) {
            throw new IllegalArgumentException("Require positive pending capacity and at least 16 frame bytes");
        }
    }
    /**
     * Admits using this invocation's start tick.
     * @param requestCommandId nonzero request identity in 1..0x7fffffff
     * @param expectedResponseCommandId request identity with bit 31 set
     * @param responseType representation expected from the decoder for this response command
     * @param frameBytes exact encoded request size in 16..0xffffffff octets
     * @param options total timeout
     * @param <R> response type
     * @return admitted handle; no sequence is consumed when admission fails
     * @throws NullPointerException if options or responseType is null
     * @throws IllegalArgumentException if command identities or frame size are invalid
     * @throws RequestFailure for closed admission, an elapsed deadline, sequence exhaustion or a full bound
     */
    public <R extends Command> RequestHandle<R> admit(
            long requestCommandId,
            long expectedResponseCommandId,
            Class<R> responseType,
            long frameBytes,
            RequestOptions options) {
        return admit(
                requestCommandId, expectedResponseCommandId, responseType, frameBytes, options, nanoClock.getAsLong());
    }
    /**
     * Admits with an earlier invocation tick from the same clock; validation and admission consume the timeout.
     * @param requestCommandId nonzero request identity in 1..0x7fffffff
     * @param expectedResponseCommandId request identity with bit 31 set
     * @param responseType representation expected from the decoder for this response command
     * @param frameBytes exact encoded request size in 16..0xffffffff octets
     * @param options total timeout
     * @param invocationStartNanos original clock tick
     * @param <R> response type
     * @return admitted handle; no sequence is consumed when admission fails
     * @throws NullPointerException if options or responseType is null
     * @throws IllegalArgumentException if command identities or frame size are invalid
     * @throws RequestFailure for closed admission, an elapsed deadline, sequence exhaustion or a full bound
     */
    public synchronized <R extends Command> RequestHandle<R> admit(
            long requestCommandId,
            long expectedResponseCommandId,
            Class<R> responseType,
            long frameBytes,
            RequestOptions options,
            long invocationStartNanos) {
        Objects.requireNonNull(responseType, "responseType");
        Objects.requireNonNull(options, "options");
        if (requestCommandId < 1
                || requestCommandId > 0x7fffffffL
                || expectedResponseCommandId != (requestCommandId | 0x80000000L)) {
            throw new IllegalArgumentException("Expected the exact response ID for a nonzero request command");
        }
        if (frameBytes < 16 || frameBytes > 0xffffffffL) {
            throw new IllegalArgumentException("Encoded request size must be in 16..0xffffffff bytes");
        }
        if (closed) {
            throw admissionFailure(RequestFailure.Reason.CLOSED, requestCommandId);
        }
        long deadline = invocationStartNanos + options.timeout().toNanos();
        if (nanoClock.getAsLong() - deadline >= 0) {
            throw admissionFailure(RequestFailure.Reason.DEADLINE_EXPIRED, requestCommandId);
        }
        if (nextSequence > 0x7fffffffL) {
            throw admissionFailure(RequestFailure.Reason.SEQUENCE_EXHAUSTED, requestCommandId);
        }
        if (pending.size() == maxPending) {
            throw admissionFailure(RequestFailure.Reason.WINDOW_FULL, requestCommandId);
        }
        if (frameBytes > maxBytes - pendingBytes) {
            throw admissionFailure(RequestFailure.Reason.BYTE_LIMIT, requestCommandId);
        }
        var reserved = notifications
                .tryReserve()
                .orElseThrow(() -> admissionFailure(RequestFailure.Reason.NOTIFICATION_BACKLOG, requestCommandId));
        var identity = new RequestIdentity(generation, nextSequence);
        var handle = new RequestHandle<>(
                this,
                identity,
                requestCommandId,
                expectedResponseCommandId,
                responseType,
                frameBytes,
                deadline,
                reserved);
        pending.put(identity.sequenceNumber(), handle);
        pendingBytes += frameBytes;
        nextSequence++;
        return handle;
    }

    private RequestFailure admissionFailure(RequestFailure.Reason reason, long requestCommandId) {
        return new RequestFailure(reason, generation, 0, requestCommandId, TransmissionCertainty.NOT_SENT, null);
    }
    /**
     * Returns pending metadata for a local sequence. The snapshot is not proof of a later successful
     * correlation; only {@link #accept(UUID, Pdu)} consumes a matched request.
     * @param sequenceNumber local sequence
     * @return pending handle
     */
    public synchronized Optional<RequestHandle<?>> pending(long sequenceNumber) {
        return Optional.ofNullable(pending.get(sequenceNumber));
    }
    /**
     * Returns the connection generation.
     * @return immutable generation
     */
    public UUID generation() {
        return generation;
    }
    /**
     * Returns occupied pending slots.
     * @return count
     */
    public synchronized int pendingCount() {
        return pending.size();
    }
    /**
     * Returns occupied request bytes.
     * @return bytes
     */
    public synchronized long pendingBytes() {
        return pendingBytes;
    }
    /**
     * Returns globally reserved notifications on this window's dispatcher.
     * @return reserved, queued and executing count
     */
    public int notificationsOutstanding() {
        return notifications.outstandingCount();
    }
    /**
     * Waits within a bound for this window's dispatcher to finish closed admission and all reserved work.
     * Close this window first when it owns the dispatcher. A supplied shared dispatcher must be closed
     * by its owner; this method then reports all of that dispatcher's connections, not just this window.
     * @param timeout wait bound
     * @return true if its workers finished
     * @throws InterruptedException if interrupted
     */
    public boolean awaitNotifications(Duration timeout) throws InterruptedException {
        return notifications.awaitTermination(timeout);
    }
    /**
     * Correlates an independently decoded and session-authorized peer response. Exact generation, local
     * sequence, expected command and representation must match. A negative generic_nack matches any
     * expected response command and produces {@link PeerNackException}; operation-specific negative
     * responses remain normal results. Wrong, duplicate, late and peer-request PDUs are not consumed.
     * @param responseGeneration connection of origin
     * @param response received response
     * @return true if the peer response won; false if unmatched or a due deadline won
     */
    public boolean accept(UUID responseGeneration, Pdu<? extends Command> response) {
        Objects.requireNonNull(responseGeneration, "responseGeneration");
        Objects.requireNonNull(response, "response");
        RequestHandle<?> handle;
        boolean expired;
        synchronized (this) {
            handle = pending.get(response.sequenceNumber());
            boolean nack = response.command() instanceof ControlCommand control
                    && control.type() == ControlCommand.Type.GENERIC_NACK
                    && response.commandStatus() != 0;
            if (!generation.equals(responseGeneration)
                    || handle == null
                    || (!nack
                            && (handle.expectedResponseCommandId
                                            != response.command().commandId()
                                    || !handle.responseType.isInstance(response.command())))) {
                return false;
            }
            expired = isExpired(handle, nanoClock.getAsLong());
            if (expired) {
                settleFailure(handle, RequestFailure.Reason.DEADLINE_EXPIRED, null);
            } else if (nack) {
                settle(
                        handle,
                        RequestOutcome.failed(new PeerNackException(
                                handle.identity(),
                                new Pdu<>(response.commandStatus(), response.sequenceNumber(), (ControlCommand)
                                        response.command()))));
            } else {
                settleResponse(handle, response);
            }
        }
        handle.dispatchCompletion();
        return !expired;
    }

    private <R extends Command> void settleResponse(RequestHandle<R> handle, Pdu<? extends Command> response) {
        settle(
                handle,
                RequestOutcome.responded(new Pdu<>(
                        response.commandStatus(),
                        response.sequenceNumber(),
                        handle.responseType.cast(response.command()))));
    }

    private <R extends Command> void settle(RequestHandle<R> handle, RequestOutcome<R> outcome) {
        pending.remove(handle.identity().sequenceNumber());
        pendingBytes -= handle.frameBytes;
        handle.outcome = outcome;
    }
    /**
     * Authorizes at most one physical write before its deadline. Transport must invoke this guard
     * immediately before its first physical write and skip the write when it returns false. Returning
     * true atomically establishes MAY_HAVE_BEEN_SENT, even if the later write fails before any bytes
     * escape. Successful local writing never settles a request or proves peer acceptance.
     * @param handle admitted handle
     * @return true if writing is allowed; false after settlement, expiry or previous authorization
     * @throws NullPointerException if handle is null
     * @throws IllegalArgumentException if handle belongs to another window
     */
    public boolean beginWrite(RequestHandle<?> handle) {
        synchronized (this) {
            requireOwned(handle);
            if (handle.isDone()) {
                return false;
            }
            if (isExpired(handle, nanoClock.getAsLong())) {
                settleFailure(handle, RequestFailure.Reason.DEADLINE_EXPIRED, null);
            } else {
                if (handle.transmission != TransmissionCertainty.NOT_SENT) {
                    return false;
                }
                handle.transmission = TransmissionCertainty.MAY_HAVE_BEEN_SENT;
                return true;
            }
        }
        handle.dispatchCompletion();
        return false;
    }
    /**
     * Settles transport rejection or write failure. Certainty comes from the before-write guard,
     * never from an assumption about the transport exception. A due deadline settles instead.
     * @param handle admitted handle
     * @param cause transport failure
     * @return true if this write failure won; false if another terminal outcome won
     * @throws NullPointerException if handle or cause is null
     * @throws IllegalArgumentException if handle belongs to another window
     */
    public boolean failWrite(RequestHandle<?> handle, Throwable cause) {
        Objects.requireNonNull(cause, "cause");
        return fail(handle, RequestFailure.Reason.WRITE_FAILED, cause);
    }

    boolean cancel(RequestHandle<?> handle) {
        return fail(handle, RequestFailure.Reason.CANCELLED, null);
    }

    private boolean fail(RequestHandle<?> handle, RequestFailure.Reason reason, Throwable cause) {
        boolean expired;
        synchronized (this) {
            requireOwned(handle);
            if (handle.isDone()) {
                return false;
            }
            expired = isExpired(handle, nanoClock.getAsLong());
            settleFailure(handle, expired ? RequestFailure.Reason.DEADLINE_EXPIRED : reason, expired ? null : cause);
        }
        handle.dispatchCompletion();
        return !expired;
    }

    private void requireOwned(RequestHandle<?> handle) {
        Objects.requireNonNull(handle, "handle");
        if (handle.owner != this) {
            throw new IllegalArgumentException("Request belongs to another window");
        }
    }

    private void settleFailure(RequestHandle<?> handle, RequestFailure.Reason reason, Throwable cause) {
        settle(
                handle,
                RequestOutcome.failed(new RequestFailure(
                        reason,
                        generation,
                        handle.identity().sequenceNumber(),
                        handle.requestHeader().commandId(),
                        handle.transmission,
                        cause)));
    }
    /**
     * Expires pending requests against the injected clock.
     * @return number expired
     */
    public int expire() {
        ArrayList<RequestHandle<?>> expired = new ArrayList<>();
        synchronized (this) {
            long now = nanoClock.getAsLong();
            for (RequestHandle<?> handle : pending.values()) {
                if (isExpired(handle, now)) {
                    expired.add(handle);
                }
            }
            expired.forEach(handle -> settleFailure(handle, RequestFailure.Reason.DEADLINE_EXPIRED, null));
        }
        expired.forEach(RequestHandle::dispatchCompletion);
        return expired.size();
    }

    private static boolean isExpired(RequestHandle<?> handle, long now) {
        return now - handle.deadlineNanos() >= 0;
    }
    /**
     * Closes admission and settles all pending requests.
     * @param cause optional disconnect cause
     * @return number newly settled, including due deadlines; repeated disconnect returns zero
     */
    public int disconnect(Throwable cause) {
        ArrayList<RequestHandle<?>> disconnected;
        synchronized (this) {
            closed = true;
            disconnected = new ArrayList<>(pending.values());
            long now = nanoClock.getAsLong();
            disconnected.forEach(handle -> {
                boolean expired = isExpired(handle, now);
                settleFailure(
                        handle,
                        expired ? RequestFailure.Reason.DEADLINE_EXPIRED : RequestFailure.Reason.DISCONNECTED,
                        expired ? null : cause);
            });
        }
        disconnected.forEach(RequestHandle::dispatchCompletion);
        return disconnected.size();
    }
    /**
     * Idempotently aborts pending work and requests shutdown of an internally owned dispatcher.
     * Does not block for callbacks, drop reserved notifications or close a caller-owned dispatcher.
     * Use {@link #awaitNotifications(Duration)} and {@link #notificationsOutstanding()} for cleanup reporting.
     */
    @Override
    public void close() {
        disconnect(null);
        if (ownsNotifications) {
            notifications.close();
        }
    }
}
