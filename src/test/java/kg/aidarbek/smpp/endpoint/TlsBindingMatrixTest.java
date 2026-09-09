package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Outbind;
import kg.aidarbek.smpp.spi.TransportFailure;
import kg.aidarbek.smpp.transport.TlsConfig;
import kg.aidarbek.smpp.transport.TlsTestMaterial;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TlsBindingMatrixTest {
    @Test
    void untrustedReversedConnectionNeverDispatchesOutbindCredentials() throws Exception {
        AtomicInteger authentications = new AtomicInteger();
        OutbindListener listener = new OutbindListener(
                new OutbindListenerConfig(
                        new InetSocketAddress("127.0.0.1", 0), bind(SmppVersion.V3_4, BindMode.RECEIVER), true),
                EndpointOptions.defaults(),
                (request, peer) -> {
                    authentications.incrementAndGet();
                    return CompletableFuture.completedFuture(true);
                },
                ignored -> {},
                ExchangeConfig.defaults(),
                tls(false, true));
        OutbindConnector connector = new OutbindConnector(
                new OutbindConnectorConfig(Set.of(SmppVersion.V3_4), SmppVersion.V3_4, "mc", 1, 1),
                EndpointOptions.defaults(),
                (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                ExchangeConfig.defaults(),
                tls(true, false));
        try {
            var address = listener.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            ExecutionException failed = assertThrows(
                    ExecutionException.class,
                    () -> connector
                            .connectAttempt(address, outbind())
                            .result()
                            .toCompletableFuture()
                            .get(3, TimeUnit.SECONDS));
            assertEquals(TransportFailure.Kind.TLS_HANDSHAKE_FAILED, ((TransportFailure) failed.getCause()).kind());
            assertEquals(0, authentications.get());
        } finally {
            connector.close();
            listener.close();
            assertTrue(connector
                    .termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
            assertTrue(listener.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
        }
    }

    @ParameterizedTest
    @MethodSource("variants")
    void secureBindingEnquiriesAndEitherUnbindOrigin(
            SmppVersion version, BindMode mode, boolean reversed, boolean acceptorUnbind) throws Exception {
        CompletableFuture<BoundSession> accepted = new CompletableFuture<>();
        if (reversed) {
            OutbindListener listener = new OutbindListener(
                    new OutbindListenerConfig(new InetSocketAddress("127.0.0.1", 0), bind(version, mode), true),
                    EndpointOptions.defaults(),
                    (request, peer) -> CompletableFuture.completedFuture(true),
                    accepted::complete,
                    ExchangeConfig.defaults(),
                    tls(false, true));
            OutbindConnector connector = new OutbindConnector(
                    new OutbindConnectorConfig(Set.of(version), version, "mc", 1, 1),
                    EndpointOptions.defaults(),
                    (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                    ExchangeConfig.defaults(),
                    tls(true, true));
            try {
                var address = listener.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
                BoundSession connected = connector
                        .connectAttempt(address, outbind())
                        .result()
                        .toCompletableFuture()
                        .get(3, TimeUnit.SECONDS);
                exchange(connected, accepted.get(3, TimeUnit.SECONDS), mode, version, acceptorUnbind);
            } finally {
                connector.close();
                listener.close();
                assertTrue(connector
                        .termination()
                        .toCompletableFuture()
                        .get(3, TimeUnit.SECONDS)
                        .complete());
                assertTrue(listener.termination()
                        .toCompletableFuture()
                        .get(3, TimeUnit.SECONDS)
                        .complete());
            }
        } else {
            SmppServer server = new SmppServer(
                    new ServerConfig(new InetSocketAddress("127.0.0.1", 0), Set.of(version), version, "mc", 1, 1),
                    EndpointOptions.defaults(),
                    (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                    accepted::complete,
                    null,
                    ExchangeConfig.defaults(),
                    tls(false, true));
            SmppClient client = new SmppClient(EndpointOptions.defaults(), ExchangeConfig.defaults(), tls(true, true));
            try {
                var address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
                BoundSession connected = client.connect(new ClientConfig(address, bind(version, mode), true))
                        .toCompletableFuture()
                        .get(3, TimeUnit.SECONDS);
                exchange(connected, accepted.get(3, TimeUnit.SECONDS), mode, version, acceptorUnbind);
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
    }

    private static void exchange(
            BoundSession connected, BoundSession accepted, BindMode mode, SmppVersion version, boolean acceptorUnbind)
            throws Exception {
        assertEquals(mode, connected.bindMode());
        assertEquals(mode, accepted.bindMode());
        assertEquals(
                version,
                connected.negotiation().effectiveProfile().orElseThrow().version());
        assertEquals(
                0,
                connected
                        .enquireLink()
                        .result()
                        .toCompletableFuture()
                        .get(2, TimeUnit.SECONDS)
                        .commandStatus());
        assertEquals(
                0,
                accepted.enquireLink()
                        .result()
                        .toCompletableFuture()
                        .get(2, TimeUnit.SECONDS)
                        .commandStatus());
        assertEquals(
                0,
                (acceptorUnbind ? accepted : connected)
                        .unbind()
                        .result()
                        .toCompletableFuture()
                        .get(2, TimeUnit.SECONDS)
                        .commandStatus());
        connected.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
        accepted.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
    }

    private static Stream<Arguments> variants() {
        return Stream.of(SmppVersion.values())
                .flatMap(version -> Stream.of(BindMode.values())
                        .flatMap(mode -> Stream.of(false, true)
                                .filter(reversed ->
                                        !reversed || version == SmppVersion.V5_0 || mode == BindMode.RECEIVER)
                                .flatMap(reversed -> Stream.of(false, true)
                                        .map(acceptorUnbind ->
                                                Arguments.of(version, mode, reversed, acceptorUnbind)))));
    }

    private static BindRequest bind(SmppVersion version, BindMode mode) {
        return new BindRequest(mode, "demo", "demo", "", version.interfaceVersion(), 0, 0, "");
    }

    private static Outbind outbind() {
        return new Outbind("mc", "demo", new OptionalParameters(List.of()));
    }

    private static ConnectionLifecycle tls(boolean client, boolean trust) throws Exception {
        return ConnectionLifecycle.defaults()
                .withTls(
                        client
                                ? TlsConfig.client(
                                        TlsTestMaterial.context(false, trust), "localhost", Duration.ofSeconds(2))
                                : TlsConfig.server(TlsTestMaterial.context(true, trust), Duration.ofSeconds(2)));
    }
}
