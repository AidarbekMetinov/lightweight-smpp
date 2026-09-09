package kg.aidarbek.smpp.codec;

import java.util.Objects;
import java.util.Optional;
import kg.aidarbek.smpp.protocol.OctetString;

/** SMPP 5.0 broadcast TLV structure and supported discriminants, preserving raw octets. */
public final class BroadcastTlvValueCodec implements TlvValueCodec<OctetString> {
    private final int tag;
    /** Selects a broadcast-specific tag or CBS message_state context.
     * @param tag broadcast tag 0600..060A or message_state 0427
     * @throws IllegalArgumentException for any other tag */
    public BroadcastTlvValueCodec(int tag) {
        if ((tag < 0x0600 || tag > 0x060a) && tag != 0x0427)
            throw new IllegalArgumentException("Not a broadcast-specific parameter tag");
        this.tag = tag;
    }

    @Override
    public Class<OctetString> valueType() {
        return OctetString.class;
    }

    @Override
    public Optional<OctetString> decode(byte[] value) {
        Objects.requireNonNull(value, "value");
        return supported(value) ? Optional.of(new OctetString(value)) : Optional.empty();
    }

    @Override
    public byte[] encode(OctetString value) {
        byte[] bytes = Objects.requireNonNull(value, "value").value();
        if (!supported(bytes)) throw new IllegalArgumentException("Unsupported outgoing broadcast TLV value");
        return bytes;
    }

    private boolean supported(byte[] bytes) {
        switch (tag) {
            case 0x0601, 0x0605 -> length(bytes, 3, 3);
            case 0x0602, 0x060a -> length(bytes, 1, 255);
            case 0x0604 -> length(bytes, 2, 2);
            case 0x0606 -> length(bytes, 1, 101);
            case 0x0607 -> length(bytes, 4, 4);
            case 0x0609 -> {
                length(bytes, 17, 17);
                FieldReader reader = new FieldReader(bytes, 17);
                String time = reader.readCOctetString(17);
                if (reader.remaining() != 0 || time.length() != 16 || time.endsWith("R"))
                    throw new FieldCodecException("Broadcast end time requires absolute C-octet grammar");
                MessageFields.validateTime(time, false);
            }
            default -> length(bytes, 1, 1);
        }
        int first = bytes[0] & 255;
        return switch (tag) {
            case 0x0600 -> first <= 1;
            case 0x0601 -> first <= 3 && (first != 0 || genericContent(((bytes[1] & 255) << 8) | (bytes[2] & 255)));
            case 0x0603 -> first == 0 || first == 2 || first == 3;
            case 0x0605 -> first == 0 || (first >= 8 && first <= 14);
            case 0x0606 -> first <= 2;
            case 0x0608 -> first <= 100 || first == 255;
            case 0x0427 -> first <= 9 && first != 2;
            default -> true;
        };
    }

    private static boolean genericContent(int content) {
        return content <= 2
                || (content >= 0x10 && content <= 0x23)
                || (content >= 0x30 && content <= 0x39)
                || content == 0x40
                || content == 0x41
                || content == 0x70
                || content == 0x71
                || (content >= 0x80 && content <= 0x85)
                || content == 0x100;
    }

    private static void length(byte[] value, int minimum, int maximum) {
        if (value.length < minimum || value.length > maximum)
            throw new FieldCodecException("Malformed standard broadcast TLV length");
    }
}
