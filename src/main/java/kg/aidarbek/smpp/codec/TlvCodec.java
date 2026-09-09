package kg.aidarbek.smpp.codec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Tlv;

/**
 * Stateless, thread-safe framing of raw TLV blocks; no tag is interpreted or deduplicated here.
 * Callers supply only the already bounded trailing TLV region of a PDU, excluding mandatory
 * non-TLV fields, and continue to enforce the enclosing PDU's total length separately.
 */
public final class TlvCodec {
    private TlvCodec() {}

    /**
     * Decodes a complete raw block in wire order. Input is copied only after its length is bounded;
     * declared value lengths are checked against remaining bytes before value allocation.
     *
     * @param encoded non-null complete TLV block
     * @param maximumLength nonnegative maximum block size in octets, including all TLV headers
     * @param maximumCount nonnegative maximum number of entries, including repeated tags
     * @return immutable entries owning all their value octets
     * @throws IllegalArgumentException if configured bounds are invalid or the byte bound is exceeded
     * @throws FieldCodecException if a header/value is truncated or the entry-count bound is exceeded
     */
    public static OptionalParameters decode(byte[] encoded, int maximumLength, int maximumCount) {
        if (maximumCount < 0) {
            throw new IllegalArgumentException("Maximum TLV count must not be negative");
        }
        FieldReader reader = new FieldReader(encoded, maximumLength);
        List<Tlv> entries = new ArrayList<>();
        while (reader.remaining() > 0) {
            if (entries.size() == maximumCount) {
                throw new FieldCodecException("TLV count exceeds configured bound");
            }
            int tag = reader.readUnsignedShort();
            int length = reader.readUnsignedShort();
            entries.add(new Tlv(tag, reader.readOctets(length)));
        }
        return new OptionalParameters(entries);
    }

    /**
     * Encodes entries verbatim after checking their aggregate size and count before buffer allocation.
     * The aggregate length is computed without 32-bit overflow.
     *
     * @param parameters non-null raw entries in desired wire order
     * @param maximumLength nonnegative maximum block size in octets, including all TLV headers
     * @param maximumCount nonnegative maximum entry count
     * @return a fresh caller-owned encoded block
     * @throws IllegalArgumentException if a configured bound is invalid or exceeded
     */
    public static byte[] encode(OptionalParameters parameters, int maximumLength, int maximumCount) {
        Objects.requireNonNull(parameters, "parameters");
        if (maximumLength < 0 || maximumCount < 0) {
            throw new IllegalArgumentException("TLV bounds must not be negative");
        }
        if (parameters.entries().size() > maximumCount) {
            throw new IllegalArgumentException("TLV count exceeds configured bound");
        }
        long length = 0;
        for (Tlv entry : parameters.entries()) {
            length += 4 + entry.valueLength();
            if (length > maximumLength) {
                throw new IllegalArgumentException("TLV block exceeds configured byte bound");
            }
        }
        FieldWriter writer = new FieldWriter((int) length);
        for (Tlv entry : parameters.entries()) {
            writer.writeUnsignedShort(entry.tag());
            writer.writeUnsignedShort(entry.valueLength());
            writer.writeOctets(entry.value());
        }
        return writer.toByteArray();
    }
}
