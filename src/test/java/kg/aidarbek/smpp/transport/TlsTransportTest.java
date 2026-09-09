package kg.aidarbek.smpp.transport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import kg.aidarbek.smpp.spi.TransportFailure;
import kg.aidarbek.smpp.spi.WriteClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TlsTransportTest {
    @Test
    void trustedClientExchangesFramesOnlyAfterHandshake() throws Exception {
        try (SSLServerSocket server = (SSLServerSocket) TlsTestMaterial.context(true, true)
                .getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(3000);
            CompletableFuture<byte[]> received = new CompletableFuture<>();
            Thread peer = Thread.ofVirtual().start(() -> {
                try (SSLSocket socket = (SSLSocket) server.accept()) {
                    socket.setSoTimeout(3000);
                    byte[] frame = socket.getInputStream().readNBytes(16);
                    socket.getOutputStream().write(frame);
                    received.complete(frame);
                } catch (Exception failure) {
                    received.completeExceptionally(failure);
                }
            });
            try (TcpTransport transport = TcpTransport.connect(
                    (InetSocketAddress) server.getLocalSocketAddress(),
                    TcpTransportConfig.defaults(),
                    TcpTransportTest.deadline(),
                    TlsConfig.client(TlsTestMaterial.context(false, true), "localhost", Duration.ofSeconds(2)))) {
                TcpTransportTest.Events events = new TcpTransportTest.Events();
                transport.start(events);
                assertTrue(events.connected.get(3, TimeUnit.SECONDS));
                byte[] expected = TcpTransportTest.frame(16, 1);
                transport.write(
                        expected,
                        WriteClass.ORDINARY,
                        TcpTransportTest.deadline(),
                        new TcpTransportTest.Writes(() -> true));
                assertArrayEquals(expected, received.get(3, TimeUnit.SECONDS));
                assertArrayEquals(expected, events.frames.poll(3, TimeUnit.SECONDS));
            } finally {
                peer.join(4000);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"true,wrong.example", "false,localhost"})
    void trustAndExpectedIdentityAreBothRequired(boolean trust, String identity) throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (SSLServerSocket server = (SSLServerSocket) TlsTestMaterial.context(true, true)
                .getServerSocketFactory()
                .createServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            server.setSoTimeout(3000);
            Thread peer = Thread.ofVirtual().start(() -> {
                try (SSLSocket socket = (SSLSocket) server.accept()) {
                    socket.setSoTimeout(3000);
                    socket.startHandshake();
                    release.await(3, TimeUnit.SECONDS);
                } catch (Exception expected) {
                    /* A rejected handshake also closes this peer. */
                }
            });
            try (TcpTransport transport = TcpTransport.connect(
                    (InetSocketAddress) server.getLocalSocketAddress(),
                    TcpTransportConfig.defaults(),
                    TcpTransportTest.deadline(),
                    TlsConfig.client(TlsTestMaterial.context(false, trust), identity, Duration.ofSeconds(2)))) {
                TcpTransportTest.Events events = new TcpTransportTest.Events();
                transport.start(events);
                TransportFailure failure = events.closed.get(2, TimeUnit.SECONDS);
                assertEquals(TransportFailure.Kind.TLS_HANDSHAKE_FAILED, failure.kind());
                assertFalse(events.connected.isDone());
                transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            } finally {
                release.countDown();
                peer.join(4000);
            }
        }
    }

    @Test
    void stalledHandshakeOwnsAnIndependentDeadlineAndNeverStartsQueuedFrames() throws Exception {
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                TcpTransport transport = TcpTransport.connect(
                        (InetSocketAddress) server.getLocalSocketAddress(),
                        TcpTransportConfig.defaults(),
                        TcpTransportTest.deadline(),
                        TlsConfig.client(TlsTestMaterial.context(false, true), "localhost", Duration.ofMillis(150)))) {
            server.setSoTimeout(3000);
            TcpTransportTest.Events events = new TcpTransportTest.Events();
            transport.start(events);
            try (Socket peer = server.accept()) {
                peer.setSoTimeout(3000);
                TcpTransportTest.Writes write = new TcpTransportTest.Writes(() -> true);
                transport.write(TcpTransportTest.frame(16, 9), WriteClass.CONTROL, TcpTransportTest.deadline(), write);
                assertEquals(22, peer.getInputStream().read());
                assertEquals(
                        TransportFailure.Kind.TLS_HANDSHAKE_TIMEOUT,
                        events.closed.get(2, TimeUnit.SECONDS).kind());
                assertFalse(events.connected.isDone());
                assertEquals(0, write.guards.get());
                assertEquals(
                        TransportFailure.Kind.TLS_HANDSHAKE_TIMEOUT,
                        write.result.get(2, TimeUnit.SECONDS).kind());
                transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
                assertEquals(1, events.closures.get());
            }
        }
    }

    @Test
    void acceptedTlsTransportAuthenticatesAndExchangesWithAnIndependentClient() throws Exception {
        CompletableFuture<TcpTransportTest.Events> accepted = new CompletableFuture<>();
        CompletableFuture<kg.aidarbek.smpp.spi.FrameTransport> owned = new CompletableFuture<>();
        try (TcpListener listener = TcpListener.bind(
                        new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                        2,
                        TcpTransportConfig.defaults(),
                        TlsConfig.server(TlsTestMaterial.context(true, true), Duration.ofSeconds(2)),
                        (transport, peer) -> {
                            TcpTransportTest.Events events = new TcpTransportTest.Events();
                            transport.start(events);
                            owned.complete(transport);
                            accepted.complete(events);
                            return true;
                        });
                SSLSocket client = (SSLSocket)
                        TlsTestMaterial.context(false, true).getSocketFactory().createSocket()) {
            client.connect(listener.localAddress(), 2000);
            client.setSoTimeout(3000);
            client.startHandshake();
            TcpTransportTest.Events events = accepted.get(2, TimeUnit.SECONDS);
            assertTrue(events.connected.get(2, TimeUnit.SECONDS));
            byte[] request = TcpTransportTest.frame(16, 7);
            client.getOutputStream().write(request);
            assertArrayEquals(request, events.frames.poll(2, TimeUnit.SECONDS));
            var transport = owned.get(2, TimeUnit.SECONDS);
            transport.write(
                    request, WriteClass.CONTROL, TcpTransportTest.deadline(), new TcpTransportTest.Writes(() -> true));
            assertArrayEquals(request, client.getInputStream().readNBytes(16));
            transport.close();
            transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
        } finally {
            if (owned.isDone()) owned.get().close();
        }
    }
}
