package kg.aidarbek.smpp.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
import kg.aidarbek.smpp.protocol.ControlCommand;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RequestAdmissionContentionTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void notificationLockWaitingCannotConsumeOwnershipOrSequenceAfterTheDeadline(boolean capacityFull)
            throws Exception {
        AtomicLong clock = new AtomicLong();
        BoundedNotifications notifications = new BoundedNotifications(1, 1);
        BoundedNotifications.Reservation occupied =
                capacityFull ? notifications.tryReserve().orElseThrow() : null;
        RequestWindow window = new RequestWindow(UUID.randomUUID(), 1, 16, notifications, clock::get);
        var field = BoundedNotifications.class.getDeclaredField("lock");
        field.setAccessible(true);
        ReentrantLock lock = (ReentrantLock) field.get(notifications);
        CompletableFuture<RequestHandle<ControlCommand>> admission = new CompletableFuture<>();
        Thread caller = Thread.ofVirtual().unstarted(() -> {
            try {
                admission.complete(window.admit(
                        0x15, 0x80000015L, ControlCommand.class, 16, RequestOptions.timeout(Duration.ofNanos(10))));
            } catch (Throwable failure) {
                admission.completeExceptionally(failure);
            }
        });
        try {
            lock.lock();
            try {
                caller.start();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (!lock.hasQueuedThread(caller) && System.nanoTime() - deadline < 0)
                    LockSupport.parkNanos(100_000);
                assertTrue(lock.hasQueuedThread(caller), "admission did not reach notification-lock contention");
                clock.set(10);
            } finally {
                lock.unlock();
            }
            ExecutionException thrown =
                    assertThrows(ExecutionException.class, () -> admission.get(2, TimeUnit.SECONDS));
            RequestFailure failure = (RequestFailure) thrown.getCause();
            assertEquals(RequestFailure.Reason.DEADLINE_EXPIRED, failure.reason());
            assertEquals(TransmissionCertainty.NOT_SENT, failure.transmission());
            assertEquals(0, window.pendingCount());
            assertEquals(0, window.pendingBytes());
            assertEquals(capacityFull ? 1 : 0, notifications.outstandingCount());
            if (occupied != null) occupied.release();
            RequestHandle<ControlCommand> next = window.admit(
                    0x15, 0x80000015L, ControlCommand.class, 16, RequestOptions.timeout(Duration.ofNanos(10)));
            assertEquals(1, next.identity().sequenceNumber());
            next.cancel();
        } finally {
            if (occupied != null) occupied.release();
            window.close();
            notifications.close();
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
        }
    }
}
