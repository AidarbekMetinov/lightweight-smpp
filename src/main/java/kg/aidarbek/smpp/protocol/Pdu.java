package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/**
 * Immutable command envelope; the encoded length is computed by the full-PDU codec.
 *
 * @param commandStatus unsigned response status; requests use zero
 * @param sequenceNumber correlation sequence, normally in 1..0x7fffffff
 * @param command immutable command data
 * @param <T> command representation
 */
public record Pdu<T extends Command>(long commandStatus, long sequenceNumber, T command) {
    /**
     * Validates the command-independent envelope invariants; body rules belong to its codec.
     * @throws IllegalArgumentException for invalid unsigned values, request status or sequence
     * @throws NullPointerException if command is null
     */
    public Pdu {
        Objects.requireNonNull(command, "command");
        validateHeader(new PduHeader(PduHeader.LENGTH, command.commandId(), commandStatus, sequenceNumber));
    }

    /**
     * Validates envelope rules before a body is dispatched. Unknown IDs and response statuses are
     * retained; registry and command-specific rules are separate. Only generic_nack may use sequence
     * zero to indicate that no valid original sequence was available.
     * @param header structurally valid raw header
     * @throws IllegalArgumentException if a request has nonzero status or the sequence is invalid
     */
    public static void validateHeader(PduHeader header) {
        Objects.requireNonNull(header, "header");
        if ((header.commandId() & 0x80000000L) == 0 && header.commandStatus() != 0) {
            throw new IllegalArgumentException("Request command_status must be zero");
        }
        if (header.sequenceNumber() > 0x7fffffffL
                || (header.sequenceNumber() == 0 && header.commandId() != 0x80000000L)) {
            throw new IllegalArgumentException("Invalid command sequence_number");
        }
    }
}
