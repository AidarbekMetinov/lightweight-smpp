package kg.aidarbek.smpp.codec;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable, thread-safe interpretation of a one-octet TLV whose supported values start at zero.
 * A reserved incoming value above the configured maximum is ignored semantically; an outgoing
 * value above it is rejected. Raw storage remains independent of this interpretation.
 */
public final class UnsignedByteTlvCodec implements TlvValueCodec<Integer> {
    private final int maximumValue;

    /**
     * Configures the inclusive supported range.
     *
     * @param maximumValue inclusive maximum in 0..255
     * @throws IllegalArgumentException if the maximum is outside the unsigned-octet range
     */
    public UnsignedByteTlvCodec(int maximumValue) {
        if (maximumValue < 0 || maximumValue > 0xff) {
            throw new IllegalArgumentException("Maximum TLV value must be an unsigned octet");
        }
        this.maximumValue = maximumValue;
    }

    @Override
    public Class<Integer> valueType() {
        return Integer.class;
    }

    @Override
    public Optional<Integer> decode(byte[] value) {
        Objects.requireNonNull(value, "value");
        if (value.length != 1) {
            throw new FieldCodecException("Unsigned-octet TLV requires exactly one value octet");
        }
        int decoded = value[0] & 0xff;
        return decoded <= maximumValue ? Optional.of(decoded) : Optional.empty();
    }

    @Override
    public byte[] encode(Integer value) {
        Objects.requireNonNull(value, "value");
        if (value < 0 || value > maximumValue) {
            throw new IllegalArgumentException("Unsigned-octet TLV value is outside the supported range");
        }
        return new byte[] {value.byteValue()};
    }
}
