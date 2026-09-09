package kg.aidarbek.smpp.codec;

import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindResponse;
import kg.aidarbek.smpp.protocol.OptionalParameters;

/** Stateless body translation for one bind response mode. */
final class BindResponseCodec implements CommandCodec<BindResponse> {
    private final BindMode mode;

    BindResponseCodec(BindMode mode) {
        this.mode = mode;
    }

    @Override
    public long commandId() {
        return mode.responseCommandId();
    }

    @Override
    public Class<BindResponse> commandType() {
        return BindResponse.class;
    }

    @Override
    public BindResponse decode(byte[] body, long status, ProtocolProfile profile, PduLimits limits) {
        CommandCodecChecks.context(commandId(), status, profile, limits);
        FieldReader reader = new FieldReader(body, limits.maximumBodyLength());
        if (status != 0) {
            if (profile.version() == SmppVersion.V3_4 && reader.remaining() != 0) {
                throw new FieldCodecException("SMPP 3.4 failed bind must omit its body");
            }
            return new BindResponse(mode, Optional.empty(), new OptionalParameters(List.of()));
        }
        String systemId = reader.readCOctetString(16);
        OptionalParameters parameters = ControlTlvCodec.decode(reader, commandId(), profile, limits);
        return new BindResponse(mode, Optional.of(systemId), parameters);
    }

    @Override
    public byte[] encode(BindResponse command, long status, ProtocolProfile profile, PduLimits limits) {
        CommandCodecChecks.context(commandId(), status, profile, limits);
        CommandCodecChecks.command(command, commandId());
        if (status != 0) {
            if (command.systemId().isPresent()
                    || !command.optionalParameters().entries().isEmpty()) {
                throw new IllegalArgumentException("Failed bind must omit its body");
            }
            return new byte[0];
        }
        String systemId = command.systemId()
                .orElseThrow(() -> new IllegalArgumentException("Successful bind requires a system_id field"));
        byte[] tlvs = ControlTlvCodec.encode(command.optionalParameters(), commandId(), profile, limits);
        long length = systemId.length() + 1L + tlvs.length;
        if (length > limits.maximumBodyLength()) {
            throw new IllegalArgumentException("Bind response exceeds configured body bound");
        }
        FieldWriter writer = new FieldWriter((int) length);
        writer.writeCOctetString(systemId, 16);
        writer.writeOctets(tlvs);
        return writer.toByteArray();
    }
}
