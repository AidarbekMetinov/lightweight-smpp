package kg.aidarbek.smpp.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class PduFramerTest {
    private static final byte[] ENQUIRE = HexFormat.of().parseHex("00000010000000150000000000000001");
    private static final byte[] BODY_FRAME = HexFormat.of().parseHex("00000014000000040000000000000002aabbccdd");

    @Test
    void emitsOneCompleteFrameAndLeavesTheNextForTheCaller() {
        ByteBuffer source = ByteBuffer.allocate(36).put(ENQUIRE).put(BODY_FRAME).flip();
        PduFramer framer = new PduFramer(20);

        assertArrayEquals(ENQUIRE, framer.read(source).orElseThrow());
        assertEquals(16, source.position());
        assertArrayEquals(BODY_FRAME, framer.read(source).orElseThrow());
        assertEquals(36, source.position());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19})
    void retainsPartialInputAtEverySplit(int split) {
        PduFramer framer = new PduFramer(20);
        ByteBuffer first = ByteBuffer.wrap(BODY_FRAME, 0, split);
        assertTrue(framer.read(first).isEmpty());
        assertEquals(split, first.position());

        assertArrayEquals(
                BODY_FRAME,
                framer.read(ByteBuffer.wrap(BODY_FRAME, split, 20 - split)).orElseThrow());
        assertTrue(framer.read(ByteBuffer.allocate(0)).isEmpty());
    }

    @Test
    void retainsPartialThirdFrameAfterTwoCompleteFrames() {
        ByteBuffer source = ByteBuffer.allocate(40)
                .put(ENQUIRE)
                .put(ENQUIRE)
                .put(BODY_FRAME, 0, 8)
                .flip();
        PduFramer framer = new PduFramer(20);

        assertArrayEquals(ENQUIRE, framer.read(source).orElseThrow());
        assertArrayEquals(ENQUIRE, framer.read(source).orElseThrow());
        assertTrue(framer.read(source).isEmpty());
        assertEquals(40, source.position());
        assertArrayEquals(
                BODY_FRAME, framer.read(ByteBuffer.wrap(BODY_FRAME, 8, 12)).orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 15, 21, 0x80000000L, 0xffffffffL})
    void rejectsInvalidLengthOnItsFourthOctetAndBecomesTerminal(long length) {
        PduFramer framer = new PduFramer(20);
        ByteBuffer source =
                ByteBuffer.allocate(8).putInt((int) length).putInt(0x12345678).flip();
        source.limit(3);
        assertTrue(framer.read(source).isEmpty());
        source.limit(8);

        assertThrows(IllegalArgumentException.class, () -> framer.read(source));
        assertEquals(4, source.position());
        assertThrows(IllegalStateException.class, () -> framer.read(source));
        assertEquals(4, source.position());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 15})
    void rejectsAnUnusableMaximum(int maximum) {
        assertThrows(IllegalArgumentException.class, () -> new PduFramer(maximum));
    }

    @Test
    void deliversEarlierFramesBeforeReportingALaterInvalidLength() {
        PduFramer framer = new PduFramer(16);
        ByteBuffer source =
                ByteBuffer.allocate(36).put(ENQUIRE).put(ENQUIRE).putInt(-1).flip();
        assertArrayEquals(ENQUIRE, framer.read(source).orElseThrow());
        assertArrayEquals(ENQUIRE, framer.read(source).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> framer.read(source));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 4, 15, 19})
    void detectsTruncatedInputAtEndAndTerminates(int available) {
        PduFramer framer = new PduFramer(20);
        assertTrue(framer.read(ByteBuffer.wrap(BODY_FRAME, 0, available)).isEmpty());

        assertThrows(IllegalArgumentException.class, framer::endOfInput);
        assertThrows(IllegalStateException.class, () -> framer.read(ByteBuffer.wrap(ENQUIRE)));
        framer.endOfInput();
    }

    @Test
    void completesAtFrameBoundaryAndEndsIdempotently() {
        PduFramer framer = new PduFramer(16);
        assertArrayEquals(ENQUIRE, framer.read(ByteBuffer.wrap(ENQUIRE)).orElseThrow());
        framer.endOfInput();
        framer.endOfInput();
        ByteBuffer source = ByteBuffer.wrap(ENQUIRE);
        assertThrows(IllegalStateException.class, () -> framer.read(source));
        assertEquals(0, source.position());
    }

    @Test
    void ownsRetainedBytesAndTransfersCompletedFrameOwnership() {
        PduFramer framer = new PduFramer(20);
        byte[] reused = BODY_FRAME.clone();
        assertTrue(framer.read(ByteBuffer.wrap(reused, 0, 10)).isEmpty());
        Arrays.fill(reused, (byte) 0);
        byte[] frame = framer.read(ByteBuffer.wrap(BODY_FRAME, 10, 10)).orElseThrow();
        assertArrayEquals(BODY_FRAME, frame);
        Arrays.fill(frame, (byte) 0);
        assertArrayEquals(BODY_FRAME, framer.read(ByteBuffer.wrap(BODY_FRAME)).orElseThrow());
    }

    @Test
    void consumesReadOnlyDirectSliceRegardlessOfByteOrder() {
        ByteBuffer backing = ByteBuffer.allocateDirect(22)
                .put((byte) 0xff)
                .put(BODY_FRAME)
                .put((byte) 0xee)
                .flip();
        backing.position(1);
        backing.limit(21);
        ByteBuffer source = backing.slice().asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);

        assertArrayEquals(BODY_FRAME, new PduFramer(20).read(source).orElseThrow());
        assertEquals(20, source.position());
        assertEquals(ByteOrder.LITTLE_ENDIAN, source.order());
        assertEquals(1, backing.position());
    }

    @Test
    void rejectsNullWithoutPoisoningTheFramer() {
        PduFramer framer = new PduFramer(16);
        assertThrows(NullPointerException.class, () -> framer.read(null));
        assertArrayEquals(ENQUIRE, framer.read(ByteBuffer.wrap(ENQUIRE)).orElseThrow());
    }

    @Test
    void acceptsAnEmptyStreamAndTerminates() {
        PduFramer framer = new PduFramer(16);
        framer.endOfInput();
        framer.endOfInput();
        assertThrows(IllegalStateException.class, () -> framer.read(ByteBuffer.allocate(0)));
    }
}
