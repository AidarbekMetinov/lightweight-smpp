package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import org.junit.jupiter.api.Test;

class AuthenticationDispatcherTest {
    @Test
    void suppliedExecutorRunsOffSubmittingThreadAndRejectionIsAnAuthenticationFailure() throws Exception {
        AtomicInteger submitted = new AtomicInteger();
        Thread caller = Thread.currentThread();
        Executor inline = work -> {
            assertFalse(Thread.currentThread() == caller);
            submitted.incrementAndGet();
            work.run();
        };
        CompletableFuture<BindDecision> result = new CompletableFuture<>();
        AuthenticationDispatcher dispatcher = new AuthenticationDispatcher(1, 0, inline);
        try {
            dispatcher.submit(
                    (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                    request(),
                    new InetSocketAddress("127.0.0.1", 1),
                    (decision, error) -> result.complete(decision));
            assertEquals(BindDecision.ACCEPT, result.get(2, TimeUnit.SECONDS));
            assertEquals(1, submitted.get());
        } finally {
            dispatcher.close();
            assertTrue(dispatcher.awaitTermination(Duration.ofSeconds(2)));
        }
        CompletableFuture<Throwable> failure = new CompletableFuture<>();
        AuthenticationDispatcher rejected = new AuthenticationDispatcher(1, 0, work -> {
            throw new RejectedExecutionException("test rejection");
        });
        try {
            rejected.submit(
                    (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                    request(),
                    new InetSocketAddress("127.0.0.1", 1),
                    (decision, error) -> failure.complete(error));
            assertTrue(failure.get(2, TimeUnit.SECONDS) instanceof RejectedExecutionException);
        } finally {
            rejected.close();
            assertTrue(rejected.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    void cancelledPendingAuthenticationRetainsPhysicalCapacityAndQueuedWorkIsBounded() throws Exception {
        CompletableFuture<BindDecision> blocked = new CompletableFuture<>();
        CountDownLatch invoked = new CountDownLatch(1);
        AtomicInteger delivered = new AtomicInteger();
        AuthenticationDispatcher dispatcher = new AuthenticationDispatcher(1, 1, null);
        try {
            AuthenticationDispatcher.Ticket active = dispatcher.submit(
                    (request, peer) -> {
                        invoked.countDown();
                        return blocked;
                    },
                    request(),
                    new InetSocketAddress("127.0.0.1", 1),
                    (result, error) -> delivered.incrementAndGet());
            assertTrue(invoked.await(2, TimeUnit.SECONDS));
            active.cancel();
            AuthenticationDispatcher.Ticket queued = dispatcher.submit(
                    (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                    request(),
                    new InetSocketAddress("127.0.0.1", 1),
                    (result, error) -> delivered.incrementAndGet());
            assertEquals(2, dispatcher.outstanding());
            assertThrows(
                    RejectedExecutionException.class,
                    () -> dispatcher.submit(
                            (request, peer) -> blocked,
                            request(),
                            new InetSocketAddress("127.0.0.1", 1),
                            (result, error) -> delivered.incrementAndGet()));
            queued.cancel();
            assertEquals(1, dispatcher.outstanding());
            dispatcher.close();
            assertFalse(dispatcher.awaitTermination(Duration.ofMillis(20)));
            blocked.complete(BindDecision.ACCEPT);
            assertTrue(dispatcher.awaitTermination(Duration.ofSeconds(2)));
            assertEquals(0, dispatcher.outstanding());
            assertEquals(0, delivered.get());
        } finally {
            blocked.complete(BindDecision.ACCEPT);
            dispatcher.close();
        }
    }

    private static BindRequest request() {
        return new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x34, 0, 0, "");
    }
}
