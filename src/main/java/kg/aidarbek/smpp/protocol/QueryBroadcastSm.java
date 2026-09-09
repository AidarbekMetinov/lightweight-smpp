package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/** Immutable broadcast status query; MC identity and user reference are exclusive alternatives.
 * @param messageId MC identity, or empty when using user_message_reference
 * @param source original source address
 * @param optionalParameters ordered raw TLVs, with optional user reference */
public record QueryBroadcastSm(String messageId, Address source, OptionalParameters optionalParameters)
        implements Command {
    /** Checks ASCII representation and immutable dependencies. */
    public QueryBroadcastSm {
        MessageValueChecks.ascii(messageId, 65);
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(optionalParameters, "optionalParameters");
    }

    @Override
    public long commandId() {
        return 0x112;
    }

    @Override
    public String toString() {
        return "QueryBroadcastSm[optionalParameterCount="
                + optionalParameters.entries().size() + "]";
    }
}
