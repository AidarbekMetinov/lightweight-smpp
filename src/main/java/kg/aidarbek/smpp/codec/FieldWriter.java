package kg.aidarbek.smpp.codec;

import java.util.Arrays;
import java.util.Objects;

/**
 * Writes SMPP binary fields in big endian order into a fixed-capacity owned buffer.
 *
 * <p>Instances are mutable and require thread confinement. Writes preflight their range, complete
 * encoded length and content before changing output; any rejected write leaves existing output
 * unchanged. Command-specific field semantics are validated separately.
 */
public final class FieldWriter {
    private final byte[] output;
    private int position;

    /**
     * Allocates a buffer of the configured maximum size.
     *
     * @param maximumLength nonnegative output capacity in octets
     * @throws IllegalArgumentException if the capacity is negative
     */
    public FieldWriter(int maximumLength) {
        if (maximumLength < 0) {
            throw new IllegalArgumentException("Maximum field length must not be negative");
        }
        output = new byte[maximumLength];
    }

    /**
     * Writes one octet.
     *
     * @param value unsigned value in 0..255
     * @throws IllegalArgumentException if the value is outside its range or capacity is exhausted
     */
    public void writeUnsignedByte(int value) {
        requireUnsigned(value, 0xff);
        require(1);
        output[position++] = (byte) value;
    }

    /**
     * Writes a two-octet integer.
     *
     * @param value unsigned value in 0..65535
     * @throws IllegalArgumentException if the value is outside its range or fewer than two octets fit
     */
    public void writeUnsignedShort(int value) {
        requireUnsigned(value, 0xffff);
        require(2);
        writeUnsignedByte(value >>> 8);
        writeUnsignedByte(value & 0xff);
    }

    /**
     * Writes a four-octet integer.
     *
     * @param value unsigned value in 0..4294967295
     * @throws IllegalArgumentException if the value is outside its range or fewer than four octets fit
     */
    public void writeUnsignedInt(long value) {
        requireUnsigned(value, 0xffff_ffffL);
        require(4);
        writeUnsignedShort((int) (value >>> 16));
        writeUnsignedShort((int) (value & 0xffff));
    }

    /**
     * Returns a fresh caller-owned copy of exactly the octets written so far.
     *
     * @return a fresh caller-owned copy of exactly the octets written so far
     */
    public byte[] toByteArray() {
        return Arrays.copyOf(output, position);
    }

    /**
     * Copies raw octets without retaining the caller's array.
     *
     * @param value non-null bytes to append
     * @throws IllegalArgumentException if the complete value does not fit
     */
    public void writeOctets(byte[] value) {
        Objects.requireNonNull(value, "value");
        require(value.length);
        System.arraycopy(value, 0, output, position, value.length);
        position += value.length;
    }

    /**
     * Writes a strict ASCII string followed by one NUL octet. ASCII controls and DEL are accepted;
     * embedded NULs and all characters above U+007F are rejected without replacement encoding.
     *
     * @param value non-null string, possibly empty
     * @param maximumOctets positive field bound including the final NUL octet
     * @throws IllegalArgumentException if text is invalid, the field bound is invalid or exceeded, or output is full
     */
    public void writeCOctetString(String value, int maximumOctets) {
        Objects.requireNonNull(value, "value");
        if (maximumOctets < 1 || value.length() >= maximumOctets) {
            throw new IllegalArgumentException("C-octet string exceeds terminator-inclusive bound");
        }
        require(value.length() + 1);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == 0 || character > 0x7f) {
                throw new IllegalArgumentException("C-octet string requires non-NUL ASCII characters");
            }
        }
        for (int index = 0; index < value.length(); index++) {
            output[position++] = (byte) value.charAt(index);
        }
        output[position++] = 0;
    }

    private void require(int length) {
        if (length > output.length - position) {
            throw new IllegalArgumentException("Field exceeds configured output bound");
        }
    }

    private static void requireUnsigned(long value, long maximum) {
        if (value < 0 || value > maximum) {
            throw new IllegalArgumentException("Value is outside unsigned field range");
        }
    }
}
