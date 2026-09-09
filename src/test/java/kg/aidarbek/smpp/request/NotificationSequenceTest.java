package kg.aidarbek.smpp.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import kg.aidarbek.smpp.protocol.ControlCommand;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import org.junit.jupiter.api.Test;

class NotificationSequenceTest {
    private static final UUID GENERATION = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final RequestOptions OPTIONS = RequestOptions.timeout(Duration.ofSeconds(1));

    @Test
    void notificationsShareSequenceIdentityWithoutOccupyingAnyRequestCapacity() {
        try (RequestWindow window = new RequestWindow(GENERATION, 2, 32, 2, () -> 0)) {
            var first = window.admit(0x15, 0x80000015L, ControlCommand.class, 16, OPTIONS);
            assertEquals(1, first.identity().sequenceNumber());
            assertEquals(2, window.allocateNotificationSequence(0x102));
            var second = window.admit(0x15, 0x80000015L, ControlCommand.class, 16, OPTIONS);
            assertEquals(3, second.identity().sequenceNumber());
            assertEquals(4, window.allocateNotificationSequence(0x0b));
            assertEquals(2, window.pendingCount());
            assertEquals(32, window.pendingBytes());
            assertEquals(2, window.notificationsOutstanding());
            assertFalse(window.accept(
                    GENERATION,
                    new Pdu<>(
                            3,
                            2,
                            new ControlCommand(ControlCommand.Type.GENERIC_NACK, new OptionalParameters(List.of())))));
            assertFalse(first.isDone());
            assertFalse(second.isDone());
            assertTrue(window.pending(2).isEmpty());
        }
    }

    @Test
    void notificationAllocationRejectsPairedUnknownClosedAndExhaustedContexts() {
        try (BoundedNotifications notifications = new BoundedNotifications(1, 1);
                RequestWindow window = new RequestWindow(GENERATION, 1, 16, notifications, () -> 0, 0x7ffffffeL)) {
            for (long id : new long[] {0, 4, 0x15, 0x80000102L, 0x777, -1})
                assertThrows(IllegalArgumentException.class, () -> window.allocateNotificationSequence(id));
            assertEquals(0x7ffffffeL, window.allocateNotificationSequence(0x0b));
            assertEquals(
                    0x7fffffffL,
                    window.admit(0x15, 0x80000015L, ControlCommand.class, 16, OPTIONS)
                            .identity()
                            .sequenceNumber());
            assertEquals(
                    RequestFailure.Reason.SEQUENCE_EXHAUSTED,
                    assertThrows(RequestFailure.class, () -> window.allocateNotificationSequence(0x102))
                            .reason());
            assertEquals(1, notifications.outstandingCount());
        }
        RequestWindow window = new RequestWindow(GENERATION, 1, 16, 1, () -> 0);
        try {
            window.close();
            assertEquals(
                    RequestFailure.Reason.CLOSED,
                    assertThrows(RequestFailure.class, () -> window.allocateNotificationSequence(0x102))
                            .reason());
            assertEquals(0, window.pendingCount());
            assertEquals(0, window.notificationsOutstanding());
        } finally {
            window.close();
        }
    }
}
