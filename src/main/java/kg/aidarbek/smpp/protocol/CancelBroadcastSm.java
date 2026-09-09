package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/** Immutable broadcast cancellation selector; matching and storage are application policy.
 * @param serviceType optional service selector
 * @param messageId MC identity, or empty for a user reference or group cancellation
 * @param source original source address
 * @param optionalParameters ordered optional content type or user-reference TLVs */
public record CancelBroadcastSm(
        String serviceType, String messageId, Address source, OptionalParameters optionalParameters)
        implements Command {
    /** Checks ASCII representation and immutable dependencies. */
    public CancelBroadcastSm {
        MessageValueChecks.ascii(serviceType, 6);
        MessageValueChecks.ascii(messageId, 65);
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(optionalParameters, "optionalParameters");
    }

    @Override
    public long commandId() {
        return 0x113;
    }

    @Override
    public String toString() {
        return "CancelBroadcastSm[optionalParameterCount="
                + optionalParameters.entries().size() + "]";
    }
}
