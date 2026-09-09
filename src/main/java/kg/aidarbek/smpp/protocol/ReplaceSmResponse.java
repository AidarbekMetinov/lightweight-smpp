package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/**
 * Immutable replacement response; the command status is carried by its PDU envelope.
 * @param optionalParameters ordered raw extensions, including supported 5.0 congestion on success
 */
public record ReplaceSmResponse(OptionalParameters optionalParameters) implements Command {
    /** Requires non-null immutable parameters. */
    public ReplaceSmResponse {
        Objects.requireNonNull(optionalParameters, "optionalParameters");
    }

    @Override
    public long commandId() {
        return 0x80000007L;
    }

    @Override
    public String toString() {
        return "ReplaceSmResponse[optionalParameterCount="
                + optionalParameters.entries().size() + "]";
    }
}
