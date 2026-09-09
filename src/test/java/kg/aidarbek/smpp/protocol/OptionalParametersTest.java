package kg.aidarbek.smpp.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OptionalParametersTest {
    @Test
    void retainsRepeatedUnknownTagsInAnImmutableOrderedValue() {
        Tlv first = new Tlv(0x1400, new byte[] {1});
        Tlv second = new Tlv(0x1400, new byte[] {2});
        List<Tlv> source = new ArrayList<>(List.of(first, second));
        OptionalParameters parameters = new OptionalParameters(source);
        source.clear();
        assertEquals(List.of(first, second), parameters.entries());
        assertThrows(
                UnsupportedOperationException.class, () -> parameters.entries().clear());
        OptionalParameters equal = new OptionalParameters(List.of(first, second));
        assertEquals(equal, parameters);
        assertEquals(equal.hashCode(), parameters.hashCode());
        assertNotEquals(new OptionalParameters(List.of(second, first)), parameters);
        assertThrows(NullPointerException.class, () -> new OptionalParameters(null));
        assertThrows(NullPointerException.class, () -> new OptionalParameters(java.util.Arrays.asList(first, null)));
    }
}
