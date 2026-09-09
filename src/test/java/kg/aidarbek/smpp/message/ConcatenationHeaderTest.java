package kg.aidarbek.smpp.message;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HexFormat;
import kg.aidarbek.smpp.protocol.OctetString;
import org.junit.jupiter.api.Test;

class ConcatenationHeaderTest {
    @Test
    void canonicalUdhUsesEtsiInformationElementsAndNeverSilentlyPacksPayload() {
        MessageSegment eight = new MessageSegment(0xab, 3, 2, bytes("004103a9"));
        MessageSegment sixteen = new MessageSegment(0x1234, 3, 2, bytes("004103a9"));
        assertArrayEquals(
                bytes("050003ab0302004103a9").value(),
                ConcatenationHeader.prepend8(eight).value());
        assertArrayEquals(
                bytes("06080412340302004103a9").value(),
                ConcatenationHeader.prepend16(sixteen).value());
        assertEquals(eight, ConcatenationHeader.read(bytes("050003ab0302004103a9")));
        assertEquals(sixteen, ConcatenationHeader.read(bytes("06080412340302004103a9")));
        assertThrows(IllegalArgumentException.class, () -> ConcatenationHeader.prepend8(sixteen));
        for (String bad : new String[] {
            "",
            "050003ab0302".substring(0, 10),
            "060804123403",
            "050003ab0001",
            "050003ab0203",
            "050103ab0302",
            "070003ab03020000",
            "06080512340302"
        }) assertThrows(IllegalArgumentException.class, () -> ConcatenationHeader.read(bytes(bad)));
    }

    private static OctetString bytes(String hex) {
        return new OctetString(HexFormat.of().parseHex(hex));
    }
}
