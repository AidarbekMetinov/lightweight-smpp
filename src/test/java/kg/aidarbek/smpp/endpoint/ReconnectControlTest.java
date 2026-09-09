package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import kg.aidarbek.smpp.session.SessionState;
import kg.aidarbek.smpp.spi.TransportFailure;
import org.junit.jupiter.api.Test;

class ReconnectControlTest {
    @Test
    void backoffAndFiniteBudgetUseTheGivenMonotonicClock() throws Exception {
        EndpointResources resources = new EndpointResources(EndpointOptions.defaults(), null);
        AtomicLong clock = new AtomicLong(1000);
        AtomicInteger calls = new AtomicInteger();
        RuntimeException refusal = new EndpointException(EndpointException.Reason.CAPACITY, new UUID(0, 0), -1, -1);
        ReconnectHandle handle = new ReconnectHandle(
                resources,
                new ReconnectPolicy(3, Duration.ofSeconds(2)),
                () -> {
                    calls.incrementAndGet();
                    return new ConnectionAttempt(refusal, CompletableFuture.completedFuture(null));
                },
                ignored -> {},
                clock::get);
        try {
            handle.tick(clock.get());
            assertEquals(1, calls.get());
            clock.addAndGet(TimeUnit.SECONDS.toNanos(1));
            handle.tick(clock.get());
            assertEquals(1, calls.get());
            clock.addAndGet(TimeUnit.SECONDS.toNanos(1));
            handle.tick(clock.get());
            assertEquals(2, calls.get());
            clock.addAndGet(TimeUnit.SECONDS.toNanos(2));
            handle.tick(clock.get());
            ReconnectResult result = handle.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(ReconnectResult.Reason.ATTEMPTS_EXHAUSTED, result.reason());
            assertEquals(3, result.attempts());
            assertSame(refusal, result.lastFailure().orElseThrow());
            handle.tick(clock.addAndGet(TimeUnit.DAYS.toNanos(1)));
            assertEquals(3, calls.get());
            assertFalse(handle.cancel());
        } finally {
            handle.close();
            finish(resources);
        }
    }

    @Test
    void cancellationBeforeFirstAttemptCannotBeUndoneByTicksOrObserverFutureMutation() throws Exception {
        EndpointResources resources = new EndpointResources(EndpointOptions.defaults(), null);
        AtomicInteger calls = new AtomicInteger();
        ReconnectHandle handle = new ReconnectHandle(
                resources,
                new ReconnectPolicy(2, Duration.ZERO),
                () -> {
                    calls.incrementAndGet();
                    throw new AssertionError("No attempt after cancellation");
                },
                ignored -> {});
        try {
            handle.termination()
                    .toCompletableFuture()
                    .complete(new ReconnectResult(
                            ReconnectResult.Reason.CLEANUP_FAILED, 0, 0, java.util.Optional.empty()));
            assertTrue(handle.cancel());
            handle.tick(System.nanoTime());
            assertEquals(0, calls.get());
            assertEquals(
                    ReconnectResult.Reason.CANCELLED,
                    handle.termination()
                            .toCompletableFuture()
                            .get(2, TimeUnit.SECONDS)
                            .reason());
        } finally {
            handle.close();
            finish(resources);
        }
    }

    @Test
    void failedPhysicalRetirementStopsReplacementAndPreservesTheCause() throws Exception {
        EndpointResources resources = new EndpointResources(EndpointOptions.defaults(), null);
        CompletableFuture<Void> retired = new CompletableFuture<>();
        AtomicLong clock = new AtomicLong(1000);
        AtomicInteger calls = new AtomicInteger();
        RuntimeException refusal = new EndpointException(EndpointException.Reason.CLOSED, new UUID(0, 0), -1, -1);
        ReconnectHandle handle = new ReconnectHandle(
                resources,
                new ReconnectPolicy(100, Duration.ZERO),
                () -> {
                    calls.incrementAndGet();
                    return new ConnectionAttempt(refusal, retired);
                },
                ignored -> {},
                clock::get);
        try {
            handle.tick(clock.get());
            handle.tick(clock.addAndGet(TimeUnit.SECONDS.toNanos(30)));
            assertEquals(1, calls.get(), "A failed construction still owns its physical retirement");
            RuntimeException cleanup = new TransportFailure(
                    TransportFailure.Kind.CLEANUP_FAILED,
                    false,
                    new java.io.IOException("fixture physical close failed"));
            retired.completeExceptionally(cleanup);
            ReconnectResult result = handle.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(ReconnectResult.Reason.CLEANUP_FAILED, result.reason());
            assertSame(cleanup, result.lastFailure().orElseThrow());
            handle.tick(clock.get());
            assertEquals(1, calls.get());
        } finally {
            retired.complete(null);
            handle.close();
            finish(resources);
        }
    }

    @Test
    void losingCancellationCannotAbortAnAlreadySelectedGracefulEndpointStop() throws Exception {
        EndpointResources resources = new EndpointResources(EndpointOptions.defaults(), null);
        try (KeepaliveTest.Connection source = new KeepaliveTest.Connection(null)) {
            ReconnectHandle handle = new ReconnectHandle(
                    resources,
                    new ReconnectPolicy(2, Duration.ZERO),
                    () -> new ConnectionAttempt(source.coordinator, source.coordinator.ioTermination()),
                    ignored -> {});
            try {
                handle.tick(System.nanoTime());
                handle.endpointClosing();
                assertFalse(handle.cancel());
                assertEquals(
                        SessionState.BOUND_TRX,
                        source.session.state(),
                        "The endpoint's graceful drain owns this closure");
                source.coordinator.close();
                assertEquals(
                        ReconnectResult.Reason.ENDPOINT_CLOSED,
                        handle.termination()
                                .toCompletableFuture()
                                .get(2, TimeUnit.SECONDS)
                                .reason());
            } finally {
                source.coordinator.close();
                handle.close();
            }
        } finally {
            finish(resources);
        }
    }

    @Test
    void factoryFailureConsumesTheFiniteAttemptWithoutEscapingTheEndpointTimer() throws Exception {
        EndpointResources resources = new EndpointResources(EndpointOptions.defaults(), null);
        RuntimeException failure = new IllegalStateException("fixture factory failed before ownership");
        ReconnectHandle handle = new ReconnectHandle(
                resources,
                new ReconnectPolicy(1, Duration.ZERO),
                () -> {
                    throw failure;
                },
                ignored -> {});
        try {
            assertDoesNotThrow(() -> handle.tick(System.nanoTime()));
            ReconnectResult result = handle.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(ReconnectResult.Reason.ATTEMPTS_EXHAUSTED, result.reason());
            assertSame(failure, result.lastFailure().orElseThrow());
        } finally {
            handle.close();
            finish(resources);
        }
    }

    private static void finish(EndpointResources resources) throws Exception {
        resources.close();
        assertTrue(resources
                .termination()
                .toCompletableFuture()
                .get(3, TimeUnit.SECONDS)
                .complete());
    }
}
