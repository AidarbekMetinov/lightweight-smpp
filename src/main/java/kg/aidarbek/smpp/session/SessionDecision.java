package kg.aidarbek.smpp.session;

/**
 * Protocol-policy results, not wire statuses. Rejections leave lifecycle state unchanged except the
 * two terminal bind outcomes explicitly documented below.
 */
public enum SessionDecision {
    /** The event or permission check succeeded. */
    ACCEPTED,
    /** The lifecycle does not allow this event. */
    INVALID_STATE,
    /** The PDU originates from the wrong endpoint for this event. */
    INVALID_DIRECTION,
    /** No local implementation was declared for this request operation. */
    COMMAND_NOT_IMPLEMENTED,
    /** Configured or negotiated version policy does not satisfy the proposed operation or fields. */
    CAPABILITY_UNAVAILABLE,
    /** The response has no matching request identity and direction. */
    UNEXPECTED_RESPONSE,
    /** A negative bind result was accepted and closed the lifecycle. */
    BIND_REJECTED,
    /** A successful wire bind failed version policy and closed the lifecycle. */
    VERSION_REJECTED
}
