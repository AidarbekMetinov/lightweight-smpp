package kg.aidarbek.smpp.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class UnsignedByteTlvCodecTest {
    @Test
    void distinguishesMalformedLengthsFromUnsupportedReservedValues() {
        TlvValueCodec<Integer> codec = new UnsignedByteTlvCodec(100);
        assertEquals(Optional.of(100), codec.decode(new byte[] {100}));
        assertEquals(Optional.empty(), codec.decode(new byte[] {101}));
        assertEquals(Optional.empty(), codec.decode(new byte[] {(byte) 0xff}));
        assertThrows(FieldCodecException.class, () -> codec.decode(new byte[0]));
        assertThrows(FieldCodecException.class, () -> codec.decode(new byte[] {1, 2}));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(-1));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(101));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(256));
        assertThrows(NullPointerException.class, () -> codec.encode(null));
        assertThrows(NullPointerException.class, () -> codec.decode(null));
        assertThrows(IllegalArgumentException.class, () -> new UnsignedByteTlvCodec(-1));
        assertThrows(IllegalArgumentException.class, () -> new UnsignedByteTlvCodec(256));
        byte[] output = codec.encode(100);
        output[0] = 0;
        assertArrayEquals(new byte[] {100}, codec.encode(100));
    }

    @Test
    void encodesAndInterpretsIndependentUnsignedOctets() {
        TlvValueCodec<Integer> codec = new UnsignedByteTlvCodec(255);
        assertEquals(Integer.class, codec.valueType());
        assertEquals(Optional.of(255), codec.decode(new byte[] {(byte) 0xff}));
        assertEquals(Optional.of(0), codec.decode(new byte[] {0}));
        assertEquals(Optional.of(0x50), codec.decode(new byte[] {0x50}));
        assertArrayEquals(new byte[] {(byte) 0xff}, codec.encode(255));
        assertArrayEquals(new byte[] {0}, codec.encode(0));
        assertArrayEquals(new byte[] {0x34}, codec.encode(0x34));
    }
}
