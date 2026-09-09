package kg.aidarbek.smpp.transport;

import static kg.aidarbek.smpp.transport.TcpTransportTest.Events;
import static kg.aidarbek.smpp.transport.TcpTransportTest.Writes;
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
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.spi.TransportFailure;
import kg.aidarbek.smpp.spi.WriteClass;
import org.junit.jupiter.api.Test;

class TcpConnectTest {
    @Test
    void connectsAsynchronouslyAndInstallsListenerBeforeAnyRead() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                TcpTransport transport = TcpTransport.connect(
                        (InetSocketAddress) server.getLocalSocketAddress(),
                        TcpTransportConfig.defaults(),
                        deadline())) {
            server.setSoTimeout(2000);
            Events events = new Events();
            transport.start(events);
            try (Socket peer = server.accept()) {
                peer.setSoTimeout(2000);
                assertTrue(events.connected.get(2, TimeUnit.SECONDS));
                peer.getOutputStream().write(frame(16, 1));
                assertArrayEquals(frame(16, 1), events.frames.poll(2, TimeUnit.SECONDS));
                Writes writes = new Writes(() -> true);
                transport.write(frame(16, 2), WriteClass.CONTROL, deadline(), writes);
                assertArrayEquals(frame(16, 2), peer.getInputStream().readNBytes(16));
                assertNull(writes.result.get(2, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void expiredAndRefusedConnectsTerminateWithNoConnectedNotification() throws Exception {
        InetSocketAddress unused;
        try (ServerSocket reservation = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            unused = (InetSocketAddress) reservation.getLocalSocketAddress();
        }
        for (boolean expired : new boolean[] {true, false}) {
            try (TcpTransport transport = TcpTransport.connect(
                    unused, TcpTransportConfig.defaults(), expired ? System.nanoTime() - 1 : deadline())) {
                Events events = new Events();
                transport.start(events);
                assertEquals(
                        expired ? TransportFailure.Kind.CONNECT_TIMEOUT : TransportFailure.Kind.CONNECT_FAILED,
                        events.closed.get(2, TimeUnit.SECONDS).kind());
                assertFalse(events.connected.isDone());
                transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void validatesAdoptionAndResolvedAddressesBeforeTakingOwnership() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> TcpTransport.connect(
                        InetSocketAddress.createUnresolved("invalid.example", 2775),
                        TcpTransportConfig.defaults(),
                        deadline()));
        assertThrows(
                NullPointerException.class,
                () -> TcpTransport.connect(null, TcpTransportConfig.defaults(), deadline()));
        try (Socket socket = new Socket()) {
            assertThrows(
                    IllegalArgumentException.class, () -> TcpTransport.adopt(socket, TcpTransportConfig.defaults()));
            assertFalse(socket.isClosed());
            assertThrows(NullPointerException.class, () -> TcpTransport.adopt(socket, null));
            assertFalse(socket.isClosed());
        }
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                Socket peer = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort());
                Socket accepted = server.accept()) {
            assertThrows(NullPointerException.class, () -> TcpTransport.adopt(accepted, null));
            assertFalse(accepted.isClosed());
            accepted.setSoLinger(true, 30);
            accepted.setSoTimeout(100);
            try (TcpTransport transport = TcpTransport.adopt(accepted, TcpTransportConfig.defaults())) {
                assertEquals(-1, accepted.getSoLinger());
                assertEquals(0, accepted.getSoTimeout());
                assertTrue(accepted.getTcpNoDelay());
                assertFalse(transport.termination().toCompletableFuture().isDone());
            }
            assertTrue(accepted.isClosed());
            assertFalse(peer.isClosed());
        }
    }
}
