package kg.aidarbek.smpp.codec;

import java.util.Set;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;

/** Stateless body translation for one bind request mode. */
final class BindRequestCodec implements CommandCodec<BindRequest> {
    private static final Set<Integer> NUMBERING_PLANS = Set.of(0, 1, 3, 4, 6, 8, 9, 10, 14, 18);
    private final BindMode mode;

    BindRequestCodec(BindMode mode) {
        this.mode = mode;
    }

    @Override
    public long commandId() {
        return mode.requestCommandId();
    }

    @Override
    public Class<BindRequest> commandType() {
        return BindRequest.class;
    }

    @Override
    public BindRequest decode(byte[] body, long status, ProtocolProfile profile, PduLimits limits) {
        CommandCodecChecks.context(commandId(), status, profile, limits);
        FieldReader reader = new FieldReader(body, limits.maximumBodyLength());
        return new BindRequest(
                mode,
                reader.readCOctetString(16),
                reader.readCOctetString(9),
                reader.readCOctetString(13),
                reader.readUnsignedByte(),
                reader.readUnsignedByte(),
                reader.readUnsignedByte(),
                reader.readCOctetString(41),
                ControlTlvCodec.decode(reader, commandId(), profile, limits));
    }

    @Override
    public byte[] encode(BindRequest command, long status, ProtocolProfile profile, PduLimits limits) {
        CommandCodecChecks.context(commandId(), status, profile, limits);
        CommandCodecChecks.command(command, commandId());
        if (command.addrTon() > 6 || !NUMBERING_PLANS.contains(command.addrNpi())) {
            throw new IllegalArgumentException("Outgoing bind has a reserved TON or NPI");
        }
        if (!command.optionalParameters().entries().isEmpty()) {
            throw new IllegalArgumentException("Bind requests have no permitted outgoing TLVs");
        }
        int length = command.systemId().length()
                + command.password().length()
                + command.systemType().length()
                + command.addressRange().length()
                + 7;
        FieldWriter writer = new FieldWriter(Math.min(length, limits.maximumBodyLength()));
        writer.writeCOctetString(command.systemId(), 16);
        writer.writeCOctetString(command.password(), 9);
        writer.writeCOctetString(command.systemType(), 13);
        writer.writeUnsignedByte(command.interfaceVersion());
        writer.writeUnsignedByte(command.addrTon());
        writer.writeUnsignedByte(command.addrNpi());
        writer.writeCOctetString(command.addressRange(), 41);
        return writer.toByteArray();
    }
}
