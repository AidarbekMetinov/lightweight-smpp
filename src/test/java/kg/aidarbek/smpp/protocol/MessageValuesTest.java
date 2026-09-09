package kg.aidarbek.smpp.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MessageValuesTest {
    @Test
    void messageValuesBoundEveryAsciiAndOctetRepresentationWithoutDisclosingContents() {
        OptionalParameters empty = new OptionalParameters(List.of());
        Address address = new Address(0, 0, "");
        assertThrows(IllegalArgumentException.class, () -> new DataSm("123456", address, address, 0, 0, 0, empty));
        assertThrows(IllegalArgumentException.class, () -> new DataSm("", address, address, 256, 0, 0, empty));
        assertThrows(IllegalArgumentException.class, () -> new DataSm("", address, address, 0, -1, 0, empty));
        assertThrows(IllegalArgumentException.class, () -> new DataSm("", address, address, 0, 0, 256, empty));
        assertThrows(NullPointerException.class, () -> new DataSm("", null, address, 0, 0, 0, empty));
        assertThrows(IllegalArgumentException.class, () -> new MessageResponse(Optional.of("x".repeat(65)), empty));
        assertThrows(IllegalArgumentException.class, () -> new MessageResponse(Optional.of("a\u0000b"), empty));
        assertThrows(IllegalArgumentException.class, () -> new MessageResponse(Optional.of("é"), empty));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MessageResponse(
                        Optional.empty(), new OptionalParameters(List.of(new Tlv(0x1400, new byte[0])))));
        assertFalse(new MessageResponse(Optional.of("private-message-id"), empty)
                .toString()
                .contains("private-message-id"));
        for (int position = 0; position < 7; position++) {
            int[] octets = new int[7];
            octets[position] = 256;
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ShortMessage(
                            "",
                            address,
                            address,
                            octets[0],
                            octets[1],
                            octets[2],
                            "",
                            "",
                            octets[3],
                            octets[4],
                            octets[5],
                            octets[6],
                            new OctetString(new byte[0]),
                            empty));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new ShortMessage(
                        "",
                        address,
                        address,
                        0,
                        0,
                        0,
                        "1".repeat(17),
                        "",
                        0,
                        0,
                        0,
                        0,
                        new OctetString(new byte[0]),
                        empty));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ShortMessage(
                        "", address, address, 0, 0, 0, "", "", 0, 0, 0, 0, new OctetString(new byte[256]), empty));
    }

    @Test
    void addressesRequireUnsignedOctetsAndBoundedNulFreeAscii() {
        assertEquals(64, new Address(255, 255, "a".repeat(64)).value().length());
        assertEquals("", new Address(0, 0, "").value());
        assertThrows(IllegalArgumentException.class, () -> new Address(-1, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> new Address(0, 256, ""));
        assertThrows(IllegalArgumentException.class, () -> new Address(0, 0, "a".repeat(65)));
        assertThrows(IllegalArgumentException.class, () -> new Address(0, 0, "a\u0000b"));
        assertThrows(IllegalArgumentException.class, () -> new Address(0, 0, "é"));
    }

    @Test
    void octetsOwnInputAndOutputAndCompareByContent() {
        byte[] input = {0, (byte) 0xff, 65};
        OctetString octets = new OctetString(input);
        input[0] = 5;
        assertArrayEquals(new byte[] {0, (byte) 0xff, 65}, octets.value());
        octets.value()[1] = 0;
        assertArrayEquals(new byte[] {0, (byte) 0xff, 65}, octets.value());
        assertEquals(3, octets.length());
        assertEquals(new OctetString(new byte[] {0, (byte) 0xff, 65}), octets);
        assertEquals(new OctetString(new byte[] {0, (byte) 0xff, 65}).hashCode(), octets.hashCode());
        assertNotEquals(octets, new OctetString(new byte[] {0, (byte) 0xff}));
        assertFalse(octets.toString().contains("65"));
        assertThrows(NullPointerException.class, () -> new OctetString(null));
    }
}
