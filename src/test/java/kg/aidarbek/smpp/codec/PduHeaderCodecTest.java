package kg.aidarbek.smpp.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.util.HexFormat;
import kg.aidarbek.smpp.protocol.PduHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class PduHeaderCodecTest {
    @Test
    void encodesIndependentEnquireLinkBytes() {
        ByteBuffer destination = ByteBuffer.allocate(16);

        PduHeaderCodec.encode(new PduHeader(16, 0x15, 0, 1), destination);

        assertArrayEquals(HexFormat.of().parseHex("00000010000000150000000000000001"), destination.array());
    }

    @Test
    void decodesUnsignedFieldsAndConsumesOnlyTheHeader() {
        ByteBuffer source = ByteBuffer.wrap(HexFormat.of().parseHex("0000001480000015ffffffffffffffffaabbccdd"));

        assertEquals(new PduHeader(20, 0x80000015L, 0xffffffffL, 0xffffffffL), PduHeaderCodec.decode(source));
        assertEquals(16, source.position());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void usesNetworkOrderAndCurrentPositionForHeapAndDirectBuffers(boolean direct) {
        ByteBuffer buffer =
                (direct ? ByteBuffer.allocateDirect(20) : ByteBuffer.allocate(20)).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(2);
        buffer.limit(18);
        PduHeader header = new PduHeader(16, 0x80000015L, 0xffffffffL, 1);

        PduHeaderCodec.encode(header, buffer);

        assertEquals(18, buffer.position());
        assertEquals(ByteOrder.LITTLE_ENDIAN, buffer.order());
        byte[] encoded = new byte[16];
        buffer.position(2);
        buffer.get(encoded);
        assertArrayEquals(HexFormat.of().parseHex("0000001080000015ffffffff00000001"), encoded);
        buffer.position(2);
        assertEquals(header, PduHeaderCodec.decode(buffer));
        assertEquals(18, buffer.position());
        assertEquals(18, buffer.limit());
        assertEquals(ByteOrder.LITTLE_ENDIAN, buffer.order());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3, 4, 15})
    void rejectsIncompleteHeaderWithoutConsumingInput(int available) {
        ByteBuffer source = ByteBuffer.wrap(HexFormat.of().parseHex("00000010000000150000000000000001"));
        source.limit(available);

        assertThrows(BufferUnderflowException.class, () -> PduHeaderCodec.decode(source));
        assertEquals(0, source.position());
    }

    @Test
    void rejectsShortDestinationWithoutWritingAnyBytes() {
        ByteBuffer destination = ByteBuffer.allocate(16);
        destination.position(1);
        byte[] original = destination.array().clone();

        assertThrows(
                BufferOverflowException.class, () -> PduHeaderCodec.encode(new PduHeader(16, 0x15, 0, 1), destination));
        assertArrayEquals(original, destination.array());
        assertEquals(1, destination.position());
    }

    @Test
    void rejectsMalformedLengthWithoutConsumingHeader() {
        ByteBuffer source = ByteBuffer.wrap(HexFormat.of().parseHex("0000000f000000150000000000000001"));

        assertThrows(IllegalArgumentException.class, () -> PduHeaderCodec.decode(source));
        assertEquals(0, source.position());
    }

    @Test
    void decodesReadOnlySliceWithSequenceZero() {
        ByteBuffer source = ByteBuffer.wrap(HexFormat.of().parseHex("ff00000010800000010000000300000000aa"));
        source.position(1);
        source.limit(17);
        ByteBuffer slice = source.slice().asReadOnlyBuffer();

        assertEquals(new PduHeader(16, 0x80000001L, 3, 0), PduHeaderCodec.decode(slice));
        assertEquals(16, slice.position());
        assertEquals(1, source.position());
    }

    @Test
    void rejectsReadOnlyDestinationWithoutChangingContentsOrPosition() {
        byte[] bytes = new byte[18];
        ByteBuffer destination = ByteBuffer.wrap(bytes).asReadOnlyBuffer();
        destination.position(1);

        assertThrows(
                ReadOnlyBufferException.class, () -> PduHeaderCodec.encode(new PduHeader(16, 0x15, 0, 1), destination));
        assertArrayEquals(new byte[18], bytes);
        assertEquals(1, destination.position());
    }
}
