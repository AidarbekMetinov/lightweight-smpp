package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/**
 * Immutable cancellation selector; matching stored messages belongs to the application.
 * @param serviceType service selector of up to five ASCII characters
 * @param messageId MC message identity of up to 64 ASCII characters, or empty for selection by addresses/service
 * @param source originating SME address, limited to 20 characters by the codec
 * @param destination target SME address, limited to 20 characters by the codec
 * @param optionalParameters ordered raw extensions; standard output allows none
 */
public record CancelSm(
        String serviceType,
        String messageId,
        Address source,
        Address destination,
        OptionalParameters optionalParameters)
        implements Command {
    /** Validates C-octet representations and non-null immutable fields. */
    public CancelSm {
        MessageValueChecks.ascii(serviceType, 6);
        MessageValueChecks.ascii(messageId, 65);
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(optionalParameters, "optionalParameters");
    }

    @Override
    public long commandId() {
        return 8;
    }

    @Override
    public String toString() {
        return "CancelSm[optionalParameterCount=" + optionalParameters.entries().size() + "]";
    }
}
