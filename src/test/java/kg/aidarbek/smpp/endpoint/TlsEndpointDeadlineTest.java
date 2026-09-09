package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.net.ssl.SSLServerSocket;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.transport.TlsConfig;
import kg.aidarbek.smpp.transport.TlsTestMaterial;
import org.junit.jupiter.api.Test;

class TlsEndpointDeadlineTest {
    @Test
    void successfulTcpConnectDoesNotSpendTlsBudgetOnTheExpiredTcpTimer() throws Exception {
        CountDownLatch accepted = new CountDownLatch(1), release = new CountDownLatch(1);
        CompletableFuture<Void> peerResult = new CompletableFuture<>();
        try (SSLServerSocket server = (SSLServerSocket) TlsTestMaterial.context(true, true)
                        .getServerSocketFactory()
                        .createServerSocket(0, 1, InetAddress.getLoopbackAddress());
                SmppClient client = new SmppClient(
                        options(Duration.ofMillis(100), Duration.ofSeconds(2)),
                        ExchangeConfig.defaults(),
                        ConnectionLifecycle.defaults()
                                .withTls(TlsConfig.client(
                                        TlsTestMaterial.context(false, true), "localhost", Duration.ofSeconds(2))))) {
            Thread peer = Thread.ofVirtual().start(() -> {
                try (RawPeer socket = RawPeer.accept(server)) {
                    accepted.countDown();
                    if (!release.await(2, TimeUnit.SECONDS))
                        throw new IllegalStateException("Fixture handshake release expired");
                    byte[] bind = socket.read();
                    socket.send(RawPeer.bindResponse(0x80000009L, 0, RawPeer.sequence(bind), 0x34));
                    peerResult.complete(null);
                } catch (Exception failure) {
                    peerResult.completeExceptionally(failure);
                }
            });
            try {
                var attempt = client.connect(TlsEndpointsTest.config(
                                (InetSocketAddress) server.getLocalSocketAddress(),
                                BindMode.TRANSCEIVER,
                                SmppVersion.V3_4))
                        .toCompletableFuture();
                assertTrue(accepted.await(2, TimeUnit.SECONDS));
                assertThrows(
                        TimeoutException.class,
                        () -> attempt.get(250, TimeUnit.MILLISECONDS),
                        "TCP is connected and the independent TLS budget is still live");
                release.countDown();
                assertEquals(
                        BindMode.TRANSCEIVER, attempt.get(2, TimeUnit.SECONDS).bindMode());
                peerResult.get(2, TimeUnit.SECONDS);
            } finally {
                release.countDown();
                peer.join(3000);
            }
        }
    }

    @Test
    void acceptedBindDeadlineIncludesWaitingForTlsAndReleasesTheSocket() throws Exception {
        SmppServer server = new SmppServer(
                new ServerConfig(
                        new InetSocketAddress("127.0.0.1", 0), Set.of(SmppVersion.V3_4), SmppVersion.V3_4, "mc", 1, 0),
                options(Duration.ofSeconds(3), Duration.ofMillis(150)),
                (bind, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                ignored -> {},
                null,
                ExchangeConfig.defaults(),
                ConnectionLifecycle.defaults()
                        .withTls(TlsConfig.server(TlsTestMaterial.context(true, true), Duration.ofSeconds(2))));
        try {
            var address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            try (Socket peer = new Socket()) {
                peer.connect(address, 2000);
                peer.setSoTimeout(1000);
                assertEquals(-1, peer.getInputStream().read());
            }
        } finally {
            server.close();
            assertTrue(server.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
        }
    }

    private static EndpointOptions options(Duration connect, Duration bind) {
        EndpointOptions defaults = EndpointOptions.defaults();
        return new EndpointOptions(
                2, 8, 65536, 2, 32, connect, bind, Duration.ofSeconds(2), Duration.ofSeconds(2), defaults.pduLimits());
    }
}
