package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/**
 * Immutable control command with no standard body fields; permitted TLVs depend on its profile and direction.
 *
 * @param type control wire identity
 * @param optionalParameters immutable ordered raw TLVs
 */
public record ControlCommand(Type type, OptionalParameters optionalParameters) implements Command {
    /**
     * Validates immutable field references; the body codec applies status/profile-specific rules.
     * @throws NullPointerException if either field is null
     */
    public ControlCommand {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(optionalParameters, "optionalParameters");
    }

    /** The control operations with an empty standard body. */
    public enum Type {
        /** Session termination request. */
        UNBIND(0x00000006L),
        /** Session termination response. */
        UNBIND_RESPONSE(0x80000006L),
        /** Connection enquiry request. */
        ENQUIRE_LINK(0x00000015L),
        /** Connection enquiry response. */
        ENQUIRE_LINK_RESPONSE(0x80000015L),
        /** Negative acknowledgement of an unrecognized or corrupt PDU. */
        GENERIC_NACK(0x80000000L);

        private final long commandId;

        Type(long commandId) {
            this.commandId = commandId;
        }

        /**
         * Returns the exact unsigned command identifier.
         * @return the wire identity
         */
        public long commandId() {
            return commandId;
        }
    }

    @Override
    public long commandId() {
        return type.commandId();
    }
}
