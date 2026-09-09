package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.spi.TransportFailure;
import kg.aidarbek.smpp.transport.TlsConfig;
import kg.aidarbek.smpp.transport.TlsTestMaterial;
import org.junit.jupiter.api.Test;

class TlsEndpointsTest {
    @Test
    void untrustedTlsNeverExposesBindCredentialsToAuthentication() throws Exception {
        AtomicInteger authentications = new AtomicInteger();
        SmppServer server = server(authentications);
        SmppClient client = new SmppClient(
                EndpointOptions.defaults(),
                ExchangeConfig.defaults(),
                ConnectionLifecycle.defaults()
                        .withTls(TlsConfig.client(
                                TlsTestMaterial.context(false, false), "localhost", Duration.ofSeconds(2))));
        try {
            var address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            ExecutionException failed = assertThrows(
                    ExecutionException.class,
                    () -> client.connect(config(address, BindMode.TRANSCEIVER, SmppVersion.V3_4))
                            .toCompletableFuture()
                            .get(3, TimeUnit.SECONDS));
            assertEquals(TransportFailure.Kind.TLS_HANDSHAKE_FAILED, ((TransportFailure) failed.getCause()).kind());
            assertEquals(0, authentications.get());
        } finally {
            client.close();
            server.close();
            assertTrue(client.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
            assertTrue(server.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
        }
    }

    @Test
    void plaintextBindCannotReachTlsServerAuthentication() throws Exception {
        AtomicInteger authentications = new AtomicInteger();
        SmppServer server = server(authentications);
        try {
            try (RawPeer peer =
                    RawPeer.connect(server.start().toCompletableFuture().get(2, TimeUnit.SECONDS))) {
                peer.send(RawPeer.bind(9, 1, 0x34));
                boolean closed = false;
                for (int index = 0; index < 32 && !closed; index++) closed = peer.closedByEndpoint();
                assertTrue(closed, "TLS may send a finite fatal alert before physical closure");
                assertEquals(0, authentications.get());
            }
        } finally {
            server.close();
            assertTrue(server.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
        }
    }

    private static SmppServer server(AtomicInteger authentications) throws Exception {
        return new SmppServer(
                new ServerConfig(
                        new InetSocketAddress("127.0.0.1", 0),
                        Set.of(SmppVersion.V3_4, SmppVersion.V5_0),
                        SmppVersion.V5_0,
                        "tls-test",
                        2,
                        2),
                EndpointOptions.defaults(),
                (bind, peer) -> {
                    authentications.incrementAndGet();
                    return CompletableFuture.completedFuture(BindDecision.ACCEPT);
                },
                ignored -> {},
                null,
                ExchangeConfig.defaults(),
                ConnectionLifecycle.defaults()
                        .withTls(TlsConfig.server(TlsTestMaterial.context(true, true), Duration.ofSeconds(2))));
    }

    static ClientConfig config(InetSocketAddress address, BindMode mode, SmppVersion version) {
        return new ClientConfig(
                address, new BindRequest(mode, "demo", "demo", "", version.interfaceVersion(), 0, 0, ""), true);
    }
}
