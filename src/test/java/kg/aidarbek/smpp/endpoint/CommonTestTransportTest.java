package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kg.aidarbek.smpp.spi.FrameListener;
import kg.aidarbek.smpp.spi.TransportFailure;
import kg.aidarbek.smpp.spi.WriteClass;
import kg.aidarbek.smpp.spi.WriteObserver;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Deferred fixture progress preserves the port deadline contract when explicitly driven. */
class CommonTestTransportTest {
    @ParameterizedTest
    @ValueSource(strings = {"connected", "frame", "guard", "written", "failed"})
    void failedCallbacksPreserveTheCauseAndDistinguishProgressFromCleanup(String phase) throws Exception {
        CommonTestTransport transport = new CommonTestTransport();
        IllegalStateException progressFailure = new IllegalStateException("fixture progress failure");
        CompletableFuture<TransportFailure> closed = new CompletableFuture<>();
        FrameListener listener = new FrameListener() {
            @Override
            public void connected() {
                if (phase.equals("connected")) throw progressFailure;
            }

            @Override
            public void frame(byte[] frame) {
                if (phase.equals("frame")) throw progressFailure;
            }

            @Override
            public void closed(TransportFailure failure) {
                closed.complete(failure);
            }
        };
        try {
            assertDoesNotThrow(() -> transport.start(listener));
            if (phase.equals("frame")) assertDoesNotThrow(() -> transport.receive(RawPeer.header(0x15, 0, 1)));
            if (phase.equals("guard") || phase.equals("written") || phase.equals("failed")) {
                CompletableFuture<TransportFailure> writeFailure = new CompletableFuture<>();
                assertDoesNotThrow(() -> transport.write(
                        RawPeer.header(0x15, 0, 1),
                        WriteClass.ORDINARY,
                        System.nanoTime() + TimeUnit.SECONDS.toNanos(2),
                        new WriteObserver() {
                            @Override
                            public boolean beforeWrite() {
                                if (phase.equals("guard")) throw progressFailure;
                                return !phase.equals("failed");
                            }

                            @Override
                            public void written() {
                                if (phase.equals("written")) throw progressFailure;
                                writeFailure.complete(null);
                            }

                            @Override
                            public void failed(TransportFailure failure) {
                                if (phase.equals("failed")) throw progressFailure;
                                writeFailure.complete(failure);
                            }
                        }));
                if (phase.equals("guard"))
                    assertEquals(
                            TransportFailure.Kind.OBSERVER_FAILED,
                            writeFailure.get(2, TimeUnit.SECONDS).kind());
            }
            TransportFailure reason = closed.get(2, TimeUnit.SECONDS);
            assertEquals(TransportFailure.Kind.OBSERVER_FAILED, reason.kind());
            assertSame(progressFailure, reason.getCause());
            if (phase.equals("written") || phase.equals("failed")) {
                ExecutionException failure = assertThrows(
                        ExecutionException.class,
                        () -> transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS));
                assertEquals(TransportFailure.Kind.CLEANUP_FAILED, ((TransportFailure) failure.getCause()).kind());
                assertSame(progressFailure, failure.getCause().getCause());
            } else
                assertDoesNotThrow(
                        () -> transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertEquals(phase.equals("written") ? 1 : 0, transport.written.size());
        } finally {
            transport.close();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void deferredProgressCannotEmitAFrameAfterItsOriginalDeadline(boolean claimed) throws Exception {
        CommonTestTransport transport = new CommonTestTransport();
        CompletableFuture<TransportFailure> result = new CompletableFuture<>();
        CompletableFuture<TransportFailure> closed = new CompletableFuture<>();
        AtomicInteger guards = new AtomicInteger();
        transport.start(new FrameListener() {
            @Override
            public void connected() {}

            @Override
            public void frame(byte[] frame) {}

            @Override
            public void closed(TransportFailure failure) {
                closed.complete(failure);
            }
        });
        transport.deferred = true;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        try {
            transport.write(RawPeer.header(0x15, 0, 1), WriteClass.ORDINARY, deadline, new WriteObserver() {
                @Override
                public boolean beforeWrite() {
                    guards.incrementAndGet();
                    return true;
                }

                @Override
                public void written() {
                    result.complete(null);
                }

                @Override
                public void failed(TransportFailure failure) {
                    result.complete(failure);
                }
            });
            if (claimed) assertTrue(transport.claimNext());
            while (System.nanoTime() - deadline < 0)
                new CountDownLatch(1).await(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
            if (claimed) transport.completeClaimed();
            else assertFalse(transport.claimNext(), "An expired queued write must never invoke the guard");
            TransportFailure failure = result.get(2, TimeUnit.SECONDS);
            assertNotNull(failure, "An expired active write must fail instead of recording bytes");
            assertEquals(TransportFailure.Kind.WRITE_TIMEOUT, failure.kind());
            assertEquals(claimed, failure.writeStarted());
            assertEquals(claimed ? 1 : 0, guards.get());
            assertTrue(transport.written.isEmpty());
            if (claimed)
                assertEquals(
                        TransportFailure.Kind.WRITE_TIMEOUT,
                        closed.get(2, TimeUnit.SECONDS).kind());
            else assertFalse(closed.isDone());
        } finally {
            transport.close();
            transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
        }
    }
}
