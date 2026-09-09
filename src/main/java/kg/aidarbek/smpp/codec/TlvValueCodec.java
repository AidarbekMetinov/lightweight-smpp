package kg.aidarbek.smpp.codec;

import java.util.Optional;

/**
 * An extension boundary for interpreting one registered TLV value independently of raw framing.
 *
 * <p>Implementations must be thread-safe, keep metadata stable, return immutable typed values and
 * avoid logging value contents. They must not retain or mutate decoding input. An encoded value
 * must be a fresh caller-owned array no longer than 65535 octets. The registry supplies context;
 * the raw TLV codec continues to enforce enclosing block bounds.
 *
 * @param <T> immutable typed value representation
 */
public interface TlvValueCodec<T> {
    /**
     * Returns the stable, non-null reference class of decoded values, never a primitive class.
     *
     * @return the stable, non-null reference class of decoded values, never a primitive class
     */
    Class<T> valueType();

    /**
     * Interprets structurally valid content, preserving forward compatibility for unsupported values.
     *
     * @param value non-null complete TLV value octets, excluding tag and length
     * @return a non-null optional containing a supported value, or empty for a reserved/unsupported value
     * @throws FieldCodecException if the known field's value length or structure is malformed
     */
    Optional<T> decode(byte[] value);

    /**
     * Encodes a value in this codec's declared domain and rejects values outside that domain.
     * A codec preserving a raw numeric field may deliberately accept the entire wire range;
     * command-specific semantic restrictions must then be validated separately.
     *
     * @param value non-null supported typed value
     * @return a non-null, fresh caller-owned array of 0..65535 octets
     * @throws IllegalArgumentException if the value cannot be encoded in this codec's supported domain
     */
    byte[] encode(T value);
}
