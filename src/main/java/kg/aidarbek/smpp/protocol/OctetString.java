package kg.aidarbek.smpp.protocol;

import java.util.Arrays;
import java.util.Objects;

/** Immutable binary message content, with octet equality and no character encoding. */
public final class OctetString {
    private final byte[] value;

    /**
     * Copies non-null content. Enclosing fields/codecs apply their wire and allocation limits.
     * @param value uninterpreted octets, copied immediately
     */
    public OctetString(byte[] value) {
        this.value = Objects.requireNonNull(value, "value").clone();
    }

    /**
     * Copies the immutable content without exposing internal storage.
     * @return a fresh caller-owned array
     */
    public byte[] value() {
        return value.clone();
    }

    /**
     * Measures binary content independently of character encoding.
     * @return the nonnegative octet count
     */
    public int length() {
        return value.length;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OctetString that && Arrays.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return "OctetString[length=" + value.length + "]";
    }
}
