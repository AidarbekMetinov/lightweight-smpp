package kg.aidarbek.smpp.message;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HexFormat;
import kg.aidarbek.smpp.protocol.OctetString;
import org.junit.jupiter.api.Test;

class TextEncodingTest {
    @Test
    void asciiPositionsEmptyContentAndTheCompleteSupportedAlphabetAreStable() {
        assertArrayEquals(
                HexFormat.of().parseHex("415a617a303920213f231124"),
                TextEncoding.GSM7_UNPACKED.encode("AZaz09 !?#_¤").value());
        for (TextEncoding encoding : TextEncoding.values()) {
            assertEquals(0, encoding.encodedLength(""));
            assertEquals("", encoding.decode(encoding.encode("")));
            assertThrows(NullPointerException.class, () -> encoding.encode(null));
            assertThrows(NullPointerException.class, () -> encoding.decode(null));
        }
        for (int value = 0; value < 128; value++) {
            if (value == 27) continue;
            OctetString septet = new OctetString(new byte[] {(byte) value});
            assertEquals(septet, TextEncoding.GSM7_UNPACKED.encode(TextEncoding.GSM7_UNPACKED.decode(septet)));
        }
    }

    @Test
    void gsmDefaultAndExtensionAlphabetHaveIndependentSeptetFixtures() {
        String text = "@£$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ¡ÄÖÑÜ§¿äöñüà\f^{}\\[~]|€";
        byte[] expected = HexFormat.of()
                .parseHex(
                        "000102030405060708090a0b0c0d0e0f101112131415161718191a1c1d1e1f405b5c5d5e5f607b7c7d7e7f1b0a1b141b281b291b2f1b3c1b3d1b3e1b401b65");
        assertArrayEquals(expected, TextEncoding.GSM7_UNPACKED.encode(text).value());
        assertEquals(expected.length, TextEncoding.GSM7_UNPACKED.encodedLength(text));
    }

    @Test
    void decodingIsStrictAndUcs2UsesBigEndianBmpWithoutSurrogateReplacement() {
        assertEquals(
                "Hello €!",
                TextEncoding.GSM7_UNPACKED.decode(new OctetString(HexFormat.of().parseHex("48656c6c6f201b6521"))));
        assertArrayEquals(
                HexFormat.of().parseHex("004103a94e2d0000feff"),
                TextEncoding.UCS2.encode("AΩ中\0\ufeff").value());
        assertEquals(
                "AΩ中\0\ufeff",
                TextEncoding.UCS2.decode(new OctetString(HexFormat.of().parseHex("004103a94e2d0000feff"))));
        assertEquals(6, TextEncoding.UCS2.encodedLength("AΩ中"));
        for (String value : new String[] {"😀", "\ud800", "\udc00"}) {
            assertThrows(IllegalArgumentException.class, () -> TextEncoding.UCS2.encode(value));
            assertThrows(IllegalArgumentException.class, () -> TextEncoding.UCS2.encodedLength(value));
        }
        for (String value : new String[] {"中", "`", "\u001b", "😀"})
            assertThrows(IllegalArgumentException.class, () -> TextEncoding.GSM7_UNPACKED.encode(value));
        for (String hex : new String[] {"1b", "1b00", "80", "1b1b"})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TextEncoding.GSM7_UNPACKED.decode(
                            new OctetString(HexFormat.of().parseHex(hex))));
        for (String hex : new String[] {"00", "d800", "dc00", "d83dde00"})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TextEncoding.UCS2.decode(
                            new OctetString(HexFormat.of().parseHex(hex))));
    }
}
