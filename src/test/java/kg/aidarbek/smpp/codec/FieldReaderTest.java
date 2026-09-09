package kg.aidarbek.smpp.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FieldReaderTest {
    @Test
    void malformedStringsLeaveReaderAtTheStart() {
        byte[][] inputs = {{0x41}, {0x41, 0}, {(byte) 0x80, 0}, {(byte) 0xff, 0}};
        int[] bounds = {2, 1, 2, 2};
        for (int index = 0; index < inputs.length; index++) {
            byte[] input = inputs[index];
            int bound = bounds[index];
            FieldReader reader = new FieldReader(input, input.length);
            assertThrows(FieldCodecException.class, () -> reader.readCOctetString(bound));
            assertEquals(input.length, reader.remaining());
            assertArrayEquals(input, reader.readOctets(input.length));
        }
        FieldReader reader = new FieldReader(new byte[] {0}, 1);
        assertThrows(IllegalArgumentException.class, () -> reader.readCOctetString(0));
        assertEquals(1, reader.remaining());
    }

    @Test
    void readsAsciiStringWithinTerminatorInclusiveBound() {
        FieldReader reader = new FieldReader(new byte[] {0x48, 0x65, 0x6c, 0x6c, 0x6f, 0, 0, 0x7f, 0}, 9);
        assertEquals("Hello", reader.readCOctetString(6));
        assertEquals("", reader.readCOctetString(1));
        assertEquals("\u007f", reader.readCOctetString(2));
        assertEquals(0, reader.remaining());
    }

    @Test
    void boundsAndOwnsInputAndReturnedOctets() {
        byte[] input = {1, 2, 3};
        assertThrows(IllegalArgumentException.class, () -> new FieldReader(input, 2));
        assertThrows(IllegalArgumentException.class, () -> new FieldReader(new byte[0], -1));
        FieldReader reader = new FieldReader(input, 3);
        input[0] = 99;
        assertThrows(IllegalArgumentException.class, () -> reader.readOctets(-1));
        assertThrows(FieldCodecException.class, () -> reader.readOctets(4));
        assertEquals(3, reader.remaining());
        assertArrayEquals(new byte[] {1, 2}, reader.readOctets(2));
        assertArrayEquals(new byte[] {3}, reader.readOctets(1));
        assertArrayEquals(new byte[0], reader.readOctets(0));
    }

    @Test
    void truncatedIntegerDoesNotConsumeAnyBytes() {
        FieldReader reader = new FieldReader(new byte[] {0x12, 0x34, 0x56}, 3);
        assertThrows(FieldCodecException.class, reader::readUnsignedInt);
        assertEquals(3, reader.remaining());
        assertEquals(0x1234, reader.readUnsignedShort());
        assertThrows(FieldCodecException.class, reader::readUnsignedShort);
        assertEquals(1, reader.remaining());
        assertEquals(0x56, reader.readUnsignedByte());
        assertThrows(FieldCodecException.class, reader::readUnsignedByte);
    }

    @Test
    void readsUnsignedIntegersFromIndependentBigEndianBytes() {
        FieldReader reader = new FieldReader(
                new byte[] {(byte) 0xff, (byte) 0xa3, 0x12, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff}, 7);

        assertEquals(255, reader.readUnsignedByte());
        assertEquals(41746, reader.readUnsignedShort());
        assertEquals(0xffff_ffffL, reader.readUnsignedInt());
        assertEquals(0, reader.remaining());
    }
}
