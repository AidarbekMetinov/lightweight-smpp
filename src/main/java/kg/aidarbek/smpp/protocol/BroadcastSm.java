package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/** Immutable broadcast request. Geographic selection and delivery are application responsibilities.
 * @param serviceType at most five non-NUL ASCII characters
 * @param source source address, limited to twenty characters by the codec
 * @param messageId original MC identity for replacement, otherwise empty
 * @param priorityFlag raw unsigned network-specific priority
 * @param scheduleDeliveryTime empty or sixteen-character SMPP time
 * @param validityPeriod empty or sixteen-character SMPP time
 * @param replaceIfPresentFlag raw unsigned replacement flag
 * @param dataCoding raw unsigned payload encoding identifier
 * @param defaultMessageId raw unsigned canned-message identifier
 * @param optionalParameters ordered immutable required and optional TLVs, including payload */
public record BroadcastSm(
        String serviceType,
        Address source,
        String messageId,
        int priorityFlag,
        String scheduleDeliveryTime,
        String validityPeriod,
        int replaceIfPresentFlag,
        int dataCoding,
        int defaultMessageId,
        OptionalParameters optionalParameters)
        implements Command {
    /** Checks immutable representation; the codec applies SMPP 5.0 semantic rules. */
    public BroadcastSm {
        MessageValueChecks.ascii(serviceType, 6);
        Objects.requireNonNull(source, "source");
        MessageValueChecks.ascii(messageId, 65);
        MessageValueChecks.octet(priorityFlag);
        MessageValueChecks.ascii(scheduleDeliveryTime, 17);
        MessageValueChecks.ascii(validityPeriod, 17);
        MessageValueChecks.octet(replaceIfPresentFlag);
        MessageValueChecks.octet(dataCoding);
        MessageValueChecks.octet(defaultMessageId);
        Objects.requireNonNull(optionalParameters, "optionalParameters");
    }

    @Override
    public long commandId() {
        return 0x111;
    }

    @Override
    public String toString() {
        return "BroadcastSm[optionalParameterCount="
                + optionalParameters.entries().size() + "]";
    }
}
