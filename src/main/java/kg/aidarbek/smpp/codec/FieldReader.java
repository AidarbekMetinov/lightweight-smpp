package kg.aidarbek.smpp.codec;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * Reads SMPP binary fields from an owned, bounded block in big endian order.
 *
 * <p>Instances are mutable and require thread confinement. Each read advances only on success;
 * a rejected read leaves all bytes available. This reader does not assemble partial network input
 * or validate a field's command-specific meaning.
 */
public final class FieldReader {
    private final byte[] input;
    private int position;

    /**
     * Copies a complete field block after checking its allocation bound.
     *
     * @param input non-null input bytes, unaffected by subsequent reads
     * @param maximumLength nonnegative maximum input length in octets
     * @throws IllegalArgumentException if the bound is negative or the input exceeds it
     */
    public FieldReader(byte[] input, int maximumLength) {
        Objects.requireNonNull(input, "input");
        if (maximumLength < 0 || input.length > maximumLength) {
            throw new IllegalArgumentException("Input exceeds configured field bound");
        }
        this.input = input.clone();
    }

    /**
     * Reads one unsigned octet.
     *
     * @return a value in 0..255
     * @throws FieldCodecException if no octet remains
     */
    public int readUnsignedByte() {
        require(1);
        return input[position++] & 0xff;
    }

    /**
     * Reads a two-octet unsigned integer.
     *
     * @return a value in 0..65535
     * @throws FieldCodecException if fewer than two octets remain
     */
    public int readUnsignedShort() {
        require(2);
        return (readUnsignedByte() << 8) | readUnsignedByte();
    }

    /**
     * Reads a four-octet unsigned integer without sign extension.
     *
     * @return a value in 0..4294967295
     * @throws FieldCodecException if fewer than four octets remain
     */
    public long readUnsignedInt() {
        require(4);
        return ((long) readUnsignedShort() << 16) | readUnsignedShort();
    }

    /**
     * Returns the nonnegative count of unread octets.
     *
     * @return the nonnegative count of unread octets
     */
    public int remaining() {
        return input.length - position;
    }

    /**
     * Reads ASCII characters through the first NUL octet, consuming that terminator.
     * An empty string consumes one octet; ASCII controls and DEL are preserved.
     *
     * @param maximumOctets positive field limit including the terminator
     * @return the characters preceding the terminator
     * @throws IllegalArgumentException if the limit is not positive
     * @throws FieldCodecException if the available bounded field lacks a terminator or contains non-ASCII bytes
     */
    public String readCOctetString(int maximumOctets) {
        if (maximumOctets < 1) {
            throw new IllegalArgumentException("C-octet bound must include a terminator");
        }
        int available = Math.min(maximumOctets, remaining());
        for (int offset = 0; offset < available; offset++) {
            int octet = input[position + offset] & 0xff;
            if (octet == 0) {
                String result = new String(input, position, offset, StandardCharsets.US_ASCII);
                position += offset + 1;
                return result;
            }
            if (octet > 0x7f) {
                throw new FieldCodecException("Non-ASCII C-octet field at offset " + position);
            }
        }
        throw new FieldCodecException("Missing C-octet terminator within bound at offset " + position);
    }

    /**
     * Reads an exact number of uninterpreted octets into a new caller-owned array.
     *
     * @param length nonnegative octet count
     * @return an independent array, including an empty array for zero length
     * @throws IllegalArgumentException if the length is negative
     * @throws FieldCodecException if fewer than the requested octets remain
     */
    public byte[] readOctets(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("Octet count must not be negative");
        }
        require(length);
        byte[] result = Arrays.copyOfRange(input, position, position + length);
        position += length;
        return result;
    }

    private void require(int length) {
        if (length > remaining()) {
            throw new FieldCodecException("Truncated field at offset " + position);
        }
    }
}
