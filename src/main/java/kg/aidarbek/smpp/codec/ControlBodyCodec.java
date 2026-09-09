package kg.aidarbek.smpp.codec;

import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.ControlCommand;

/** Stateless body translation for one control identity with no standard body fields. */
final class ControlBodyCodec implements CommandCodec<ControlCommand> {
    private final ControlCommand.Type type;

    ControlBodyCodec(ControlCommand.Type type) {
        this.type = type;
    }

    @Override
    public long commandId() {
        return type.commandId();
    }

    @Override
    public Class<ControlCommand> commandType() {
        return ControlCommand.class;
    }

    @Override
    public ControlCommand decode(byte[] body, long status, ProtocolProfile profile, PduLimits limits) {
        CommandCodecChecks.context(commandId(), status, profile, limits);
        validateStatus(status);
        FieldReader reader = new FieldReader(body, limits.maximumBodyLength());
        return new ControlCommand(type, ControlTlvCodec.decode(reader, commandId(), profile, limits));
    }

    @Override
    public byte[] encode(ControlCommand command, long status, ProtocolProfile profile, PduLimits limits) {
        CommandCodecChecks.context(commandId(), status, profile, limits);
        CommandCodecChecks.command(command, commandId());
        validateStatus(status);
        if (permitsTlvs(profile)) {
            return ControlTlvCodec.encode(command.optionalParameters(), commandId(), profile, limits);
        }
        if (!command.optionalParameters().entries().isEmpty()) {
            throw new IllegalArgumentException("Control command must omit its body");
        }
        return new byte[0];
    }

    private boolean permitsTlvs(ProtocolProfile profile) {
        return (commandId() & 0x80000000L) != 0 && profile.version() == SmppVersion.V5_0;
    }

    private void validateStatus(long status) {
        if (type == ControlCommand.Type.GENERIC_NACK && status == 0) {
            throw new IllegalArgumentException("generic_nack requires a nonzero error status");
        }
    }
}
