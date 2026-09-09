package kg.aidarbek.smpp.message;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Tlv;

/** Explicit encoded-length segmentation, independent of sessions and provider radio packing. */
public final class MessageSegments {
    private MessageSegments() {}

    /**
     * Reads a complete unambiguous SAR trio, without interpreting other TLVs.
     *
     * @param payload raw fragment
     * @param parameters original parameters
     * @return empty when all SAR tags are absent; otherwise the validated fragment
     */
    public static Optional<MessageSegment> fromSar(OctetString payload, OptionalParameters parameters) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(parameters, "parameters");
        byte[] reference = null;
        byte[] total = null;
        byte[] number = null;
        for (Tlv parameter : parameters.entries()) {
            switch (parameter.tag()) {
                case 0x020c -> {
                    if (reference != null) throw new IllegalArgumentException("Duplicate SAR reference");
                    reference = parameter.value();
                }
                case 0x020e -> {
                    if (total != null) throw new IllegalArgumentException("Duplicate SAR total");
                    total = parameter.value();
                }
                case 0x020f -> {
                    if (number != null) throw new IllegalArgumentException("Duplicate SAR number");
                    number = parameter.value();
                }
                default -> {}
            }
        }
        if (reference == null && total == null && number == null) return Optional.empty();
        if (reference == null
                || total == null
                || number == null
                || reference.length != 2
                || total.length != 1
                || number.length != 1)
            throw new IllegalArgumentException("SAR needs exactly one correctly sized reference, total and number");
        return Optional.of(new MessageSegment(
                ((reference[0] & 255) << 8) | (reference[1] & 255), total[0] & 255, number[0] & 255, payload));
    }

    /**
     * Splits text without breaking an encoding unit; limits measure encoded payload octets.
     *
     * @param text complete message
     * @param encoding explicitly selected alphabet
     * @param reference originator reference
     * @param singlePartLimit payload bound when the message fits one part, 1..65535
     * @param multipartLimit payload bound per part otherwise, 1..singlePartLimit
     * @return immutable ordered parts, at most 255
     * @throws IllegalArgumentException for invalid limits/reference, unsupported text or over 255 parts
     * @throws NullPointerException for missing text or encoding
     */
    public static List<MessageSegment> split(
            String text, TextEncoding encoding, int reference, int singlePartLimit, int multipartLimit) {
        Objects.requireNonNull(encoding, "encoding");
        if (reference < 0
                || reference > 65535
                || singlePartLimit < 1
                || singlePartLimit > 65535
                || multipartLimit < 1
                || multipartLimit > singlePartLimit)
            throw new IllegalArgumentException("Invalid reference or payload limits");
        int length = encoding.encodedLength(text);
        if (length <= singlePartLimit) return List.of(new MessageSegment(reference, 1, 1, encoding.encode(text)));
        if (length > 255L * multipartLimit) throw new IllegalArgumentException("Message exceeds 255 segments");
        byte[] encoded = encoding.encode(text).value();
        List<OctetString> payloads = new ArrayList<>();
        int offset = 0;
        while (offset < encoded.length) {
            int end = Math.min(offset + multipartLimit, encoded.length);
            if (encoding == TextEncoding.UCS2) end -= (end - offset) & 1;
            else if (encoded[end - 1] == 27) end--;
            if (end == offset) throw new IllegalArgumentException("A complete encoding unit cannot fit the part limit");
            if (payloads.size() == 255) throw new IllegalArgumentException("Message exceeds 255 segments");
            payloads.add(new OctetString(Arrays.copyOfRange(encoded, offset, end)));
            offset = end;
        }
        List<MessageSegment> result = new ArrayList<>(payloads.size());
        for (int i = 0; i < payloads.size(); i++)
            result.add(new MessageSegment(reference, payloads.size(), i + 1, payloads.get(i)));
        return List.copyOf(result);
    }
}
