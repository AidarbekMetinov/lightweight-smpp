package kg.aidarbek.smpp.codec;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import kg.aidarbek.smpp.protocol.PduHeader;

/** Stateless, thread-safe translation of the fixed SMPP header, independently of command bodies. */
public final class PduHeaderCodec {
    private PduHeaderCodec() {}

    /**
     * Reads exactly 16 octets at the source position using network byte order. No bytes are retained.
     *
     * <p>Failure leaves the source position unchanged. Callers must coordinate access to a shared buffer.
     *
     * @param source non-null buffer, whose limit and byte order are preserved
     * @return the raw header, including unknown command/status values
     * @throws BufferUnderflowException if fewer than 16 octets remain
     * @throws IllegalArgumentException if the encoded length is below 16
     * @throws NullPointerException if source is null
     */
    public static PduHeader decode(ByteBuffer source) {
        Objects.requireNonNull(source, "source");
        if (source.remaining() < PduHeader.LENGTH) {
            throw new BufferUnderflowException();
        }
        ByteBuffer view = source.duplicate().order(ByteOrder.BIG_ENDIAN);
        PduHeader header = new PduHeader(
                Integer.toUnsignedLong(view.getInt()),
                Integer.toUnsignedLong(view.getInt()),
                Integer.toUnsignedLong(view.getInt()),
                Integer.toUnsignedLong(view.getInt()));
        source.position(view.position());
        return header;
    }

    /**
     * Writes exactly 16 octets in network byte order and advances the destination position.
     *
     * <p>Failure leaves destination contents and position unchanged. Callers own and coordinate access to their buffers.
     *
     * @param header non-null validated raw header
     * @param destination non-null writable buffer; its limit and byte order are preserved
     * @throws BufferOverflowException if fewer than 16 octets remain
     * @throws java.nio.ReadOnlyBufferException if the destination is read-only
     * @throws NullPointerException if either argument is null
     */
    public static void encode(PduHeader header, ByteBuffer destination) {
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(destination, "destination");
        if (destination.remaining() < PduHeader.LENGTH) {
            throw new BufferOverflowException();
        }
        ByteBuffer view = destination.duplicate().order(ByteOrder.BIG_ENDIAN);
        view.putInt((int) header.commandLength());
        view.putInt((int) header.commandId());
        view.putInt((int) header.commandStatus());
        view.putInt((int) header.sequenceNumber());
        destination.position(view.position());
    }
}
