package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/**
 * Immutable MC authentication notification; subsequent binding is a separate operation on the same connection.
 * @param systemId MC identity of up to 15 ASCII characters
 * @param password MC credential of up to eight ASCII characters
 * @param optionalParameters ordered raw extensions; standard output allows none
 */
public record Outbind(String systemId, String password, OptionalParameters optionalParameters) implements Command {
    /** Validates both credential representations; diagnostic output omits their values. */
    public Outbind {
        MessageValueChecks.ascii(systemId, 16);
        MessageValueChecks.ascii(password, 9);
        Objects.requireNonNull(optionalParameters, "optionalParameters");
    }

    @Override
    public long commandId() {
        return 0x0b;
    }

    @Override
    public String toString() {
        return "Outbind[optionalParameterCount=" + optionalParameters.entries().size() + "]";
    }
}
