package kg.aidarbek.smpp.codec;

import java.util.Objects;
import java.util.Optional;
import kg.aidarbek.smpp.profile.MessageDirection;
import kg.aidarbek.smpp.profile.MessageTlvRules;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.OctetString;

/**
 * Immutable interpretation of a standard message TLV as validated raw octets. No text conversion,
 * receipt parser or network-specific service is implied. Unsupported incoming discriminants yield
 * no interpretation; malformed known structure fails. Enclosing command/context rules are separate.
 */
public final class MessageTlvValueCodec implements TlvValueCodec<OctetString> {
    private final SmppVersion version;
    private final int tag;
    /**
     * Selects a standard tag's structural and supported-value contract.
     * @param version explicit specification
     * @param tag standard message parameter tag
     * @throws IllegalArgumentException if this profile has no supported message context for the tag
     */
    public MessageTlvValueCodec(SmppVersion version, int tag) {
        this.version = Objects.requireNonNull(version, "version");
        this.tag = tag;
        boolean supported = false;
        for (long command : new long[] {4, 5, 0x103, 0x80000004L, 0x80000005L, 0x80000103L})
            for (MessageDirection direction : MessageDirection.values())
                supported |= MessageTlvRules.permittedTags(version, command, direction)
                        .contains(tag);
        if (!supported) throw new IllegalArgumentException("Tag is not a message parameter in this profile");
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
        if (!supported(bytes)) throw new IllegalArgumentException("Unsupported outgoing message TLV value");
        return bytes;
    }

    private boolean supported(byte[] value) {
        switch (tag) {
            case 0x0008, 0x0204, 0x020a, 0x020b, 0x020c, 0x1203, 0x1383 -> length(value, 2, 2);
            case 0x0017 -> length(value, 4, 4);
            case 0x001d -> cString(value, 1, 256);
            case 0x001e -> cString(value, 1, 65);
            case 0x0202, 0x0203 -> length(value, 2, 23);
            case 0x0303 -> length(value, 1, 65);
            case 0x0381 -> length(value, 4, 19);
            case 0x0423 -> length(value, 3, 3);
            case 0x0424 -> length(value, 0, 65535);
            case 0x060b -> length(value, 1, 1024);
            case 0x060d, 0x060e -> cString(value, 7, 65);
            case 0x060f, 0x0610 -> {
                length(value, 6, 6);
                digits(value, 0, value.length);
            }
            case 0x0612 -> {
                length(value, 10, 10);
                digits(value, 0, value.length);
            }
            case 0x0613 -> length(value, 1, 5);
            case 0x1204 -> {
                if (version == SmppVersion.V3_4) length(value, 1, 1);
                else if (value.length != 1 && value.length != 4) throw malformed();
            }
            case 0x130c -> length(value, 0, version == SmppVersion.V3_4 ? 0 : 1);
            default -> length(value, 1, 1);
        }
        int first = value.length == 0 ? 0 : value[0] & 255;
        return switch (tag) {
            case 0x0005, 0x000d -> first <= 4;
            case 0x0006, 0x0007, 0x000e, 0x000f, 0x1380 -> first <= 8;
            case 0x0008 -> version == SmppVersion.V3_4 || value[1] == 0;
            case 0x0019, 0x0420, 0x0421, 0x0426 -> first <= 1;
            case 0x0030 -> (first & 0x7c) == 0;
            case 0x0201, 0x0425, 0x130c -> first <= 3;
            case 0x0202, 0x0203 -> first == 0x80 || first == 0x88 || first == 0xa0;
            case 0x020e, 0x020f -> first >= 1;
            case 0x0302 -> (first & 0xf0) == 0 && (first & 12) != 12;
            case 0x0303 -> callbackDisplay(value);
            case 0x0304 -> first <= 99;
            case 0x0381 -> callback(value);
            case 0x0423 -> first >= 1 && first <= (version == SmppVersion.V3_4 ? 3 : 8);
            case 0x0427 ->
                first >= (version == SmppVersion.V3_4 ? 1 : 0) && first <= (version == SmppVersion.V3_4 ? 8 : 9);
            case 0x0428 -> first <= 100;
            case 0x0501 -> first <= 3 || (first >= 16 && first <= 19) || first >= 32;
            case 0x060b -> first >= 128;
            case 0x060d, 0x060e -> networkId(value);
            case 0x0611, 0x1201 -> first <= 2;
            case 0x1204 -> validity(value);
            default -> true;
        };
    }

    private boolean validity(byte[] value) {
        int behavior = value[0] & 255;
        if (version == SmppVersion.V3_4) return behavior <= 3;
        if (behavior > 4) return false;
        if (behavior == 4 && value.length != 4) throw malformed();
        return value.length == 1 || (value[1] & 255) <= 6;
    }

    private static boolean callback(byte[] value) {
        int mode = value[0] & 255;
        if (mode > 1 || (value[1] & 255) > 6 || !MessageFields.supportedNpi(value[2] & 255)) return false;
        if (mode == 1) digits(value, 3, value.length);
        else
            for (int i = 3; i < value.length; i++) {
                int digit = value[i] & 255;
                if ((digit & 15) == 15 || ((digit >>> 4) == 15 && i != value.length - 1)) throw malformed();
            }
        return true;
    }

    private static boolean callbackDisplay(byte[] value) {
        int coding = value[0] & 255;
        if (coding == 8 && (value.length - 1) % 2 != 0) {
            throw malformed();
        }
        return coding <= 10 || coding == 13 || coding == 14 || (coding >= 0xc0 && coding <= 0xdf) || coding >= 0xf0;
    }

    private static boolean networkId(byte[] value) {
        int format = value[0];
        if (format != '1' && format != '2' && format != '3') return false;
        digits(value, 1, 4);
        if (format == '1') {
            if (value.length != 7) throw malformed();
            digits(value, 4, 6);
        } else if (format == '2') {
            if (value.length != 10) throw malformed();
            digits(value, 4, 9);
        } else {
            int addressType = value[4];
            if (addressType < '1' || addressType > '4') return false;
            if (addressType != '2') digits(value, 5, value.length - 1);
        }
        return true;
    }

    private static void cString(byte[] value, int minimum, int maximum) {
        length(value, minimum, maximum);
        FieldReader reader = new FieldReader(value, maximum);
        reader.readCOctetString(maximum);
        if (reader.remaining() != 0) throw malformed();
    }

    private static void length(byte[] value, int minimum, int maximum) {
        if (value.length < minimum || value.length > maximum) throw malformed();
    }

    private static void digits(byte[] value, int start, int end) {
        for (int i = start; i < end; i++) if (value[i] < '0' || value[i] > '9') throw malformed();
    }

    private static FieldCodecException malformed() {
        return new FieldCodecException("Malformed standard message TLV value");
    }
}
