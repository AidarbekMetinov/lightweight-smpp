package kg.aidarbek.smpp.endpoint;

/**
 * An authentication decision expressed as a wire status, independent of version negotiation.
 *
 * @param commandStatus zero accepts; a nonzero unsigned SMPP status rejects
 */
public record BindDecision(long commandStatus) {
    /** A successful authentication decision. */
    public static final BindDecision ACCEPT = new BindDecision(0);

    /**
     * Checks the unsigned wire range.
     *
     * @throws IllegalArgumentException if the status cannot fit four octets
     */
    public BindDecision {
        if (commandStatus < 0 || commandStatus > 0xffff_ffffL) {
            throw new IllegalArgumentException("Authentication status must be an unsigned 32-bit value");
        }
    }
}
