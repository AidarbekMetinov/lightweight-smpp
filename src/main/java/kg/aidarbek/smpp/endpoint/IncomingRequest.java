package kg.aidarbek.smpp.endpoint;

import java.util.concurrent.atomic.AtomicBoolean;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.Pdu;

/** Immutable received request context with cooperative cancellation observation.
 * The session can close while application work is pending; cancellation never forcibly interrupts application code.
 * @param <Q> immutable decoded request representation */
public final class IncomingRequest<Q extends Command> {
    private final BoundSession session;
    private final Pdu<Q> pdu;
    private final long deadlineNanos;
    private final AtomicBoolean cancelled;

    IncomingRequest(BoundSession session, Pdu<Q> pdu, long deadlineNanos, AtomicBoolean cancelled) {
        this.session = session;
        this.pdu = pdu;
        this.deadlineNanos = deadlineNanos;
        this.cancelled = cancelled;
    }
    /** Returns the owning session facade; its lifecycle remains independently observable.
     * @return session that received this request */
    public BoundSession session() {
        return session;
    }
    /** Returns immutable command fields and the peer's separate sequence identity.
     * @return received PDU, without payload interpretation or acceptance inference */
    public Pdu<Q> pdu() {
        return pdu;
    }
    /** Returns the acknowledgement decision deadline in {@link System#nanoTime()} units.
     * @return absolute monotonic deadline; compare by subtraction to tolerate wraparound */
    public long deadlineNanos() {
        return deadlineNanos;
    }
    /** Returns whether timeout or connection closure has cancelled the application result.
     * A late decision is suppressed; physical work remains counted until invocation and stage completion.
     * @return cooperative cancellation observation */
    public boolean isCancelled() {
        return cancelled.get();
    }
}
