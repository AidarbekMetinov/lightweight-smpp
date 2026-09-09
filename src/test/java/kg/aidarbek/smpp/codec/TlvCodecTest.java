package kg.aidarbek.smpp.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

class TlvCodecTest {
    @Test
    void boundsEncodingBeforeAllocatingAndAcceptsMaximumWireValueLength() {
        OptionalParameters parameters = new OptionalParameters(List.of(new Tlv(0xffff, new byte[65535])));
        assertThrows(IllegalArgumentException.class, () -> TlvCodec.encode(parameters, 65538, 1));
        assertThrows(IllegalArgumentException.class, () -> TlvCodec.encode(parameters, 65539, 0));
        assertThrows(IllegalArgumentException.class, () -> TlvCodec.encode(parameters, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> TlvCodec.encode(parameters, 65539, -1));
        byte[] encoded = TlvCodec.encode(parameters, 65539, 1);
        assertEquals(65539, encoded.length);
        assertArrayEquals(
                new byte[] {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff}, java.util.Arrays.copyOf(encoded, 4));
        assertEquals(parameters, TlvCodec.decode(encoded, 65539, 1));
        OptionalParameters overflow = new OptionalParameters(
                java.util.Collections.nCopies(32768, parameters.entries().get(0)));
        IllegalArgumentException failure =
                assertThrows(IllegalArgumentException.class, () -> TlvCodec.encode(overflow, Integer.MAX_VALUE, 32768));
        assertEquals("TLV block exceeds configured byte bound", failure.getMessage());
    }

    @Test
    void encodesOrderedRawValuesAsIndependentBytes() {
        OptionalParameters parameters = new OptionalParameters(List.of(
                new Tlv(7, new byte[] {4}), new Tlv(0xffff, new byte[0]), new Tlv(7, new byte[] {(byte) 0xff})));
        assertArrayEquals(
                new byte[] {0, 7, 0, 1, 4, (byte) 0xff, (byte) 0xff, 0, 0, 0, 7, 0, 1, (byte) 0xff},
                TlvCodec.encode(parameters, 14, 3));
        assertArrayEquals(new byte[0], TlvCodec.encode(new OptionalParameters(List.of()), 0, 0));
    }

    @Test
    void rejectsTruncatedHeadersAndValuesWithoutReadingOutsideBlock() {
        byte[][] malformed = {
            {0x14}, {0x14, 0}, {0x14, 0, 0}, {0x14, 0, 0, 1}, {0x14, 0, 0, 2, 1}, {0x14, 0, (byte) 0xff, (byte) 0xff, 1}
        };
        for (byte[] encoded : malformed) {
            assertThrows(FieldCodecException.class, () -> TlvCodec.decode(encoded, encoded.length, 1));
        }
    }

    @Test
    void rejectsCountAndByteBoundsBeforeAcceptingOptionalData() {
        byte[] twoEmptyValues = {0x14, 0, 0, 0, 0x14, 0, 0, 0};
        assertThrows(IllegalArgumentException.class, () -> TlvCodec.decode(twoEmptyValues, 8, 1));
        assertThrows(IllegalArgumentException.class, () -> TlvCodec.decode(twoEmptyValues, 7, 2));
        assertThrows(IllegalArgumentException.class, () -> TlvCodec.decode(new byte[0], 0, -1));
        assertThrows(IllegalArgumentException.class, () -> TlvCodec.decode(new byte[0], -1, 0));
    }

    @Test
    void decodesKnownUnknownRepeatedAndEmptyValuesInWireOrder() {
        byte[] fixture = {0, 7, 0, 1, 4, 0x14, 0, 0, 2, (byte) 0xff, 0, 0x14, 0, 0, 0};
        OptionalParameters expected = new OptionalParameters(List.of(
                new Tlv(7, new byte[] {4}),
                new Tlv(0x1400, new byte[] {(byte) 0xff, 0}),
                new Tlv(0x1400, new byte[0])));
        OptionalParameters decoded = TlvCodec.decode(fixture, 15, 3);
        fixture[4] = 99;
        assertEquals(expected, decoded);
        assertEquals(new OptionalParameters(List.of()), TlvCodec.decode(new byte[0], 0, 0));
    }
}
