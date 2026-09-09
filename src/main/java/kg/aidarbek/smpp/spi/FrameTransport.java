package kg.aidarbek.smpp.spi;

import java.util.concurrent.CompletionStage;

/**
 * Session-owned complete-frame I/O port, independent of sockets and protocol request tracking.
 * All callbacks are internal and must return promptly without blocking or invoking application code.
 */
public interface FrameTransport extends AutoCloseable {
    /**
     * Installs the sole listener and starts I/O; may be called once, before closure.
     * @param listener internal lifecycle and frame consumer
     * @throws IllegalStateException if already started or closed
     */
    void start(FrameListener listener);

    /**
     * Admits an owned copy without waiting for capacity or I/O. Counts and byte limits include queued
     * and in-flight frames, with separate finite control capacity. Ordinary writes remain FIFO;
     * control writes may overtake queued ordinary writes. The absolute deadline uses System.nanoTime()
     * with a positive future offset below 2^63 nanoseconds on that same origin. Rejection throws before ownership
     * transfer and never calls the observer. Accepted writes have exactly one written/failed callback.
     * No write is accepted before start or after closure. Control traffic cannot preempt an active
     * physical write. Deadline expiry during that write closes the stream to stop further bytes.
     * @param frame complete frame with matching unsigned length prefix; the caller retains ownership
     * @param writeClass ordinary or separately reserved control capacity
     * @param deadlineNanos absolute deadline on the System.nanoTime origin
     * @param observer internal transmission guard and terminal consumer
     * @return cancellation handle for accepted work
     * @throws IllegalArgumentException if the frame length or its prefix is invalid
     * @throws TransportFailure if closed, not started, expired, or the selected capacity is full
     */
    WriteHandle write(byte[] frame, WriteClass writeClass, long deadlineNanos, WriteObserver observer);

    /**
     * Returns a stage after physical close, I/O worker termination and internal callback completion.
     * Connection failure is delivered to the listener; successful cleanup still completes normally.
     * A failed physical close or terminal callback completes exceptionally with CLEANUP_FAILED and
     * its first retained local cause. External dependent actions cannot postpone owned I/O cleanup.
     * @return read-only cleanup result, unaffected by completion or cancellation of a derived future
     */
    CompletionStage<Void> termination();

    /** Aborts pending and active I/O idempotently without waiting for callback dependents. */
    @Override
    void close();
}
