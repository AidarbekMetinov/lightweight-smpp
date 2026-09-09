package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/**
 * Immutable one-way availability notification, with no response command.
 * @param source alerted SME address; the codec permits up to 64 ASCII characters
 * @param esme destination ESME address; the codec permits up to 64 ASCII characters
 * @param optionalParameters ordered raw availability and extension values
 */
public record AlertNotification(Address source, Address esme, OptionalParameters optionalParameters)
        implements Command {
    /** Requires immutable non-null fields; the codec validates numeric address semantics. */
    public AlertNotification {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(esme, "esme");
        Objects.requireNonNull(optionalParameters, "optionalParameters");
    }

    @Override
    public long commandId() {
        return 0x102;
    }

    @Override
    public String toString() {
        return "AlertNotification[optionalParameterCount="
                + optionalParameters.entries().size() + "]";
    }
}
