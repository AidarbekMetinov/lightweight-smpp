package kg.aidarbek.smpp.message;

import java.util.Objects;
import kg.aidarbek.smpp.protocol.OctetString;

/**
 * Explicit strict text transformations; no SMPP data-coding value selects one implicitly. GSM input
 * uses only the default and extension tables, without national shift tables, transliteration,
 * replacement or inferred packing. Decoding rejects reserved/unrecognized escape sequences rather
 * than applying a handset display fallback. UCS-2 rejects every surrogate code unit, including
 * otherwise well-formed UTF-16 pairs. NUL and an explicitly supplied BOM remain ordinary BMP data.
 */
public enum TextEncoding {
    /** GSM default alphabet and extension table, one uncompressed septet per octet. */
    GSM7_UNPACKED,
    /** Big-endian UCS-2 without an inserted byte-order mark; surrogate code units are rejected. */
    UCS2;

    private static final String DEFAULT = "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞ\u001bÆæßÉ"
            + " !\"#¤%&'()*+,-./0123456789:;<=>?¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà";
    private static final String EXTENSIONS = "\f^{}\\[~]|€";
    private static final int[] EXTENSION_CODES = {10, 20, 40, 41, 47, 60, 61, 62, 64, 101};

    /**
     * Encodes text without replacement or automatic alphabet changes.
     * @param text source
     * @return owned octets
     */
    public OctetString encode(String text) {
        byte[] bytes = new byte[encodedLength(text)];
        int offset = 0;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (this == UCS2) {
                bytes[offset++] = (byte) (character >>> 8);
                bytes[offset++] = (byte) character;
                continue;
            }
            int extension = EXTENSIONS.indexOf(character);
            if (extension >= 0) {
                bytes[offset++] = 27;
                bytes[offset++] = (byte) EXTENSION_CODES[extension];
            } else bytes[offset++] = (byte) DEFAULT.indexOf(character);
        }
        return new OctetString(bytes);
    }

    /**
     * Decodes explicitly selected encoding strictly.
     * @param payload encoded octets
     * @return text
     */
    public String decode(OctetString payload) {
        byte[] bytes = Objects.requireNonNull(payload, "payload").value();
        StringBuilder result = new StringBuilder(bytes.length);
        if (this == UCS2) {
            if ((bytes.length & 1) != 0) throw new IllegalArgumentException("UCS-2 needs complete two-octet units");
            for (int i = 0; i < bytes.length; i += 2) {
                char character = (char) (((bytes[i] & 255) << 8) | (bytes[i + 1] & 255));
                if (Character.isSurrogate(character))
                    throw new IllegalArgumentException("UCS-2 forbids surrogate code units");
                result.append(character);
            }
        } else {
            for (int i = 0; i < bytes.length; i++) {
                int value = bytes[i] & 255;
                if (value > 127) throw new IllegalArgumentException("Unpacked GSM septet exceeds seven bits");
                if (value != 27) result.append(DEFAULT.charAt(value));
                else {
                    if (++i == bytes.length) throw new IllegalArgumentException("Incomplete GSM extension escape");
                    int extension = -1;
                    for (int j = 0; j < EXTENSION_CODES.length; j++)
                        if (EXTENSION_CODES[j] == (bytes[i] & 255)) extension = j;
                    if (extension < 0) throw new IllegalArgumentException("Unsupported GSM extension code");
                    result.append(EXTENSIONS.charAt(extension));
                }
            }
        }
        return result.toString();
    }

    /**
     * Counts encoded octets, validating the entire text.
     * @param text source
     * @return encoded length
     */
    public int encodedLength(String text) {
        Objects.requireNonNull(text, "text");
        int length = 0;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (this == UCS2) {
                if (Character.isSurrogate(character))
                    throw new IllegalArgumentException("UCS-2 forbids surrogate code units at UTF-16 index " + i);
                length = Math.addExact(length, 2);
            } else if (EXTENSIONS.indexOf(character) >= 0) length = Math.addExact(length, 2);
            else if (character != 27 && DEFAULT.indexOf(character) >= 0) length = Math.addExact(length, 1);
            else throw new IllegalArgumentException("Character is outside the selected alphabet at UTF-16 index " + i);
        }
        return length;
    }
}
