package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HandlerDispatcherTest {
    @Test
    void sameSessionInvocationsStartInReceiveOrderWhileAsynchronousDecisionsOverlap() throws Exception {
        HandlerDispatcher dispatcher = new HandlerDispatcher(3, 3);
        CompletableFuture<Void> releaseInvocation = new CompletableFuture<>();
        CompletableFuture<Integer> firstDecision = new CompletableFuture<>();
        CompletableFuture<Integer> secondDecision = new CompletableFuture<>();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch otherStarted = new CountDownLatch(1);
        try {
            var lane = dispatcher.lane();
            dispatcher.submit(
                    lane,
                    () -> {
                        firstStarted.countDown();
                        releaseInvocation.join();
                        return firstDecision;
                    },
                    new AtomicBoolean(),
                    (value, error) -> {});
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            dispatcher.submit(
                    lane,
                    () -> {
                        secondStarted.countDown();
                        return secondDecision;
                    },
                    new AtomicBoolean(),
                    (value, error) -> {});
            dispatcher.submit(
                    dispatcher.lane(),
                    () -> {
                        otherStarted.countDown();
                        return CompletableFuture.completedFuture(3);
                    },
                    new AtomicBoolean(),
                    (value, error) -> {});
            assertTrue(otherStarted.await(2, TimeUnit.SECONDS), "Another session must retain independent progress");
            assertFalse(
                    secondStarted.await(50, TimeUnit.MILLISECONDS),
                    "A blocked invocation must preserve receive-order starts");
            releaseInvocation.complete(null);
            assertTrue(
                    secondStarted.await(2, TimeUnit.SECONDS),
                    "Incomplete first decision must still permit asynchronous overlap");
            assertFalse(firstDecision.isDone());
        } finally {
            releaseInvocation.complete(null);
            firstDecision.complete(1);
            secondDecision.complete(2);
            dispatcher.close();
            assertTrue(dispatcher.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    void cancellationRetainsActiveStageCapacityAndRemovesOnlyUnstartedWork() throws Exception {
        HandlerDispatcher dispatcher = new HandlerDispatcher(1, 1);
        CompletableFuture<Integer> pending = new CompletableFuture<>();
        CountDownLatch invoked = new CountDownLatch(1);
        AtomicInteger callbacks = new AtomicInteger();
        try {
            var lane = dispatcher.lane();
            var active = dispatcher.submit(
                    lane,
                    () -> {
                        invoked.countDown();
                        return pending;
                    },
                    new AtomicBoolean(),
                    (value, error) -> callbacks.incrementAndGet());
            assertTrue(invoked.await(2, TimeUnit.SECONDS), "Accepted handler must begin on bounded owned execution");
            var queued = dispatcher.submit(
                    lane,
                    () -> CompletableFuture.completedFuture(2),
                    new AtomicBoolean(),
                    (value, error) -> callbacks.incrementAndGet());
            assertThrows(
                    RejectedExecutionException.class,
                    () -> dispatcher.submit(
                            lane,
                            () -> CompletableFuture.completedFuture(3),
                            new AtomicBoolean(),
                            (value, error) -> {}));
            active.cancel();
            assertEquals(2, dispatcher.outstanding());
            queued.cancel();
            assertEquals(1, dispatcher.outstanding());
            dispatcher.close();
            assertFalse(dispatcher.awaitTermination(Duration.ofMillis(20)));
            pending.complete(1);
            assertTrue(dispatcher.awaitTermination(Duration.ofSeconds(2)));
            assertEquals(0, dispatcher.outstanding());
            assertEquals(0, callbacks.get());
        } finally {
            pending.complete(1);
            dispatcher.close();
        }
    }
}
