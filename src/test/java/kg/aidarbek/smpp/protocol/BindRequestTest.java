package kg.aidarbek.smpp.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class BindRequestTest {
    @Test
    void enforcesAsciiFieldBoundsAndOctetWidthsWithoutExposingCredentials() {
        assertThrows(IllegalArgumentException.class, () -> request("a".repeat(16), "", "", 0, 0, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> request("", "p".repeat(9), "", 0, 0, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> request("", "", "t".repeat(13), 0, 0, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> request("", "", "", 0, 0, 0, "r".repeat(41)));
        assertThrows(IllegalArgumentException.class, () -> request("a\0b", "", "", 0, 0, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> request("", "é", "", 0, 0, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> request("", "", "", -1, 0, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> request("", "", "", 256, 0, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> request("", "", "", 0, -1, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> request("", "", "", 0, 0, 256, ""));
        assertThrows(NullPointerException.class, () -> request(null, "", "", 0, 0, 0, ""));
        assertThrows(NullPointerException.class, () -> new BindRequest(null, "", "", "", 0, 0, 0, ""));
        BindRequest value = request("private-id", "secret", "private-type", 255, 255, 255, "private-range");
        assertEquals(255, value.interfaceVersion());
        for (String sensitive : new String[] {"private-id", "secret", "private-type", "private-range"}) {
            assertFalse(value.toString().contains(sensitive));
        }
    }

    private static BindRequest request(
            String systemId, String password, String systemType, int version, int ton, int npi, String range) {
        return new BindRequest(BindMode.TRANSCEIVER, systemId, password, systemType, version, ton, npi, range);
    }
}
