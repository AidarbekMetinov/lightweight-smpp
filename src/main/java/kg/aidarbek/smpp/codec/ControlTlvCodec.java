package kg.aidarbek.smpp.codec;

import java.util.Optional;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Tlv;

/** Bounded trailing control parameters, preserving unsupported incoming extensions without permission to send them. */
final class ControlTlvCodec {
    private static final TypedTlvRegistry TYPES = TypedTlvRegistry.standard();

    private ControlTlvCodec() {}

    static OptionalParameters decode(FieldReader reader, long commandId, ProtocolProfile profile, PduLimits limits) {
        if (reader.remaining() > limits.maximumTlvLength()) {
            throw new FieldCodecException("Control TLVs exceed configured byte bound");
        }
        OptionalParameters parameters = TlvCodec.decode(
                reader.readOctets(reader.remaining()), limits.maximumTlvLength(), limits.maximumTlvCount());
        profile.tlvRules(commandId).ifPresent(rules -> rules.validateIncoming(parameters));
        validateValues(parameters, commandId, profile, false);
        return parameters;
    }

    static byte[] encode(OptionalParameters parameters, long commandId, ProtocolProfile profile, PduLimits limits) {
        byte[] encoded = TlvCodec.encode(parameters, limits.maximumTlvLength(), limits.maximumTlvCount());
        profile.tlvRules(commandId).orElseThrow().validateOutgoing(parameters);
        validateValues(parameters, commandId, profile, true);
        return encoded;
    }

    private static void validateValues(
            OptionalParameters parameters, long commandId, ProtocolProfile profile, boolean outgoing) {
        for (Tlv parameter : parameters.entries()) {
            if (parameter.tag() == 0x0210 || parameter.tag() == 0x0428) {
                Optional<Integer> value = TYPES.decode(profile.version(), commandId, parameter, Integer.class);
                if (outgoing && value.isEmpty()) {
                    throw new IllegalArgumentException("Unsupported outgoing control TLV value");
                }
            }
        }
    }
}
