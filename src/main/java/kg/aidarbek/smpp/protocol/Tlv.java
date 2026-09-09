package kg.aidarbek.smpp.protocol;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable, thread-safe raw TLV with an unsigned tag and defensively owned value octets.
 * All tags, including unknown and reserved tags, can be represented. Equality and hashing include
 * the tag and binary value content; diagnostics include only the tag and value length.
 */
public final class Tlv {
    private final int tag;
    private final byte[] value;

    /**
     * Copies a raw value after checking the two-octet wire widths.
     *
     * @param tag unsigned tag in 0..65535
     * @param value non-null value of 0..65535 octets, excluding the four-octet TLV header
     * @throws IllegalArgumentException if the tag or value length exceeds its wire width
     */
    public Tlv(int tag, byte[] value) {
        Objects.requireNonNull(value, "value");
        if (tag < 0 || tag > 0xffff) {
            throw new IllegalArgumentException("TLV tag must be an unsigned 16-bit value");
        }
        if (value.length > 0xffff) {
            throw new IllegalArgumentException("TLV value exceeds unsigned 16-bit length");
        }
        this.tag = tag;
        this.value = value.clone();
    }

    /**
     * Returns the unsigned tag in 0..65535.
     *
     * @return the unsigned tag in 0..65535
     */
    public int tag() {
        return tag;
    }

    /**
     * Returns a fresh caller-owned copy of the raw value octets.
     *
     * @return a fresh caller-owned copy of the raw value octets
     */
    public byte[] value() {
        return value.clone();
    }

    /**
     * Returns the value length in octets, excluding tag and length fields.
     *
     * @return the value length in octets, excluding tag and length fields
     */
    public int valueLength() {
        return value.length;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Tlv that && tag == that.tag && Arrays.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return 31 * Integer.hashCode(tag) + Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return "Tlv[tag=0x" + Integer.toHexString(tag) + ", valueLength=" + value.length + "]";
    }
}
