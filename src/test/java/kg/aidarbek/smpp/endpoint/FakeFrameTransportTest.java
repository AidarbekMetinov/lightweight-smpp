package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import kg.aidarbek.smpp.spi.FrameListener;
import kg.aidarbek.smpp.spi.TransportFailure;
import kg.aidarbek.smpp.spi.WriteClass;
import kg.aidarbek.smpp.spi.WriteObserver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The controlled port preserves accepted-work and cleanup contracts under faulty internal observers too. */
class FakeFrameTransportTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void transportAbortPreservesItsReasonForEveryQueuedWrite(boolean listenerFails) throws Exception {
        FakeFrameTransport transport = new FakeFrameTransport();
        RuntimeException callbackFailure = new IllegalStateException("fixture inbound callback failure");
        CompletableFuture<TransportFailure> outcome = new CompletableFuture<>();
        transport.start(new FrameListener() {
            @Override
            public void connected() {}

            @Override
            public void frame(byte[] frame) {
                throw callbackFailure;
            }

            @Override
            public void closed(TransportFailure reason) {}
        });
        transport.deferWrites = true;
        try {
            transport.write(
                    RawPeer.header(0x15, 0, 1),
                    WriteClass.ORDINARY,
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(2),
                    new WriteObserver() {
                        @Override
                        public boolean beforeWrite() {
                            throw new AssertionError("Closed queued work must not claim its guard");
                        }

                        @Override
                        public void written() {
                            outcome.complete(null);
                        }

                        @Override
                        public void failed(TransportFailure reason) {
                            outcome.complete(reason);
                        }
                    });
            if (listenerFails) transport.receive(RawPeer.header(0x15, 0, 2));
            else transport.close();
            TransportFailure failure = outcome.get(2, TimeUnit.SECONDS);
            assertEquals(
                    listenerFails ? TransportFailure.Kind.OBSERVER_FAILED : TransportFailure.Kind.CLOSED,
                    failure.kind());
            assertEquals(listenerFails ? callbackFailure : null, failure.getCause());
            assertEquals(false, failure.writeStarted());
            assertTrue(transport.writes.isEmpty());
            transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
        } finally {
            transport.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"connected", "frame"})
    void failedListenerProgressAbortsThePortWithoutEscapingToItsCaller(String event) throws Exception {
        FakeFrameTransport transport = new FakeFrameTransport();
        RuntimeException failure = new IllegalStateException("fixture listener progress failure");
        CompletableFuture<TransportFailure> closed = new CompletableFuture<>();
        FrameListener listener = new FrameListener() {
            @Override
            public void connected() {
                if (event.equals("connected")) throw failure;
            }

            @Override
            public void frame(byte[] frame) {
                throw failure;
            }

            @Override
            public void closed(TransportFailure cause) {
                closed.complete(cause);
            }
        };
        try {
            assertDoesNotThrow(() -> transport.start(listener));
            if (event.equals("frame")) assertDoesNotThrow(() -> transport.receive(RawPeer.header(0x15, 0, 1)));
            assertEquals(
                    TransportFailure.Kind.OBSERVER_FAILED,
                    closed.get(2, TimeUnit.SECONDS).kind());
            assertEquals(failure, closed.get().getCause());
            transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
        } finally {
            transport.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"connected", "frame"})
    void terminationWaitsForEveryAlreadyInvokingListenerCallback(String event) throws Exception {
        FakeFrameTransport transport = new FakeFrameTransport();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FrameListener listener = new FrameListener() {
            private void hold() {
                entered.countDown();
                try {
                    if (!release.await(3, TimeUnit.SECONDS)) throw new AssertionError("Callback gate expired");
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(failure);
                }
            }

            @Override
            public void connected() {
                if (event.equals("connected")) hold();
            }

            @Override
            public void frame(byte[] frame) {
                hold();
            }

            @Override
            public void closed(TransportFailure failure) {}
        };
        if (event.equals("frame")) transport.start(listener);
        Thread caller = Thread.ofVirtual().start(() -> {
            if (event.equals("connected")) transport.start(listener);
            else transport.receive(RawPeer.header(0x15, 0, 1));
        });
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            transport.close();
            assertThrows(
                    TimeoutException.class,
                    () -> transport.termination().toCompletableFuture().get(100, TimeUnit.MILLISECONDS));
            release.countDown();
            caller.join(2000);
            transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            caller.join(3000);
            transport.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void closeOrDeadlineDuringAClaimedGuardPreventsEveryFrame(boolean deadlineWins) throws Exception {
        FakeFrameTransport transport = new FakeFrameTransport();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<TransportFailure> outcome = new CompletableFuture<>();
        transport.start(new FrameListener() {
            @Override
            public void connected() {}

            @Override
            public void frame(byte[] frame) {}

            @Override
            public void closed(TransportFailure failure) {}
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        Thread writer = Thread.ofVirtual()
                .start(() ->
                        transport.write(RawPeer.header(0x15, 0, 1), WriteClass.ORDINARY, deadline, new WriteObserver() {
                            @Override
                            public boolean beforeWrite() {
                                entered.countDown();
                                try {
                                    if (!release.await(3, TimeUnit.SECONDS))
                                        throw new AssertionError("Guard gate expired");
                                } catch (InterruptedException failure) {
                                    Thread.currentThread().interrupt();
                                    throw new AssertionError(failure);
                                }
                                return true;
                            }

                            @Override
                            public void written() {
                                outcome.complete(null);
                            }

                            @Override
                            public void failed(TransportFailure failure) {
                                outcome.complete(failure);
                            }
                        }));
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            if (deadlineWins) {
                while (System.nanoTime() - deadline < 0)
                    new CountDownLatch(1).await(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
            } else transport.close();
            release.countDown();
            TransportFailure failure = outcome.get(2, TimeUnit.SECONDS);
            assertTrue(failure != null, "An expired or closed transport must not report a successful write");
            assertEquals(
                    deadlineWins ? TransportFailure.Kind.WRITE_TIMEOUT : TransportFailure.Kind.CLOSED, failure.kind());
            assertEquals(0, transport.writtenFrames.get());
            assertTrue(transport.writes.isEmpty());
        } finally {
            release.countDown();
            writer.join(3000);
            transport.close();
            transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"guard", "written", "failed", "closed"})
    void observerFailuresCloseAndSettleOwnedWorkWithoutASecondTerminalCallback(String failing) throws Exception {
        FakeFrameTransport transport = new FakeFrameTransport();
        RuntimeException failure = new IllegalStateException("fixture callback failure");
        AtomicInteger written = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger closed = new AtomicInteger();
        AtomicReference<TransportFailure> reason = new AtomicReference<>();
        transport.start(new FrameListener() {
            @Override
            public void connected() {}

            @Override
            public void frame(byte[] frame) {}

            @Override
            public void closed(TransportFailure cause) {
                closed.incrementAndGet();
                reason.set(cause);
                if (failing.equals("closed")) throw failure;
            }
        });
        WriteObserver observer = new WriteObserver() {
            @Override
            public boolean beforeWrite() {
                if (failing.equals("guard")) throw failure;
                return !failing.equals("failed");
            }

            @Override
            public void written() {
                written.incrementAndGet();
                if (failing.equals("written")) throw failure;
            }

            @Override
            public void failed(TransportFailure cause) {
                failed.incrementAndGet();
                if (failing.equals("failed")) throw failure;
            }
        };
        try {
            transport.deferWrites = failing.equals("closed");
            assertDoesNotThrow(() -> transport.write(
                    RawPeer.header(0x15, 0, 1),
                    WriteClass.ORDINARY,
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(2),
                    observer));
            if (failing.equals("closed")) {
                transport.write(
                        RawPeer.header(0x15, 0, 2),
                        WriteClass.ORDINARY,
                        System.nanoTime() + TimeUnit.SECONDS.toNanos(2),
                        observer);
                assertDoesNotThrow(transport::close);
            }
            assertEquals(1, closed.get());
            assertEquals(failing.equals("written") ? 1 : 0, written.get());
            assertEquals(failing.equals("written") ? 0 : failing.equals("closed") ? 2 : 1, failed.get());
            if (failing.equals("guard"))
                transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            else {
                ExecutionException error = assertThrows(
                        ExecutionException.class,
                        () -> transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS));
                assertTrue(error.getCause() instanceof TransportFailure);
                assertEquals(TransportFailure.Kind.CLEANUP_FAILED, ((TransportFailure) error.getCause()).kind());
                assertEquals(failure, error.getCause().getCause());
            }
            assertEquals(
                    failing.equals("closed") ? TransportFailure.Kind.CLOSED : TransportFailure.Kind.OBSERVER_FAILED,
                    reason.get().kind());
            assertTrue(transport.deferred.isEmpty());
        } finally {
            transport.close();
        }
    }
}
