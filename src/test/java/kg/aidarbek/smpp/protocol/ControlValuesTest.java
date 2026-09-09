package kg.aidarbek.smpp.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ControlValuesTest {
    private static final OptionalParameters EMPTY = new OptionalParameters(List.of());

    @Test
    void bindResponsesValidateSystemIdAndKeepAbsentBodyDistinctFromEmptyField() {
        assertThrows(NullPointerException.class, () -> new BindResponse(null, Optional.empty(), EMPTY));
        assertThrows(NullPointerException.class, () -> new BindResponse(BindMode.RECEIVER, null, EMPTY));
        assertThrows(NullPointerException.class, () -> new BindResponse(BindMode.RECEIVER, Optional.empty(), null));
        for (String invalid : List.of("x".repeat(16), "a\0b", "å")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new BindResponse(BindMode.RECEIVER, Optional.of(invalid), EMPTY));
        }
        BindResponse absent = new BindResponse(BindMode.RECEIVER, Optional.empty(), EMPTY);
        BindResponse empty = new BindResponse(BindMode.RECEIVER, Optional.of(""), EMPTY);
        assertNotEquals(absent, empty);
        assertEquals(absent, new BindResponse(BindMode.RECEIVER, Optional.empty(), EMPTY));
        assertEquals(absent.hashCode(), new BindResponse(BindMode.RECEIVER, Optional.empty(), EMPTY).hashCode());
        BindResponse maximum = new BindResponse(BindMode.TRANSCEIVER, Optional.of("x".repeat(15)), EMPTY);
        assertEquals(15, maximum.systemId().orElseThrow().length());
        assertFalse(new BindResponse(BindMode.RECEIVER, Optional.of("private-id"), EMPTY)
                .toString()
                .contains("private-id"));
    }

    @Test
    void controlValuesRejectMissingDataAndKeepImmutableValueEquality() {
        assertThrows(NullPointerException.class, () -> new ControlCommand(null, EMPTY));
        assertThrows(NullPointerException.class, () -> new ControlCommand(ControlCommand.Type.UNBIND, null));
        ControlCommand command = new ControlCommand(ControlCommand.Type.UNBIND, EMPTY);
        assertEquals(command, new ControlCommand(ControlCommand.Type.UNBIND, EMPTY));
        assertEquals(command.hashCode(), new ControlCommand(ControlCommand.Type.UNBIND, EMPTY).hashCode());
        assertNotEquals(command, new ControlCommand(ControlCommand.Type.UNBIND_RESPONSE, EMPTY));
    }
}
