package kg.aidarbek.smpp.protocol;

/** SMPP bind role, independent of the TCP connection direction or current session state. */
public enum BindMode {
    /** Receives messages from the message center. */
    RECEIVER(0x00000001L),
    /** Submits messages to the message center. */
    TRANSMITTER(0x00000002L),
    /** Combines receiver and transmitter roles on one connection. */
    TRANSCEIVER(0x00000009L);

    private final long requestCommandId;

    BindMode(long requestCommandId) {
        this.requestCommandId = requestCommandId;
    }

    /**
     * Returns the unsigned bind request command identifier.
     * @return the request wire identity
     */
    public long requestCommandId() {
        return requestCommandId;
    }

    /**
     * Returns the unsigned matching bind response identifier.
     * @return the request wire identity with the response bit set
     */
    public long responseCommandId() {
        return requestCommandId | 0x80000000L;
    }
}
