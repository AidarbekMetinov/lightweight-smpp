package kg.aidarbek.smpp.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FieldWriterTest {
    @Test
    void rejectsEmbeddedNullEvenWhenTheCompleteStringFits() {
        FieldWriter writer = new FieldWriter(8);
        assertThrows(IllegalArgumentException.class, () -> writer.writeCOctetString("a\0b", 8));
        assertArrayEquals(new byte[0], writer.toByteArray());
    }

    @Test
    void rejectsNonAsciiEmbeddedNullAndStringBoundsAtomically() {
        FieldWriter writer = new FieldWriter(3);
        for (String invalid : new String[] {"a\u0000b", "\u0080", "\u20ac", "\ud83d\ude00"}) {
            assertThrows(IllegalArgumentException.class, () -> writer.writeCOctetString(invalid, 10));
            assertArrayEquals(new byte[0], writer.toByteArray());
        }
        assertThrows(IllegalArgumentException.class, () -> writer.writeCOctetString("", 0));
        assertThrows(IllegalArgumentException.class, () -> writer.writeCOctetString("abc", 3));
        assertThrows(IllegalArgumentException.class, () -> writer.writeCOctetString("abc", 4));
        assertThrows(IllegalArgumentException.class, () -> writer.writeOctets(new byte[4]));
        assertArrayEquals(new byte[0], writer.toByteArray());
        writer.writeCOctetString("ab", 3);
        assertArrayEquals(new byte[] {0x61, 0x62, 0}, writer.toByteArray());
    }

    @Test
    void writesTerminatedAsciiAndOwnedRawOctets() {
        FieldWriter writer = new FieldWriter(9);
        byte[] raw = {(byte) 0x80, (byte) 0xff};
        writer.writeOctets(raw);
        raw[0] = 0;
        writer.writeCOctetString("Hello", 6);
        writer.writeCOctetString("", 1);
        byte[] expected = {(byte) 0x80, (byte) 0xff, 0x48, 0x65, 0x6c, 0x6c, 0x6f, 0, 0};
        assertArrayEquals(expected, writer.toByteArray());
        byte[] exposed = writer.toByteArray();
        exposed[0] = 0;
        assertArrayEquals(expected, writer.toByteArray());
    }

    @Test
    void rejectsOutOfRangeAndOversizedWritesWithoutPartialOutput() {
        assertThrows(IllegalArgumentException.class, () -> new FieldWriter(-1));
        FieldWriter writer = new FieldWriter(3);
        assertThrows(IllegalArgumentException.class, () -> writer.writeUnsignedByte(-1));
        assertThrows(IllegalArgumentException.class, () -> writer.writeUnsignedByte(256));
        assertThrows(IllegalArgumentException.class, () -> writer.writeUnsignedShort(-1));
        assertThrows(IllegalArgumentException.class, () -> writer.writeUnsignedShort(65536));
        assertThrows(IllegalArgumentException.class, () -> writer.writeUnsignedInt(-1));
        assertThrows(IllegalArgumentException.class, () -> writer.writeUnsignedInt(0x1_0000_0000L));
        assertThrows(IllegalArgumentException.class, () -> writer.writeUnsignedInt(0));
        assertArrayEquals(new byte[0], writer.toByteArray());
        writer.writeUnsignedShort(0x1234);
        assertThrows(IllegalArgumentException.class, () -> writer.writeUnsignedShort(0));
        writer.writeUnsignedByte(0);
        assertThrows(IllegalArgumentException.class, () -> writer.writeUnsignedByte(0));
        assertArrayEquals(new byte[] {0x12, 0x34, 0}, writer.toByteArray());
    }

    @Test
    void writesUnsignedIntegersAsIndependentBigEndianBytes() {
        FieldWriter writer = new FieldWriter(7);
        writer.writeUnsignedByte(255);
        writer.writeUnsignedShort(41746);
        writer.writeUnsignedInt(0xffff_ffffL);
        assertArrayEquals(
                new byte[] {(byte) 0xff, (byte) 0xa3, 0x12, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff},
                writer.toByteArray());
    }
}
