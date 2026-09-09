package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/**
 * Immutable replacement fields; applying them to a stored message belongs to the application.
 * @param messageId MC message identity of up to 64 ASCII characters
 * @param source originating SME address, limited to 20 characters by the codec
 * @param scheduleDeliveryTime SMPP time or empty to preserve the original scheduling
 * @param validityPeriod SMPP time or empty to preserve the original validity
 * @param registeredDelivery raw unsigned receipt-request flags
 * @param defaultMessageId raw unsigned predefined-message index
 * @param shortMessage owned raw bytes, with the 254/255 profile limit enforced by the codec
 * @param optionalParameters ordered raw parameters; 5.0 permits message_payload
 */
public record ReplaceSm(
        String messageId,
        Address source,
        String scheduleDeliveryTime,
        String validityPeriod,
        int registeredDelivery,
        int defaultMessageId,
        OctetString shortMessage,
        OptionalParameters optionalParameters)
        implements Command {
    /** Checks representation limits and non-null immutable fields before profile validation. */
    public ReplaceSm {
        MessageValueChecks.ascii(messageId, 65);
        Objects.requireNonNull(source, "source");
        MessageValueChecks.ascii(scheduleDeliveryTime, 17);
        MessageValueChecks.ascii(validityPeriod, 17);
        MessageValueChecks.octet(registeredDelivery);
        MessageValueChecks.octet(defaultMessageId);
        Objects.requireNonNull(shortMessage, "shortMessage");
        Objects.requireNonNull(optionalParameters, "optionalParameters");
        if (shortMessage.length() > 255) throw new IllegalArgumentException("Replacement payload exceeds 255 octets");
    }

    @Override
    public long commandId() {
        return 7;
    }

    @Override
    public String toString() {
        return "ReplaceSm[optionalParameterCount="
                + optionalParameters.entries().size() + "]";
    }
}
