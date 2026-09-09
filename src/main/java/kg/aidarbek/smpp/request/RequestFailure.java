package kg.aidarbek.smpp.request;

import java.util.Optional;
import java.util.UUID;

/** A local request failure, distinct from a known negative peer response. */
public final class RequestFailure extends RuntimeException {
    private static final long serialVersionUID = 1L;
    /** Winning local failure reason. */
    private final Reason reason;
    /** Immutable connection identity, serializable without protocol objects. */
    private final UUID generation;
    /** Sequence assigned on admission, or zero for rejection. */
    private final long sequenceNumber;
    /** Unsigned request command identity. */
    private final long requestCommandId;
    /** Knowledge captured at the winning terminal transition. */
    private final TransmissionCertainty transmission;

    /** Local terminal or admission reasons; none requests automatic replay. */
    public enum Reason {
        /** Pending request count is full. */
        WINDOW_FULL,
        /** Pending frame-byte capacity is full. */
        BYTE_LIMIT,
        /** Completion notification capacity is unavailable. */
        NOTIFICATION_BACKLOG,
        /** All permitted sequences have been allocated in this generation. */
        SEQUENCE_EXHAUSTED,
        /** This window no longer admits requests. */
        CLOSED,
        /** The total invocation deadline elapsed. */
        DEADLINE_EXPIRED,
        /** Explicit handle cancellation won. */
        CANCELLED,
        /** The connection was closed or disconnected. */
        DISCONNECTED,
        /** Transport rejected or failed a write. */
        WRITE_FAILED
    }

    RequestFailure(
            Reason reason,
            UUID generation,
            long sequenceNumber,
            long requestCommandId,
            TransmissionCertainty transmission,
            Throwable cause) {
        super(
                "Request " + reason + " [command=0x" + Long.toHexString(requestCommandId) + ", generation=" + generation
                        + ", sequence=" + sequenceNumber + ", transmission=" + transmission + "]",
                cause);
        this.reason = reason;
        this.generation = generation;
        this.sequenceNumber = sequenceNumber;
        this.requestCommandId = requestCommandId;
        this.transmission = transmission;
    }

    /**
     * Returns the structured reason.
     * @return local reason
     */
    public Reason reason() {
        return reason;
    }
    /**
     * Returns the immutable connection identity.
     * @return generation
     */
    public UUID generation() {
        return generation;
    }
    /**
     * Returns the sequence, or zero if admission never assigned one.
     * @return sequence or zero
     */
    public long sequenceNumber() {
        return sequenceNumber;
    }
    /**
     * Returns the operation's request command.
     * @return unsigned command ID
     */
    public long requestCommandId() {
        return requestCommandId;
    }
    /**
     * Returns the request identity when admission succeeded.
     * @return admitted identity, otherwise empty
     */
    public Optional<RequestIdentity> requestIdentity() {
        return sequenceNumber == 0 ? Optional.empty() : Optional.of(new RequestIdentity(generation, sequenceNumber));
    }
    /**
     * Returns transmission knowledge at the winning terminal transition.
     * @return certainty
     */
    public TransmissionCertainty transmission() {
        return transmission;
    }
}
