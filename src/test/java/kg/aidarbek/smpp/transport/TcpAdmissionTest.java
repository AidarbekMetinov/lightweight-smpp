package kg.aidarbek.smpp.transport;

import static kg.aidarbek.smpp.transport.TcpTransportTest.Events;
import static kg.aidarbek.smpp.transport.TcpTransportTest.Writes;
import static kg.aidarbek.smpp.transport.TcpTransportTest.await;
import static kg.aidarbek.smpp.transport.TcpTransportTest.deadline;
import static kg.aidarbek.smpp.transport.TcpTransportTest.frame;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kg.aidarbek.smpp.spi.TransportFailure;
import kg.aidarbek.smpp.spi.WriteClass;
import kg.aidarbek.smpp.spi.WriteHandle;
import org.junit.jupiter.api.Test;

class TcpAdmissionTest {
    @Test
    void concurrentQueuedCancellationWinsOnceAndPreventsEveryByte() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (TestPeer peer = new TestPeer(new TcpTransportConfig(256, 2, 512, 1, 256, 0))) {
            peer.transport.start(new Events());
            CountDownLatch entered = new CountDownLatch(1);
            WriteHandle active = peer.transport.write(frame(16, 1), WriteClass.ORDINARY, deadline(), new Writes(() -> {
                entered.countDown();
                await(release);
                return true;
            }));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertFalse(active.cancel());
            Writes cancelled = new Writes(() -> true);
            WriteHandle queued = peer.transport.write(frame(16, 2), WriteClass.ORDINARY, deadline(), cancelled);
            AtomicInteger wins = new AtomicInteger();
            CountDownLatch start = new CountDownLatch(1);
            List<Thread> contenders = new ArrayList<>();
            for (int i = 0; i < 16; i++)
                contenders.add(Thread.ofVirtual().start(() -> {
                    await(start);
                    if (queued.cancel()) wins.incrementAndGet();
                }));
            start.countDown();
            for (Thread contender : contenders) contender.join(2000);
            assertEquals(1, wins.get());
            TransportFailure failure = cancelled.result.get(2, TimeUnit.SECONDS);
            assertEquals(TransportFailure.Kind.CANCELLED, failure.kind());
            assertFalse(failure.writeStarted());
            assertEquals(0, cancelled.guards.get());
            assertEquals(1, cancelled.terminals.get());
            Writes replacement = new Writes(() -> true);
            peer.transport.write(frame(16, 3), WriteClass.ORDINARY, deadline(), replacement);
            release.countDown();
            assertArrayEquals(frame(16, 1), peer.peer.getInputStream().readNBytes(16));
            assertArrayEquals(frame(16, 3), peer.peer.getInputStream().readNBytes(16));
            assertNull(replacement.result.get(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
    }

    @Test
    void declinedRequestGuardPreventsWriteAndReleasesCapacity() throws Exception {
        try (TestPeer peer = new TestPeer(new TcpTransportConfig(256, 1, 256, 1, 256, 0))) {
            peer.transport.start(new Events());
            Writes refused = new Writes(() -> false);
            peer.transport.write(frame(16, 1), WriteClass.ORDINARY, deadline(), refused);
            TransportFailure failure = refused.result.get(2, TimeUnit.SECONDS);
            assertEquals(TransportFailure.Kind.REJECTED, failure.kind());
            assertFalse(failure.writeStarted());
            assertEquals(1, refused.guards.get());
            Writes next = new Writes(() -> true);
            peer.transport.write(frame(16, 2), WriteClass.ORDINARY, deadline(), next);
            assertArrayEquals(frame(16, 2), peer.peer.getInputStream().readNBytes(16));
            assertNull(next.result.get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void enforcesSeparateCountsAndBytesIncludingActiveWrites() throws Exception {
        for (TcpTransportConfig config : List.of(
                new TcpTransportConfig(256, 1, 1024, 1, 1024, 0), new TcpTransportConfig(256, 10, 16, 10, 16, 0))) {
            CountDownLatch release = new CountDownLatch(1);
            try (TestPeer peer = new TestPeer(config)) {
                peer.transport.start(new Events());
                CountDownLatch entered = new CountDownLatch(1);
                Writes ordinary = new Writes(() -> {
                    entered.countDown();
                    await(release);
                    return true;
                });
                peer.transport.write(frame(16, 1), WriteClass.ORDINARY, deadline(), ordinary);
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                Writes rejected = new Writes(() -> true);
                assertEquals(
                        TransportFailure.Kind.FULL,
                        assertThrows(
                                        TransportFailure.class,
                                        () -> peer.transport.write(
                                                frame(16, 2), WriteClass.ORDINARY, deadline(), rejected))
                                .kind());
                Writes control = new Writes(() -> true);
                peer.transport.write(frame(16, 3), WriteClass.CONTROL, deadline(), control);
                assertEquals(
                        TransportFailure.Kind.FULL,
                        assertThrows(
                                        TransportFailure.class,
                                        () -> peer.transport.write(
                                                frame(16, 4), WriteClass.CONTROL, deadline(), rejected))
                                .kind());
                assertEquals(0, rejected.terminals.get());
                assertEquals(0, rejected.guards.get());
                release.countDown();
                assertArrayEquals(frame(16, 1), peer.peer.getInputStream().readNBytes(16));
                assertArrayEquals(frame(16, 3), peer.peer.getInputStream().readNBytes(16));
                assertNull(ordinary.result.get(2, TimeUnit.SECONDS));
                assertNull(control.result.get(2, TimeUnit.SECONDS));
                Writes next = new Writes(() -> true);
                peer.transport.write(frame(16, 5), WriteClass.ORDINARY, deadline(), next);
                assertNull(next.result.get(2, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
        }
    }

    @Test
    void controlOvertakesQueuedOrdinaryWhileOrdinaryRemainsFifo() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (TestPeer peer = new TestPeer(TcpTransportConfig.defaults())) {
            peer.transport.start(new Events());
            CountDownLatch entered = new CountDownLatch(1);
            peer.transport.write(frame(16, 1), WriteClass.ORDINARY, deadline(), new Writes(() -> {
                entered.countDown();
                await(release);
                return true;
            }));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            peer.transport.write(frame(16, 2), WriteClass.ORDINARY, deadline(), new Writes(() -> true));
            peer.transport.write(frame(16, 3), WriteClass.ORDINARY, deadline(), new Writes(() -> true));
            peer.transport.write(frame(16, 4), WriteClass.CONTROL, deadline(), new Writes(() -> true));
            release.countDown();
            for (int sequence : new int[] {1, 4, 2, 3})
                assertArrayEquals(
                        frame(16, sequence), peer.peer.getInputStream().readNBytes(16));
        } finally {
            release.countDown();
        }
    }

    @Test
    void rejectsMalformedAndOversizedFramesBeforeTakingOwnership() throws Exception {
        try (TestPeer peer = new TestPeer(new TcpTransportConfig(256, 4, 1024, 1, 256, 0))) {
            peer.transport.start(new Events());
            Writes observer = new Writes(() -> true);
            for (byte[] invalid : List.of(new byte[15], new byte[16], frame(257, 1)))
                assertThrows(
                        IllegalArgumentException.class,
                        () -> peer.transport.write(invalid, WriteClass.ORDINARY, deadline(), observer));
            assertEquals(0, observer.guards.get());
            assertEquals(0, observer.terminals.get());
        }
    }
}
