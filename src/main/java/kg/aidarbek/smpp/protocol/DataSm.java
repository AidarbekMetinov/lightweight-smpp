package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/**
 * Immutable data_sm fields; message data is held only in optional message_payload octets.
 * The body has no schedule, validity, sm_length or short_message field.
 * @param serviceType non-NUL ASCII service, at most 5 characters
 * @param source source address, at most 64 characters
 * @param destination destination address, at most 64 characters
 * @param esmClass raw unsigned message flags
 * @param registeredDelivery raw unsigned receipt flags
 * @param dataCoding raw unsigned coding octet, independent of payload bytes
 * @param optionalParameters immutable ordered TLVs
 */
public record DataSm(
        String serviceType,
        Address source,
        Address destination,
        int esmClass,
        int registeredDelivery,
        int dataCoding,
        OptionalParameters optionalParameters)
        implements Command {
    /** Validates raw data-message field representations and non-null immutable values. */
    public DataSm {
        MessageValueChecks.ascii(serviceType, 6);
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        MessageValueChecks.octet(esmClass);
        MessageValueChecks.octet(registeredDelivery);
        MessageValueChecks.octet(dataCoding);
        Objects.requireNonNull(optionalParameters, "optionalParameters");
    }

    @Override
    public long commandId() {
        return 0x00000103L;
    }

    @Override
    public String toString() {
        return "DataSm[optionalParameterCount=" + optionalParameters.entries().size() + "]";
    }
}
