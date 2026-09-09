package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import org.junit.jupiter.api.Test;

class RawContentTest {
    @Test
    void rawOctetsDeclareEightBitBinaryCoding() {
        assertEquals(4, new RawContent(160, 1).next(0).dataCoding());
    }

    @Test
    void generatesBoundedRepeatableDistinctContentAndChecksReceivedLength() {
        var content = new RawContent(160, 1);
        assertNotNull(content.next(42));
        assertEquals(160, content.next(42).payload().length());
        assertEquals(content.next(42), new RawContent(160, 1).next(42));
        assertNotEquals(content.next(42), content.next(43));
        assertNotEquals(content.next(42), new RawContent(160, 2).next(42));
        content.validate(content.next(42));
        assertThrows(IllegalArgumentException.class, () -> content.validate(new RawContent(159, 1).next(42)));
        assertEquals(0, new RawContent(0, 1).next(0).payload().length());
        assertEquals(65535, new RawContent(65535, 1).next(0).payload().length());
    }

    @Test
    void rejectsInvalidBodiesAndAllocationBounds() {
        assertThrows(IllegalArgumentException.class, () -> new RawContent(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new RawContent(65536, 1));
        var empty = new OctetString(new byte[0]);
        var tags = new OptionalParameters(List.of());
        assertThrows(IllegalArgumentException.class, () -> new TrafficContent(256, 0, empty, tags));
        assertThrows(NullPointerException.class, () -> new TrafficContent(0, 0, null, tags));
    }
}
