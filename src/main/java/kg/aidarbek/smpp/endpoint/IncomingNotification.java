package kg.aidarbek.smpp.endpoint;

import java.util.concurrent.atomic.AtomicBoolean;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.Pdu;

/** Immutable one-way application context; its deadline bounds local handler work, not a protocol reply.
 * @param <Q> immutable notification command */
public final class IncomingNotification<Q extends Command> {
    private final BoundSession session;
    private final Pdu<Q> pdu;
    private final long deadline;
    private final AtomicBoolean cancelled;

    IncomingNotification(BoundSession session, Pdu<Q> pdu, long deadline, AtomicBoolean cancelled) {
        this.session = session;
        this.pdu = pdu;
        this.deadline = deadline;
        this.cancelled = cancelled;
    }
    /** Returns the owning bound session.
     * @return session capability */
    public BoundSession session() {
        return session;
    }
    /** Returns the immutable notification and peer sequence.
     * @return received PDU */
    public Pdu<Q> pdu() {
        return pdu;
    }
    /** Returns the local decision deadline on the System.nanoTime origin.
     * @return absolute monotonic deadline */
    public long deadlineNanos() {
        return deadline;
    }
    /** Reports logical cancellation by timeout or connection closure.
     * @return whether the handler should stop cooperative work */
    public boolean isCancelled() {
        return cancelled.get();
    }
}
