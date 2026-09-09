package kg.aidarbek.smpp.transport;

import static kg.aidarbek.smpp.transport.TcpTransportTest.Events;
import static kg.aidarbek.smpp.transport.TcpTransportTest.Writes;
import static kg.aidarbek.smpp.transport.TcpTransportTest.await;
import static kg.aidarbek.smpp.transport.TcpTransportTest.awaitUninterruptibly;
import static kg.aidarbek.smpp.transport.TcpTransportTest.deadline;
import static kg.aidarbek.smpp.transport.TcpTransportTest.frame;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import kg.aidarbek.smpp.spi.FrameTransport;
import kg.aidarbek.smpp.spi.TransportFailure;
import kg.aidarbek.smpp.spi.WriteClass;
import org.junit.jupiter.api.Test;

class TcpListenerTest {
    @Test
    void reportsFailedCleanupOfARejectedConnection() throws Exception {
        TcpListener listener = TcpListener.bind(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                4,
                TcpTransportConfig.defaults(),
                (transport, peer) -> {
                    transport.start(new Events(() -> {}, frame -> {}, failure -> {
                        throw new IllegalStateException("Rejected transport cleanup failed");
                    }));
                    return false;
                });
        try (Socket peer = new Socket(
                listener.localAddress().getAddress(), listener.localAddress().getPort())) {
            assertTrue(peer.isConnected());
            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> listener.termination().toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertEquals(TransportFailure.Kind.CLEANUP_FAILED, ((TransportFailure) failure.getCause()).kind());
        } finally {
            listener.close();
        }
    }

    @Test
    void retainsRejectedTransportOwnershipUntilItsInternalCleanupFinishes() throws Exception {
        CountDownLatch closing = new CountDownLatch(1), release = new CountDownLatch(1);
        TcpListener listener = TcpListener.bind(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                4,
                TcpTransportConfig.defaults(),
                (transport, peer) -> {
                    transport.start(new Events(() -> {}, frame -> {}, failure -> {
                        closing.countDown();
                        awaitUninterruptibly(release);
                    }));
                    return false;
                });
        try (Socket peer = new Socket(
                listener.localAddress().getAddress(), listener.localAddress().getPort())) {
            assertTrue(peer.isConnected());
            assertTrue(closing.await(2, TimeUnit.SECONDS));
            listener.close();
            assertThrows(
                    TimeoutException.class,
                    () -> listener.termination().toCompletableFuture().get(300, TimeUnit.MILLISECONDS));
            release.countDown();
            listener.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            listener.close();
        }
    }

    @Test
    void terminationWaitsForAnUntransferredConnectionDuringConcurrentClose() throws Exception {
        CountDownLatch acceptRelease = new CountDownLatch(1), guardRelease = new CountDownLatch(1);
        CountDownLatch notifyRelease = new CountDownLatch(1);
        LinkedBlockingQueue<FrameTransport> handoff = new LinkedBlockingQueue<>(1);
        TcpListener listener = TcpListener.bind(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                4,
                TcpTransportConfig.defaults(),
                (transport, peer) -> {
                    handoff.add(transport);
                    await(acceptRelease);
                    return false;
                });
        Thread closer = null;
        try (Socket peer = new Socket(
                listener.localAddress().getAddress(), listener.localAddress().getPort())) {
            assertTrue(peer.isConnected());
            FrameTransport pending = handoff.poll(2, TimeUnit.SECONDS);
            assertNotNull(pending);
            pending.start(new Events());
            CountDownLatch guardEntered = new CountDownLatch(1), failedEntered = new CountDownLatch(1);
            pending.write(frame(16, 1), WriteClass.ORDINARY, deadline(), new Writes(() -> {
                guardEntered.countDown();
                await(guardRelease);
                return true;
            }));
            assertTrue(guardEntered.await(2, TimeUnit.SECONDS));
            pending.write(frame(16, 2), WriteClass.ORDINARY, deadline(), new Writes(() -> true, failure -> {
                failedEntered.countDown();
                await(notifyRelease);
            }));
            closer = Thread.ofVirtual().start(listener::close);
            assertTrue(failedEntered.await(2, TimeUnit.SECONDS));
            acceptRelease.countDown();
            assertThrows(
                    TimeoutException.class,
                    () -> listener.termination().toCompletableFuture().get(300, TimeUnit.MILLISECONDS));
            notifyRelease.countDown();
            guardRelease.countDown();
            closer.join(2000);
            listener.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            pending.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
        } finally {
            acceptRelease.countDown();
            guardRelease.countDown();
            notifyRelease.countDown();
            listener.close();
            if (closer != null) closer.join(2000);
        }
    }

