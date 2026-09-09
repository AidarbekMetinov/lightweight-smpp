package kg.aidarbek.smpp.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class BoundedNotificationsTest {
    @Test
    void closeDrainsReservedWorkAndReportsCallbackFailureWithoutDroppingFollowingNotifications() throws Exception {
        BoundedNotifications notifications = new BoundedNotifications(2, 1);
        try {
            var first = notifications.tryReserve().orElseThrow();
            var second = notifications.tryReserve().orElseThrow();
            notifications.close();
            assertTrue(notifications.tryReserve().isEmpty());
            assertFalse(notifications.awaitTermination(Duration.ZERO));
            RuntimeException failure = new RuntimeException("controlled callback failure");
            assertThrows(NullPointerException.class, () -> first.dispatch(null));
            first.dispatch(() -> {
                throw failure;
            });
            CompletableFuture<Boolean> following = new CompletableFuture<>();
            second.dispatch(() -> following.complete(true));
            assertThrows(IllegalStateException.class, () -> second.dispatch(() -> {}));
            assertFalse(first.release());
            assertTrue(following.get(2, TimeUnit.SECONDS));
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
            assertSame(failure, notifications.lastFailure().orElseThrow());
            assertEquals(0, notifications.outstandingCount());
        } finally {
            notifications.close();
        }
    }

    @Test
    void validatesBoundsAndWaitDuration() {
        assertThrows(IllegalArgumentException.class, () -> new BoundedNotifications(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new BoundedNotifications(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new BoundedNotifications(1, 2));
        try (BoundedNotifications notifications = new BoundedNotifications(1, 1)) {
            assertThrows(IllegalArgumentException.class, () -> notifications.awaitTermination(Duration.ofNanos(-1)));
            assertThrows(NullPointerException.class, () -> notifications.tryDispatch(null));
        }
    }

    @Test
    void invokesNotificationsOnOwnedWorkersOutsideTheSubmittingThread() throws Exception {
        try (BoundedNotifications notifications = new BoundedNotifications(1, 1)) {
            CompletableFuture<Thread> called = new CompletableFuture<>();
            notifications.tryReserve().orElseThrow().dispatch(() -> called.complete(Thread.currentThread()));
            assertNotSame(Thread.currentThread(), called.get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void reservesCapacityBeforeNotificationExistsAndReleasesOnlyOnce() {
        try (BoundedNotifications notifications = new BoundedNotifications(1, 1)) {
            BoundedNotifications.Reservation reserved =
                    notifications.tryReserve().orElseThrow();
            assertTrue(notifications.tryReserve().isEmpty());
            assertEquals(1, notifications.outstandingCount());
            assertTrue(reserved.release());
            assertFalse(reserved.release());
            assertEquals(0, notifications.outstandingCount());
            assertTrue(notifications.tryReserve().orElseThrow().release());
        }
    }
}
