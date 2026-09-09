package kg.aidarbek.smpp.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TlvTest {
    @Test
    void enforcesUnsignedTagAndValueLengthWithoutInterpretingUnknownTags() {
        assertThrows(IllegalArgumentException.class, () -> new Tlv(-1, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new Tlv(65536, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new Tlv(0xffff, new byte[65536]));
        assertThrows(NullPointerException.class, () -> new Tlv(0, null));
        assertEquals(65535, new Tlv(0xffff, new byte[65535]).valueLength());
        assertEquals(0xffff, new Tlv(0xffff, new byte[0]).tag());
        assertEquals(0, new Tlv(0, new byte[0]).valueLength());
    }

    @Test
    void ownsValueOctetsAndUsesBinaryEqualityWithoutLeakingPayload() {
        byte[] input = {0x73, 0x65, 0x63, 0x72, 0x65, 0x74};
        Tlv tlv = new Tlv(0x1400, input);
        input[0] = 0;
        byte[] output = tlv.value();
        output[1] = 0;
        Tlv equal = new Tlv(0x1400, new byte[] {0x73, 0x65, 0x63, 0x72, 0x65, 0x74});
        assertArrayEquals(equal.value(), tlv.value());
        assertEquals(equal, tlv);
        assertEquals(equal.hashCode(), tlv.hashCode());
        assertNotEquals(new Tlv(0x1401, equal.value()), tlv);
        assertNotEquals(new Tlv(0x1400, new byte[0]), tlv);
        assertFalse(tlv.toString().contains("secret"));
        assertEquals(6, tlv.valueLength());
    }
}
