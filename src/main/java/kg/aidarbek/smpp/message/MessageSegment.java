package kg.aidarbek.smpp.message;

import java.util.List;
import java.util.Objects;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Tlv;

/**
 * One immutable concatenated-message fragment; its caller owns reference allocation and scope.
 *
 * @param reference unsigned 16-bit originator reference
 * @param total total fragments, 1..255
 * @param number one-based fragment position
 * @param payload raw fragment, at most 65535 octets
 */
public record MessageSegment(int reference, int total, int number, OctetString payload) {
    /** Validates finite wire metadata and immutable payload ownership. */
    public MessageSegment {
        Objects.requireNonNull(payload, "payload");
        if (reference < 0
                || reference > 65535
                || total < 1
                || total > 255
                || number < 1
                || number > total
                || payload.length() > 65535)
            throw new IllegalArgumentException("Invalid concatenated-message fragment");
    }
    /**
     * Returns the complete SAR TLV trio.
     * @return immutable ordered parameters
     */
    public OptionalParameters sarParameters() {
        return new OptionalParameters(List.of(
                new Tlv(0x020c, new byte[] {(byte) (reference >>> 8), (byte) reference}),
                new Tlv(0x020e, new byte[] {(byte) total}),
                new Tlv(0x020f, new byte[] {(byte) number})));
    }
}
