package kg.aidarbek.smpp.transport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocket;
import kg.aidarbek.smpp.spi.TransportFailure;
import kg.aidarbek.smpp.spi.WriteClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TlsCleanupTest {
    @ParameterizedTest
    @CsvSource({"true,true", "false,true", "true,false"})
    void mutualTlsRequiresAClientIdentityTrustedByTheServer(boolean clientIdentity, boolean serverTrust)
            throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                SSLSocket client = (SSLSocket) TlsTestMaterial.context(clientIdentity, true)
                        .getSocketFactory()
                        .createSocket()) {
            client.connect(listener.getLocalSocketAddress(), 2000);
            client.setSoTimeout(2000);
            TcpTransport server = TcpTransport.adopt(
                    listener.accept(),
                    TcpTransportConfig.defaults(),
                    TlsConfig.server(TlsTestMaterial.context(true, serverTrust), Duration.ofSeconds(2), true));
            try {
                TcpTransportTest.Events events = new TcpTransportTest.Events();
                server.start(events);
                if (clientIdentity && serverTrust) {
                    client.startHandshake();
                    assertTrue(events.connected.get(2, TimeUnit.SECONDS));
                    byte[] frame = TcpTransportTest.frame(16, 1);
                    client.getOutputStream().write(frame);
                    assertArrayEquals(frame, events.frames.poll(2, TimeUnit.SECONDS));
                } else {
                    try {
                        client.startHandshake();
                        client.getInputStream().read();
                    } catch (IOException expected) {
                        /* Rejection may arrive as a TLS alert or an already-aborted raw socket. */
                    }
                    assertEquals(
                            TransportFailure.Kind.TLS_HANDSHAKE_FAILED,
                            events.closed.get(2, TimeUnit.SECONDS).kind());
                    assertFalse(events.connected.isDone());
                }
                server.close();
                server.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            } finally {
                server.close();
            }
        }
    }

    @Test
    void acceptedHandshakeBudgetIncludesTimeBeforeListenerInstallation() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
                TcpTransport server = TcpTransport.adopt(
                        listener.accept(),
                        TcpTransportConfig.defaults(),
                        TlsConfig.server(TlsTestMaterial.context(true, true), Duration.ofMillis(50)))) {
            assertFalse(new CountDownLatch(1).await(100, TimeUnit.MILLISECONDS));
            TcpTransportTest.Events events = new TcpTransportTest.Events();
            server.start(events);
            assertEquals(
                    TransportFailure.Kind.TLS_HANDSHAKE_TIMEOUT,
                    events.closed.get(1, TimeUnit.SECONDS).kind());
            assertFalse(events.connected.isDone());
            client.setSoTimeout(1000);
            assertEquals(-1, client.getInputStream().read());
            server.termination().toCompletableFuture().get(1, TimeUnit.SECONDS);
        }
    }

    @Test
    void explicitCloseAbortsAStalledHandshakeAndNeverPublishesReadiness() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            TcpTransport client = TcpTransport.connect(
                    (InetSocketAddress) listener.getLocalSocketAddress(),
                    TcpTransportConfig.defaults(),
                    TcpTransportTest.deadline(),
                    TlsConfig.client(TlsTestMaterial.context(false, true), "localhost", Duration.ofSeconds(5)));
            try {
                TcpTransportTest.Events events = new TcpTransportTest.Events();
                client.start(events);
                try (Socket peer = listener.accept()) {
                    peer.setSoTimeout(2000);
                    assertEquals(22, peer.getInputStream().read());
                    client.close();
                    client.close();
                    client.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
                    assertEquals(
                            TransportFailure.Kind.CLOSED,
                            events.closed.get(2, TimeUnit.SECONDS).kind());
                    assertFalse(events.connected.isDone());
                    assertTrue(peer.getInputStream().readAllBytes().length < 65536);
                    assertEquals(1, events.closures.get());
                }
            } finally {
                client.close();
            }
        }
    }

    @Test
    void tlsSlowReaderDeadlineAbortsRawSocketAndSettlesAQueuedControlWithoutStartingIt() throws Exception {
        int size = 8 * 1024 * 1024;
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                SSLSocket client = (SSLSocket)
                        TlsTestMaterial.context(false, true).getSocketFactory().createSocket()) {
            client.setReceiveBufferSize(1024);
            client.connect(listener.getLocalSocketAddress(), 2000);
            client.setSoTimeout(3000);
            try (TcpTransport server = TcpTransport.adopt(
                    listener.accept(),
                    new TcpTransportConfig(size, 1, size, 1, 256, 1024),
                    TlsConfig.server(TlsTestMaterial.context(true, true), Duration.ofSeconds(2)))) {
                TcpTransportTest.Events events = new TcpTransportTest.Events();
                server.start(events);
                client.startHandshake();
                assertTrue(events.connected.get(2, TimeUnit.SECONDS));
                TcpTransportTest.Writes active = new TcpTransportTest.Writes(() -> true);
                byte[] frame = TcpTransportTest.frame(size, 1);
                server.write(frame, WriteClass.ORDINARY, System.nanoTime() + TimeUnit.SECONDS.toNanos(1), active);
                assertArrayEquals(
                        Arrays.copyOf(frame, 16), client.getInputStream().readNBytes(16));
                TcpTransportTest.Writes control = new TcpTransportTest.Writes(() -> true);
                server.write(TcpTransportTest.frame(16, 2), WriteClass.CONTROL, TcpTransportTest.deadline(), control);
                TransportFailure failed = active.result.get(3, TimeUnit.SECONDS);
                assertEquals(TransportFailure.Kind.WRITE_TIMEOUT, failed.kind());
                assertTrue(failed.writeStarted());
                assertEquals(0, control.guards.get());
                assertFalse(control.result.get(2, TimeUnit.SECONDS).writeStarted());
                server.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
                assertEquals(1, active.terminals.get());
                assertEquals(1, control.terminals.get());
            }
        }
    }
}
