package kg.aidarbek.smpp.message;

import java.util.Arrays;
import java.util.Objects;
import kg.aidarbek.smpp.protocol.OctetString;

/**
 * Explicit canonical concatenation UDH for octet-aligned payloads, such as UCS-2 or binary data.
 * This does not pack GSM septets, set esm_class, combine other information elements or infer data_coding.
 */
public final class ConcatenationHeader {
    private ConcatenationHeader() {}

    /**
     * Adds the six-octet 8-bit-reference UDH.
     * @param segment octet-aligned fragment
     * @return header and payload
     */
    public static OctetString prepend8(MessageSegment segment) {
        Objects.requireNonNull(segment, "segment");
        if (segment.reference() > 255) throw new IllegalArgumentException("Reference does not fit an 8-bit UDH");
        return prepend(
                segment,
                new byte[] {5, 0, 3, (byte) segment.reference(), (byte) segment.total(), (byte) segment.number()});
    }
    /**
     * Adds the seven-octet 16-bit-reference UDH.
     * @param segment octet-aligned fragment
     * @return header and payload
     */
    public static OctetString prepend16(MessageSegment segment) {
        Objects.requireNonNull(segment, "segment");
        return prepend(segment, new byte[] {
            6,
            8,
            4,
            (byte) (segment.reference() >>> 8),
            (byte) segment.reference(),
            (byte) segment.total(),
            (byte) segment.number()
        });
    }
    /**
     * Parses exactly one canonical concatenation UDH and its octet-aligned fragment.
     *
     * @param userData original complete user-data octets
     * @return owned raw fragment and reference metadata
     */
    public static MessageSegment read(OctetString userData) {
        Objects.requireNonNull(userData, "userData");
        if (userData.length() < 6 || userData.length() > 65542)
            throw new IllegalArgumentException("Invalid bounded concatenation user data");
        byte[] bytes = userData.value();
        if (bytes[0] == 5 && bytes[1] == 0 && bytes[2] == 3)
            return new MessageSegment(
                    bytes[3] & 255,
                    bytes[4] & 255,
                    bytes[5] & 255,
                    new OctetString(Arrays.copyOfRange(bytes, 6, bytes.length)));
        if (bytes.length >= 7 && bytes[0] == 6 && bytes[1] == 8 && bytes[2] == 4)
            return new MessageSegment(
                    ((bytes[3] & 255) << 8) | (bytes[4] & 255),
                    bytes[5] & 255,
                    bytes[6] & 255,
                    new OctetString(Arrays.copyOfRange(bytes, 7, bytes.length)));
        throw new IllegalArgumentException("Expected a sole 8-bit or 16-bit concatenation UDH");
    }

    private static OctetString prepend(MessageSegment segment, byte[] header) {
        byte[] bytes = Arrays.copyOf(header, header.length + segment.payload().length());
        byte[] payload = segment.payload().value();
        System.arraycopy(payload, 0, bytes, header.length, payload.length);
        return new OctetString(bytes);
    }
}
