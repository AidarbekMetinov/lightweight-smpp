package kg.aidarbek.smpp.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class PduHeaderTest {
    @ParameterizedTest
    @ValueSource(longs = {-1, 0x100000000L})
    void rejectsValuesOutsideUnsignedFourOctets(long invalid) {
        assertThrows(IllegalArgumentException.class, () -> new PduHeader(invalid, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PduHeader(16, invalid, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PduHeader(16, 0, invalid, 0));
        assertThrows(IllegalArgumentException.class, () -> new PduHeader(16, 0, 0, invalid));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 15})
    void rejectsLengthsSmallerThanTheHeader(long length) {
        assertThrows(IllegalArgumentException.class, () -> new PduHeader(length, 0, 0, 0));
    }

    @Test
    void preservesUnknownValuesAndSequenceZeroWithoutSessionValidation() {
        PduHeader header = new PduHeader(0xffffffffL, 0xffffffffL, 0xffffffffL, 0);
        assertEquals(0xffffffffL, header.commandLength());
        assertEquals(0xffffffffL, header.commandId());
        assertEquals(0xffffffffL, header.commandStatus());
        assertEquals(0, header.sequenceNumber());
    }
}