    @Test
    void rejectsInvalidListenerArgumentsBeforeBindingOrStartingWorkers() {
        InetSocketAddress local = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        assertThrows(
                NullPointerException.class,
                () -> TcpListener.bind(local, 1, null, (transport, peer) -> false)
                        .close());
        assertThrows(
                NullPointerException.class,
                () -> TcpListener.bind(local, 1, TcpTransportConfig.defaults(), null)
                        .close());
        assertThrows(
                NullPointerException.class,
                () -> TcpListener.bind(null, 1, TcpTransportConfig.defaults(), (transport, peer) -> false)
                        .close());
        assertThrows(
                IllegalArgumentException.class,
                () -> TcpListener.bind(local, 0, TcpTransportConfig.defaults(), (transport, peer) -> false)
                        .close());
        assertThrows(
                IllegalArgumentException.class,
                () -> TcpListener.bind(
                                InetSocketAddress.createUnresolved("invalid.example", 0),
                                1,
                                TcpTransportConfig.defaults(),
                                (transport, peer) -> false)
                        .close());
    }

    @Test
    void transfersUnstartedConnectionsWithPeerAddressAndKeepsTheirOwnershipSeparate() throws Exception {
        LinkedBlockingQueue<FrameTransport> accepted = new LinkedBlockingQueue<>(1);
        AtomicReference<SocketAddress> remote = new AtomicReference<>();
        TcpListener listener = TcpListener.bind(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                4,
                TcpTransportConfig.defaults(),
                (transport, peer) -> {
                    remote.set(peer);
                    return accepted.offer(transport);
                });
        try (Socket peer = new Socket(
                listener.localAddress().getAddress(), listener.localAddress().getPort())) {
            peer.setSoTimeout(2000);
            FrameTransport transport = accepted.poll(2, TimeUnit.SECONDS);
            assertNotNull(transport);
            try (transport) {
                assertEquals(peer.getLocalPort(), ((InetSocketAddress) remote.get()).getPort());
                peer.getOutputStream().write(frame(16, 1));
                Events events = new Events();
                transport.start(events);
                assertArrayEquals(frame(16, 1), events.frames.poll(2, TimeUnit.SECONDS));
                listener.close();
                listener.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
                peer.getOutputStream().write(frame(16, 2));
                assertArrayEquals(frame(16, 2), events.frames.poll(2, TimeUnit.SECONDS));
            }
        } finally {
            listener.close();
        }
    }

    @Test
    void rejectedOrThrowingAdmissionClosesSocketAndAllowsNextAccept() throws Exception {
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        TcpListener listener = TcpListener.bind(
                new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                4,
                TcpTransportConfig.defaults(),
                (transport, peer) -> {
                    if (attempts.incrementAndGet() == 1) throw new IllegalStateException("Reject test peer");
                    return false;
                });
        try {
            for (int i = 0; i < 2; i++) {
                try (Socket peer = new Socket(
                        listener.localAddress().getAddress(),
                        listener.localAddress().getPort())) {
                    peer.setSoTimeout(2000);
                    assertEquals(-1, peer.getInputStream().read());
                }
            }
            assertEquals(2, attempts.get());
        } finally {
            listener.close();
        }
        listener.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
    }
}
