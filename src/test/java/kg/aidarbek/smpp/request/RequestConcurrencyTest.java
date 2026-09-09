package kg.aidarbek.smpp.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import kg.aidarbek.smpp.protocol.ControlCommand;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import org.junit.jupiter.api.Test;

final class RequestConcurrencyTest {
    private static final RequestOptions OPTIONS = RequestOptions.timeout(Duration.ofNanos(100));

    @Test
    void callerCannotCompleteCancelOrObtrudeTheInternalResultThroughDerivedFutures() throws Exception {
        try (RequestWindow window = new RequestWindow(UUID.randomUUID(), 1, 16, 1, () -> 0)) {
            var handle = admit(window);
            var callerCopy = handle.result().toCompletableFuture();
            assertTrue(callerCopy.complete(response(handle, 99)));
            var cancelledCopy = handle.result().toCompletableFuture();
            assertTrue(cancelledCopy.cancel(true));
            cancelledCopy.obtrudeException(new IllegalStateException("caller mutation"));
            assertNotSame(callerCopy, handle.result().toCompletableFuture());
            assertFalse(handle.isDone());
            assertFalse(handle.result().toCompletableFuture().isDone());
            var real = response(handle, 0);
            assertTrue(window.accept(window.generation(), real));
            assertEquals(real, handle.result().toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertEquals(99, callerCopy.get().commandStatus());
        }
    }

    @Test
    void blockedDependentsRetainBoundedGlobalNotificationCapacityWhileProgressAndCloseRemainFree() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        BoundedNotifications notifications = new BoundedNotifications(2, 1);
        RequestWindow first = new RequestWindow(UUID.randomUUID(), 1, 16, notifications, () -> 0);
        RequestWindow second = new RequestWindow(UUID.randomUUID(), 1, 16, notifications, () -> 0);
        try {
            var a = admit(first);
            a.result().thenRun(() -> {
                entered.countDown();
                await(release);
            });
            try {
                assertTrue(first.accept(first.generation(), response(a, 0)));
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                var b = admit(second);
                assertTrue(second.beginWrite(b));
                assertTrue(b.cancel());
                assertTrue(a.isDone());
                assertTrue(b.isDone());
                assertEquals(0, first.pendingCount());
                assertEquals(0, second.pendingBytes());
                assertEquals(2, notifications.outstandingCount());
                assertEquals(
                        RequestFailure.Reason.NOTIFICATION_BACKLOG,
                        assertThrows(RequestFailure.class, () -> admit(first)).reason());
                assertEquals(0, first.expire());
                assertEquals(0, second.disconnect(null));
                first.close();
                notifications.close();
                assertFalse(notifications.awaitTermination(Duration.ZERO));
                assertFalse(b.result().toCompletableFuture().isDone());
                assertEquals(2, notifications.outstandingCount());
            } finally {
                release.countDown();
            }
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
            assertEquals(0, notifications.outstandingCount());
            assertTrue(notifications.isTerminated());
        } finally {
            release.countDown();
            first.close();
            second.close();
            notifications.close();
        }
    }

    @Test
    void explicitlyCoordinatedTerminalRacesPublishOneOutcomeAndReleaseEachSlotOnce() throws Exception {
        AtomicLong clock = new AtomicLong();
        try (RequestWindow window = new RequestWindow(UUID.randomUUID(), 1, 16, 1, clock::get)) {
            var handle = admit(window);
            assertTrue(window.beginWrite(handle));
            CountDownLatch ready = new CountDownLatch(5);
            CountDownLatch start = new CountDownLatch(1);
            var response = race(ready, start, () -> window.accept(window.generation(), response(handle, 0)));
            var cancellation = race(ready, start, handle::cancel);
            var failure = race(ready, start, () -> window.failWrite(handle, new IllegalStateException("write")));
            var disconnect = race(ready, start, () -> window.disconnect(null) == 1);
            var deadline = race(ready, start, () -> {
                clock.set(100);
                return window.expire() == 1;
            });
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            int reportedWinners = 0;
            for (var result : List.of(response, cancellation, failure, disconnect, deadline)) {
                if (result.get(2, TimeUnit.SECONDS)) {
                    reportedWinners++;
                }
            }
            // A due deadline may be settled by response/cancel/failWrite, whose boolean then correctly returns false.
            assertTrue(reportedWinners <= 1);
            var outcome = handle.terminalOutcome().orElseThrow();
            assertTrue(outcome.response().isPresent() ^ outcome.failure().isPresent());
            assertEquals(0, window.pendingCount());
            assertEquals(0, window.pendingBytes());
            assertFalse(handle.cancel());
            assertFalse(window.beginWrite(handle));
            assertEquals(0, window.expire());
            assertEquals(0, window.disconnect(null));
            assertTrue(handle.result()
                    .handle((result, error) -> true)
                    .toCompletableFuture()
                    .get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void writeGuardAndCancellationRaceNeverAuthorizeWritingAfterNotSentWins() throws Exception {
        try (RequestWindow window = new RequestWindow(UUID.randomUUID(), 1, 16, 1, () -> 0)) {
            var handle = admit(window);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            var writing = race(ready, start, () -> window.beginWrite(handle));
            var cancellation = race(ready, start, handle::cancel);
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            boolean wrote = writing.get(2, TimeUnit.SECONDS);
            assertTrue(cancellation.get(2, TimeUnit.SECONDS));
            var failure = (RequestFailure)
                    handle.terminalOutcome().orElseThrow().failure().orElseThrow();
            assertEquals(
                    wrote ? TransmissionCertainty.MAY_HAVE_BEEN_SENT : TransmissionCertainty.NOT_SENT,
                    failure.transmission());
            assertFalse(window.beginWrite(handle));
        }
    }

    private static CompletableFuture<Boolean> race(
            CountDownLatch ready, CountDownLatch start, java.util.function.BooleanSupplier action) {
        var result = new CompletableFuture<Boolean>();
        Thread.ofVirtual().start(() -> {
            ready.countDown();
            try {
                await(start);
                result.complete(action.getAsBoolean());
            } catch (RuntimeException | Error failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Coordination timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Coordination interrupted", exception);
        }
    }

    private static RequestHandle<ControlCommand> admit(RequestWindow window) {
        return window.admit(0x15, 0x80000015L, ControlCommand.class, 16, OPTIONS);
    }

    private static Pdu<ControlCommand> response(RequestHandle<?> handle, long status) {
        return new Pdu<>(
                status,
                handle.identity().sequenceNumber(),
                new ControlCommand(ControlCommand.Type.ENQUIRE_LINK_RESPONSE, new OptionalParameters(List.of())));
    }
}
