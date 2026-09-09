package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/**
 * Immutable query identity; matching a stored message belongs to the application.
 * @param messageId MC message identity of up to 64 ASCII characters
 * @param source originating SME address, limited to 20 characters by the codec
 * @param optionalParameters ordered raw extensions; standard output allows none
 */
public record QuerySm(String messageId, Address source, OptionalParameters optionalParameters) implements Command {
    /** Validates the identifier representation and non-null immutable fields. */
    public QuerySm {
        MessageValueChecks.ascii(messageId, 65);
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(optionalParameters, "optionalParameters");
    }

    @Override
    public long commandId() {
        return 3;
    }

    @Override
    public String toString() {
        return "QuerySm[optionalParameterCount=" + optionalParameters.entries().size() + "]";
    }
}
