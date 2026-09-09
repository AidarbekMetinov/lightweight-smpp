package kg.aidarbek.smpp.message;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

class MessageSegmentsTest {
    @Test
    void maximumPartCountsAndConfigurationBoundsRemainExact() {
        List<MessageSegment> maximum = MessageSegments.split("€".repeat(255), TextEncoding.GSM7_UNPACKED, 65535, 2, 2);
        assertEquals(255, maximum.size());
        assertEquals(255, maximum.getLast().number());
        assertEquals(255, maximum.getFirst().total());
        assertThrows(
                IllegalArgumentException.class,
                () -> MessageSegments.split("A^".repeat(128), TextEncoding.GSM7_UNPACKED, 0, 3, 2));
        for (int[] invalid : new int[][] {{-1, 1, 1}, {65536, 1, 1}, {0, 0, 1}, {0, 65536, 1}, {0, 3, 0}, {0, 3, 4}})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> MessageSegments.split("A", TextEncoding.GSM7_UNPACKED, invalid[0], invalid[1], invalid[2]));
        assertThrows(NullPointerException.class, () -> MessageSegments.split("A", null, 0, 1, 1));
        assertThrows(
                NullPointerException.class, () -> MessageSegments.split(null, TextEncoding.GSM7_UNPACKED, 0, 1, 1));
    }

    @Test
    void sarTrioPreservesReferenceAndRejectsAmbiguousOrIncompleteMetadata() {
        MessageSegment segment = new MessageSegment(0x1234, 2, 1, new OctetString(new byte[] {65}));
        List<Tlv> parameters = segment.sarParameters().entries();
        assertEquals(
                List.of(0x020c, 0x020e, 0x020f),
                parameters.stream().map(Tlv::tag).toList());
        assertArrayEquals(new byte[] {0x12, 0x34}, parameters.get(0).value());
        assertArrayEquals(new byte[] {2}, parameters.get(1).value());
        assertArrayEquals(new byte[] {1}, parameters.get(2).value());
        assertEquals(
                segment,
                MessageSegments.fromSar(segment.payload(), segment.sarParameters())
                        .orElseThrow());
        assertTrue(MessageSegments.fromSar(
                        segment.payload(), new OptionalParameters(List.of(new Tlv(0x1400, new byte[] {7}))))
                .isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> MessageSegments.fromSar(segment.payload(), new OptionalParameters(List.of(parameters.get(0)))));
        assertThrows(
                IllegalArgumentException.class,
                () -> MessageSegments.fromSar(
                        segment.payload(),
                        new OptionalParameters(
                                List.of(parameters.get(0), parameters.get(0), parameters.get(1), parameters.get(2)))));
        assertThrows(
                IllegalArgumentException.class,
                () -> MessageSegments.fromSar(
                        segment.payload(),
                        new OptionalParameters(
                                List.of(new Tlv(0x020c, new byte[] {1}), parameters.get(1), parameters.get(2)))));
        for (int[] values : new int[][] {{-1, 1, 1}, {65536, 1, 1}, {0, 0, 1}, {0, 256, 1}, {0, 2, 0}, {0, 2, 3}})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new MessageSegment(values[0], values[1], values[2], segment.payload()));
        assertThrows(
                IllegalArgumentException.class, () -> new MessageSegment(0, 1, 1, new OctetString(new byte[65536])));
        assertThrows(NullPointerException.class, () -> new MessageSegment(0, 1, 1, null));
    }

    @Test
    void segmentationCountsEncodedUnitsAndNeverSplitsGsmEscapesOrUcs2Words() {
        assertEquals(
                1,
                MessageSegments.split("A".repeat(160), TextEncoding.GSM7_UNPACKED, 0x1234, 160, 153)
                        .size());
        List<MessageSegment> gsm = MessageSegments.split(
                "A".repeat(152) + "€" + "B".repeat(8), TextEncoding.GSM7_UNPACKED, 0x1234, 160, 153);
        assertEquals(
                List.of(152, 10),
                gsm.stream().map(part -> part.payload().length()).toList());
        assertEquals(
                "€" + "B".repeat(8),
                TextEncoding.GSM7_UNPACKED.decode(gsm.get(1).payload()));
        assertEquals(2, gsm.get(0).total());
        assertEquals(2, gsm.get(1).number());
        assertEquals(0x1234, gsm.get(1).reference());
        List<MessageSegment> ucs2 = MessageSegments.split("中".repeat(71), TextEncoding.UCS2, 0, 140, 133);
        assertEquals(
                List.of(132, 10),
                ucs2.stream().map(part -> part.payload().length()).toList());
        assertEquals(
                1, MessageSegments.split("", TextEncoding.UCS2, 0, 140, 134).size());
        assertThrows(UnsupportedOperationException.class, () -> gsm.clear());
        assertThrows(
                IllegalArgumentException.class, () -> MessageSegments.split("^", TextEncoding.GSM7_UNPACKED, 0, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> MessageSegments.split("A".repeat(256), TextEncoding.GSM7_UNPACKED, 0, 1, 1));
    }
}
