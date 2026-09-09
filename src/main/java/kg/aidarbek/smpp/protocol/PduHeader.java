package kg.aidarbek.smpp.protocol;

/**
 * Immutable raw SMPP header shared by the 3.4 and 5.0 profiles.
 *
 * <p>All fields are unsigned four-octet values. Command, status, and sequence legality belongs to later command/session
 * validation; unknown values and sequence zero are preserved. Instances are safe to share between threads.
 *
 * @param commandLength total PDU length in octets, including the header, from 16 through 0xffffffff
 * @param commandId raw command identifier, from zero through 0xffffffff
 * @param commandStatus raw status, from zero through 0xffffffff
 * @param sequenceNumber raw sequence, from zero through 0xffffffff
 */
public record PduHeader(long commandLength, long commandId, long commandStatus, long sequenceNumber) {
    /** Header size in octets. */
    public static final int LENGTH = 16;

    /**
     * Validates structural field ranges without applying command or session policy.
     *
     * @throws IllegalArgumentException if a field is outside its unsigned range or length is below 16
     */
    public PduHeader {
        requireUnsigned(commandLength, "command_length");
        requireUnsigned(commandId, "command_id");
        requireUnsigned(commandStatus, "command_status");
        requireUnsigned(sequenceNumber, "sequence_number");
        if (commandLength < LENGTH) {
            throw new IllegalArgumentException("command_length is smaller than the header");
        }
    }

    private static void requireUnsigned(long value, String field) {
        if (value < 0 || value > 0xffffffffL) {
            throw new IllegalArgumentException(field + " is outside the unsigned four-octet range");
        }
    }
}
