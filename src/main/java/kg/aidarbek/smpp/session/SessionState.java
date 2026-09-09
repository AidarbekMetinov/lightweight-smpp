package kg.aidarbek.smpp.session;

/** Connection and SMPP lifecycle states; state changes do not perform network I/O. */
public enum SessionState {
    /** Awaiting establishment of the underlying connection. */
    CONNECTING,
    /** Connected, before a bind request. */
    OPEN,
    /** SMPP 5.0 outbind notification received or sent, before the ensuing bind. */
    OUTBOUND,
    /** Awaiting the result of one bind request. */
    BINDING,
    /** Bound as a receiver ESME. */
    BOUND_RX,
    /** Bound as a transmitter ESME. */
    BOUND_TX,
    /** Bound as a transceiver ESME. */
    BOUND_TRX,
    /** At least one endpoint has begun unbinding; new application requests are refused. */
    UNBINDING,
    /** Terminal state, with no permission to exchange further PDUs. */
    CLOSED
}
