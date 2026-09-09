package kg.aidarbek.smpp.transport;

import static kg.aidarbek.smpp.transport.TcpTransportTest.Events;
import static kg.aidarbek.smpp.transport.TcpTransportTest.Writes;
import static kg.aidarbek.smpp.transport.TcpTransportTest.await;
import static kg.aidarbek.smpp.transport.TcpTransportTest.awaitUninterruptibly;
import static kg.aidarbek.smpp.transport.TcpTransportTest.deadline;
import static kg.aidarbek.smpp.transport.TcpTransportTest.frame;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.spi.WriteClass;
import org.junit.jupiter.api.Test;

class TcpLifecycleTest {
    @Test
    void concurrentCloseDuringConnectionCompletesOnceAndPreStartCloseOwnsNoWorkers() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 16, InetAddress.getLoopbackAddress())) {
            for (int attempt = 0; attempt < 8; attempt++) {
                TcpTransport transport = TcpTransport.connect(
                        (InetSocketAddress) server.getLocalSocketAddress(), TcpTransportConfig.defaults(), deadline());
                Events events = new Events();
                transport.start(events);
                CountDownLatch start = new CountDownLatch(1);
                List<Thread> closers = new ArrayList<>();
                for (int i = 0; i < 4; i++)
                    closers.add(Thread.ofVirtual().start(() -> {
                        await(start);
                        transport.close();
                    }));
                start.countDown();
                for (Thread closer : closers) {
                    closer.join(2000);
                    assertFalse(closer.isAlive());
                }
                transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
                assertEquals(1, events.closures.get());
            }
            TcpTransport unused = TcpTransport.connect(
                    (InetSocketAddress) server.getLocalSocketAddress(), TcpTransportConfig.defaults(), deadline());
            unused.close();
            unused.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertThrows(IllegalStateException.class, () -> unused.start(new Events()));
        }
    }

    @Test
    void terminationDependentsCannotDelaySocketOrIoWorkerCleanup() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        try (TestPeer peer = new TestPeer(TcpTransportConfig.defaults())) {
            Events events = new Events();
            peer.transport.start(events);
            var dependent = peer.transport.termination().thenRun(() -> {
                entered.countDown();
                awaitUninterruptibly(release);
            });
            peer.transport.close();
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals(-1, peer.peer.getInputStream().read());
            peer.transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(1, events.closures.get());
            release.countDown();
            dependent.toCompletableFuture().get(2, TimeUnit.SECONDS);
        } finally {
            release.countDown();
        }
    }

    @Test
    void positiveSignedLongDeadlineOffsetsSurviveAbsoluteTimestampOverflow() throws Exception {
        try (TestPeer peer = new TestPeer(TcpTransportConfig.defaults())) {
            peer.transport.start(new Events());
            Writes observer = new Writes(() -> true);
            peer.transport.write(frame(16, 1), WriteClass.ORDINARY, System.nanoTime() + Long.MAX_VALUE, observer);
            assertArrayEquals(frame(16, 1), peer.peer.getInputStream().readNBytes(16));
            assertNull(observer.result.get(2, TimeUnit.SECONDS));
        }
    }
}
