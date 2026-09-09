package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
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
