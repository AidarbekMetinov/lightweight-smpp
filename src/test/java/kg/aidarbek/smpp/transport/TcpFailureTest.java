package kg.aidarbek.smpp.transport;

import static kg.aidarbek.smpp.transport.TcpTransportTest.Events;
import static kg.aidarbek.smpp.transport.TcpTransportTest.Writes;
import static kg.aidarbek.smpp.transport.TcpTransportTest.await;
import static kg.aidarbek.smpp.transport.TcpTransportTest.deadline;
import static kg.aidarbek.smpp.transport.TcpTransportTest.frame;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import kg.aidarbek.smpp.spi.TransportFailure;
import kg.aidarbek.smpp.spi.WriteClass;
import org.junit.jupiter.api.Test;

class TcpFailureTest {
    @Test
    void failedPhysicalCloseReportsCleanupFailureInsteadOfSuccessfulTermination() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            FailingCloseSocket socket = new FailingCloseSocket((InetSocketAddress) server.getLocalSocketAddress());
            TcpTransport transport = TcpTransport.adopt(socket, TcpTransportConfig.defaults());
            try (Socket peer = server.accept()) {
                assertTrue(peer.isConnected());
                transport.close();
                ExecutionException failure = assertThrows(
                        ExecutionException.class,
                        () -> transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS));
                assertEquals(TransportFailure.Kind.CLEANUP_FAILED, ((TransportFailure) failure.getCause()).kind());
                assertInstanceOf(IOException.class, failure.getCause().getCause());
                assertFalse(socket.isClosed());
            } finally {
                socket.fail = false;
                socket.close();
                transport.close();
            }
        }
    }

    @Test
    void terminalObserverFailuresAreReportedWithoutChangingTheWinningConnectionReason() throws Exception {
        for (boolean writeFailure : new boolean[] {false, true}) {
            TestPeer peer = new TestPeer(TcpTransportConfig.defaults());
            CountDownLatch guardRelease = new CountDownLatch(1);
            try {
                Events events = writeFailure
                        ? new Events()
                        : new Events(() -> {}, frame -> {}, failure -> {
                            throw new IllegalStateException("Closed observer failed");
                        });
                peer.transport.start(events);
                if (writeFailure) {
                    CountDownLatch entered = new CountDownLatch(1);
                    Writes writes = new Writes(
                            () -> {
                                entered.countDown();
                                await(guardRelease);
                                return true;
                            },
                            failure -> {
                                throw new IllegalStateException("Write observer failed");
                            });
                    peer.transport.write(frame(16, 1), WriteClass.ORDINARY, deadline(), writes);
                    assertTrue(entered.await(2, TimeUnit.SECONDS));
                }
                peer.transport.close();
                assertEquals(
                        TransportFailure.Kind.CLOSED,
                        events.closed.get(2, TimeUnit.SECONDS).kind());
                ExecutionException failure = assertThrows(
                        ExecutionException.class,
                        () -> peer.transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS));
                assertEquals(TransportFailure.Kind.CLEANUP_FAILED, ((TransportFailure) failure.getCause()).kind());
                assertInstanceOf(IllegalStateException.class, failure.getCause().getCause());
            } finally {
                guardRelease.countDown();
                peer.transport.close();
                peer.peer.close();
            }
        }
    }

    @Test
    void terminationCannotPrecedeTheWinningPhysicalSocketClose() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            PausingSocket socket = new PausingSocket((InetSocketAddress) server.getLocalSocketAddress());
            TcpTransport transport = TcpTransport.adopt(socket, TcpTransportConfig.defaults());
            Thread closer = null;
            try (Socket peer = server.accept()) {
                Events events = new Events();
                transport.start(events);
                events.connected.get(2, TimeUnit.SECONDS);
                closer = Thread.ofVirtual().start(transport::close);
                assertTrue(socket.closeEntered.await(2, TimeUnit.SECONDS));
                peer.shutdownOutput();
                events.closed.get(2, TimeUnit.SECONDS);
                assertThrows(
                        TimeoutException.class,
                        () -> transport.termination().toCompletableFuture().get(300, TimeUnit.MILLISECONDS));
                assertFalse(socket.isClosed());
                socket.closeRelease.countDown();
                closer.join(2000);
                transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
                assertTrue(socket.isClosed());
            } finally {
                socket.closeRelease.countDown();
                transport.close();
                socket.close();
                if (closer != null) closer.join(2000);
            }
        }
    }

    @Test
    void terminationWaitsUntilEveryAcceptedWriteHasSettled() throws Exception {
        CountDownLatch guardRelease = new CountDownLatch(1), notifyRelease = new CountDownLatch(1);
        Thread closer = null;
        try (TestPeer peer = new TestPeer(TcpTransportConfig.defaults())) {
            Events events = new Events();
            peer.transport.start(events);
            CountDownLatch guardEntered = new CountDownLatch(1), failedEntered = new CountDownLatch(1);
            Writes first = new Writes(() -> {
                guardEntered.countDown();
                await(guardRelease);
                return true;
            });
            peer.transport.write(frame(16, 1), WriteClass.ORDINARY, deadline(), first);
            assertTrue(guardEntered.await(2, TimeUnit.SECONDS));
            Writes second = new Writes(() -> true, failure -> {
                failedEntered.countDown();
                await(notifyRelease);
            });
            peer.transport.write(frame(16, 2), WriteClass.ORDINARY, deadline(), second);
            closer = Thread.ofVirtual().start(peer.transport::close);
            assertTrue(failedEntered.await(2, TimeUnit.SECONDS));
            events.closed.get(2, TimeUnit.SECONDS);
            assertThrows(
                    TimeoutException.class,
                    () -> peer.transport.termination().toCompletableFuture().get(300, TimeUnit.MILLISECONDS));
            notifyRelease.countDown();
            guardRelease.countDown();
            closer.join(2000);
            assertFalse(closer.isAlive());
            peer.transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(1, first.terminals.get());
            assertEquals(1, second.terminals.get());
            assertEquals(1, events.closures.get());
        } finally {
            guardRelease.countDown();
            notifyRelease.countDown();
            if (closer != null) closer.join(2000);
        }
    }

    @Test
    void eofAtBoundaryAndInsideAFrameHaveDistinctTerminalReasons() throws Exception {
        for (int length : new int[] {0, 3, 20, 32}) {
            try (TestPeer peer = new TestPeer(TcpTransportConfig.defaults())) {
                Events events = new Events();
                peer.transport.start(events);
                peer.peer.getOutputStream().write(frame(32, 1), 0, length);
                peer.peer.shutdownOutput();
                assertEquals(
                        length == 0 || length == 32 ? TransportFailure.Kind.EOF : TransportFailure.Kind.MALFORMED_FRAME,
                        events.closed.get(2, TimeUnit.SECONDS).kind());
                if (length == 32) assertArrayEquals(frame(32, 1), events.frames.poll(2, TimeUnit.SECONDS));
                else assertTrue(events.frames.isEmpty());
                peer.transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
                assertEquals(1, events.closures.get());
            }
        }
        try (TestPeer peer = new TestPeer(new TcpTransportConfig(16, 1, 16, 1, 16, 0))) {
            Events events = new Events();
            peer.transport.start(events);
            peer.peer.getOutputStream().write(new byte[] {0, 0, 0, 17});
            assertEquals(
                    TransportFailure.Kind.MALFORMED_FRAME,
                    events.closed.get(2, TimeUnit.SECONDS).kind());
            assertTrue(events.frames.isEmpty());
        }
    }

    @Test
    void resetPeerFailsPhysicalWriteWithConservativeTransmissionCertainty() throws Exception {
        int size = 8 * 1024 * 1024;
        CountDownLatch readerRelease = new CountDownLatch(1);
        try (TestPeer peer = new TestPeer(new TcpTransportConfig(size, 1, size, 1, 256, 1024))) {
            Events events = new Events(() -> await(readerRelease), frame -> {});
            peer.transport.start(events);
            events.connected.get(2, TimeUnit.SECONDS);
            peer.peer.setSoLinger(true, 0);
            peer.peer.close();
            Writes observer = new Writes(() -> true);
            peer.transport.write(frame(size, 1), WriteClass.ORDINARY, deadline(), observer);
            TransportFailure failure = observer.result.get(2, TimeUnit.SECONDS);
            assertEquals(TransportFailure.Kind.WRITE_FAILED, failure.kind());
            assertTrue(failure.writeStarted());
            assertEquals(1, observer.terminals.get());
            readerRelease.countDown();
            peer.transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
        } finally {
            readerRelease.countDown();
        }
    }

    @Test
    void listenerArgumentExceptionIsAnObserverFailureForValidWire() throws Exception {
        try (TestPeer peer = new TestPeer(TcpTransportConfig.defaults())) {
            Events events = new Events(() -> {}, frame -> {
                throw new IllegalArgumentException("Test callback");
            });
            peer.transport.start(events);
            peer.peer.getOutputStream().write(frame(16, 1));
            assertEquals(
                    TransportFailure.Kind.OBSERVER_FAILED,
                    events.closed.get(2, TimeUnit.SECONDS).kind());
            peer.transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void guardErrorsFailAcceptedWorkAndCloseWithoutStrandingWorkers() throws Exception {
        try (TestPeer peer = new TestPeer(TcpTransportConfig.defaults())) {
            Events events = new Events();
            peer.transport.start(events);
            Writes observer = new Writes(() -> {
                throw new AssertionError("Test guard error");
            });
            peer.transport.write(frame(16, 1), WriteClass.ORDINARY, deadline(), observer);
            assertEquals(
                    TransportFailure.Kind.OBSERVER_FAILED,
                    observer.result.get(2, TimeUnit.SECONDS).kind());
            assertEquals(
                    TransportFailure.Kind.OBSERVER_FAILED,
                    events.closed.get(2, TimeUnit.SECONDS).kind());
            peer.transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(1, observer.terminals.get());
        }
    }

    static final class PausingSocket extends Socket {
        final CountDownLatch closeEntered = new CountDownLatch(1);
        final CountDownLatch closeRelease = new CountDownLatch(1);

        PausingSocket(InetSocketAddress address) throws IOException {
            super(address.getAddress(), address.getPort());
        }

        @Override
        public void close() throws IOException {
            closeEntered.countDown();
            await(closeRelease);
            super.close();
        }
    }

    static final class FailingCloseSocket extends Socket {
        boolean fail = true;

        FailingCloseSocket(InetSocketAddress address) throws IOException {
            super(address.getAddress(), address.getPort());
        }

        @Override
        public void close() throws IOException {
            if (fail) throw new IOException("Injected socket close failure");
            super.close();
        }
    }
}
