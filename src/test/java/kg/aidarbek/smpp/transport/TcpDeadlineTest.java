package kg.aidarbek.smpp.transport;

import static kg.aidarbek.smpp.transport.TcpTransportTest.Events;
import static kg.aidarbek.smpp.transport.TcpTransportTest.Writes;
import static kg.aidarbek.smpp.transport.TcpTransportTest.await;
import static kg.aidarbek.smpp.transport.TcpTransportTest.deadline;
import static kg.aidarbek.smpp.transport.TcpTransportTest.frame;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.spi.TransportFailure;
import kg.aidarbek.smpp.spi.WriteClass;
import org.junit.jupiter.api.Test;

class TcpDeadlineTest {
    @Test
    void alreadyExpiredWriteIsRejectedWithoutObserverOrBytes() throws Exception {
        try (TestPeer peer = new TestPeer(TcpTransportConfig.defaults())) {
            peer.transport.start(new Events());
            Writes expired = new Writes(() -> true);
            TransportFailure failure = assertThrows(
                    TransportFailure.class,
                    () -> peer.transport.write(frame(16, 1), WriteClass.ORDINARY, System.nanoTime() - 1, expired));
            assertEquals(TransportFailure.Kind.WRITE_TIMEOUT, failure.kind());
            assertFalse(failure.writeStarted());
            assertEquals(0, expired.guards.get());
            assertEquals(0, expired.terminals.get());
        }
    }

    @Test
    void queuedDeadlinesExpireInBothClassesWhileAnActiveWriterIsPaused() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (TestPeer peer = new TestPeer(TcpTransportConfig.defaults())) {
            Events events = new Events();
            peer.transport.start(events);
            CountDownLatch entered = new CountDownLatch(1);
            peer.transport.write(frame(16, 1), WriteClass.ORDINARY, deadline(), new Writes(() -> {
                entered.countDown();
                await(release);
                return true;
            }));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            for (WriteClass writeClass : WriteClass.values()) {
                Writes expired = new Writes(() -> true);
                peer.transport.write(
                        frame(16, 2), writeClass, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(100), expired);
                TransportFailure failure = expired.result.get(2, TimeUnit.SECONDS);
                assertEquals(TransportFailure.Kind.WRITE_TIMEOUT, failure.kind());
                assertFalse(failure.writeStarted());
                assertEquals(0, expired.guards.get());
                assertEquals(1, expired.terminals.get());
            }
            assertFalse(events.closed.isDone());
            release.countDown();
            Writes marker = new Writes(() -> true);
            peer.transport.write(frame(16, 3), WriteClass.ORDINARY, deadline(), marker);
            assertArrayEquals(frame(16, 1), peer.peer.getInputStream().readNBytes(16));
            assertArrayEquals(frame(16, 3), peer.peer.getInputStream().readNBytes(16));
        } finally {
            release.countDown();
        }
    }

    @Test
    void slowReaderWriteDeadlineClosesSocketAndCleansEveryWorker() throws Exception {
        int size = 8 * 1024 * 1024;
        try (TestPeer peer = new TestPeer(new TcpTransportConfig(size, 2, size + 16L, 1, 256, 1024))) {
            peer.peer.setReceiveBufferSize(1024);
            Events events = new Events();
            peer.transport.start(events);
            Writes active = new Writes(() -> true);
            peer.transport.write(
                    frame(size, 1), WriteClass.ORDINARY, System.nanoTime() + TimeUnit.SECONDS.toNanos(2), active);
            assertArrayEquals(
                    java.util.Arrays.copyOf(frame(size, 1), 16),
                    peer.peer.getInputStream().readNBytes(16));
            Writes queued = new Writes(() -> true);
            peer.transport.write(frame(16, 2), WriteClass.ORDINARY, deadline(), queued);
            TransportFailure failure = active.result.get(4, TimeUnit.SECONDS);
            assertEquals(TransportFailure.Kind.WRITE_TIMEOUT, failure.kind());
            assertTrue(failure.writeStarted());
            assertFalse(queued.result.get(2, TimeUnit.SECONDS).writeStarted());
            assertEquals(0, queued.guards.get());
            assertEquals(
                    TransportFailure.Kind.WRITE_TIMEOUT,
                    events.closed.get(2, TimeUnit.SECONDS).kind());
            peer.transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(1, active.terminals.get());
            assertEquals(1, queued.terminals.get());
        }
    }
}
