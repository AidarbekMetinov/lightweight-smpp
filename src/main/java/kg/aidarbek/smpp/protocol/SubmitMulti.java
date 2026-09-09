package kg.aidarbek.smpp.protocol;

import java.util.List;
import java.util.Objects;

/**
 * Immutable multiple-destination submission; the application owns list expansion and delivery.
 * @param serviceType service identity of up to five ASCII characters
 * @param source originating SME address, limited to 20 characters by the codec
 * @param destinations copied ordered list of 1..255 alternatives; 3.4 further limits the count to 254
 * @param esmClass raw unsigned message-mode/type/feature flags
 * @param protocolId raw unsigned network protocol identifier
 * @param priorityFlag raw unsigned priority, with supported values selected by the codec
 * @param scheduleDeliveryTime SMPP scheduling time or empty for immediate submission
 * @param validityPeriod SMPP validity time or empty for MC default
 * @param registeredDelivery raw unsigned receipt-request flags
 * @param replaceIfPresentFlag raw unsigned replacement flag; outgoing 3.4 requires zero
 * @param dataCoding raw unsigned coding identifier; bytes are not text-decoded here
 * @param defaultMessageId raw unsigned predefined-message index
 * @param shortMessage owned raw bytes, with the 254/255 profile limit enforced by the codec
 * @param optionalParameters ordered raw submission parameters
 */
public record SubmitMulti(
        String serviceType,
        Address source,
        List<MultiDestination> destinations,
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
        OptionalParameters optionalParameters)
        implements Command {
    /** Bounds the count before copying the list and validates every representation field. */
    public SubmitMulti {
        MessageValueChecks.ascii(serviceType, 6);
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destinations, "destinations");
        if (destinations.isEmpty() || destinations.size() > 255)
            throw new IllegalArgumentException(
                    "Multiple submission requires 1..255 destinations before profile validation");
        destinations = List.copyOf(destinations);
        MessageValueChecks.ascii(scheduleDeliveryTime, 17);
        MessageValueChecks.ascii(validityPeriod, 17);
        for (int octet : new int[] {
            esmClass, protocolId, priorityFlag, registeredDelivery, replaceIfPresentFlag, dataCoding, defaultMessageId
        }) MessageValueChecks.octet(octet);
        Objects.requireNonNull(shortMessage, "shortMessage");
        Objects.requireNonNull(optionalParameters, "optionalParameters");
        if (shortMessage.length() > 255)
            throw new IllegalArgumentException("Multiple-submission payload exceeds 255 octets");
    }

    @Override
    public long commandId() {
        return 0x21;
    }

    @Override
    public String toString() {
        return "SubmitMulti[optionalParameterCount="
                + optionalParameters.entries().size() + "]";
    }
}
