package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/**
 * Immutable shared submit_sm/deliver_sm wire fields. Sharing storage does not equate the commands'
 * profile-specific contracts; their codecs validate those contracts. Byte arrays are owned by OctetString.
 * Scheduling strings remain protocol representations, with no clock or century inference.
 * @param serviceType non-NUL ASCII service, at most 5 characters
 * @param source source address
 * @param destination destination address
 * @param esmClass unsigned message flags
 * @param protocolId raw unsigned network protocol identifier
 * @param priorityFlag unsigned priority
 * @param scheduleDeliveryTime empty or a protocol time string, at most 16 characters
 * @param validityPeriod empty or a protocol time string, at most 16 characters
 * @param registeredDelivery unsigned receipt flags
 * @param replaceIfPresentFlag unsigned replacement flag
 * @param dataCoding raw unsigned coding octet; no text conversion is implied
 * @param defaultMessageId unsigned predefined-message index
 * @param shortMessage immutable raw bytes, at most 255 octets before profile validation
 * @param optionalParameters immutable ordered TLVs
 */
public record ShortMessage(
        String serviceType,
        Address source,
        Address destination,
        int esmClass,
        int protocolId,
        int priorityFlag,
        String scheduleDeliveryTime,
        String validityPeriod,
        int registeredDelivery,
        int replaceIfPresentFlag,
        int dataCoding,
        int defaultMessageId,
        OctetString shortMessage,
        OptionalParameters optionalParameters) {
    /** Validates representation bounds without inferring a protocol profile or network service. */
    public ShortMessage {
        MessageValueChecks.ascii(serviceType, 6);
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        MessageValueChecks.ascii(scheduleDeliveryTime, 17);
        MessageValueChecks.ascii(validityPeriod, 17);
        for (int octet : new int[] {
            esmClass, protocolId, priorityFlag, registeredDelivery, replaceIfPresentFlag, dataCoding, defaultMessageId
        }) MessageValueChecks.octet(octet);
        Objects.requireNonNull(shortMessage, "shortMessage");
        Objects.requireNonNull(optionalParameters, "optionalParameters");
        if (shortMessage.length() > 255) throw new IllegalArgumentException("short_message exceeds 255 octets");
    }

    @Override
    public String toString() {
        return "ShortMessage[shortMessageLength=" + shortMessage.length() + ", optionalParameterCount="
                + optionalParameters.entries().size() + "]";
    }
}
