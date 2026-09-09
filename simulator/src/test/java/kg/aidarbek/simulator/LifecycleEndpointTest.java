package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.endpoint.BindDecision;
import kg.aidarbek.smpp.endpoint.ClientConfig;
import kg.aidarbek.smpp.endpoint.EndpointHandlers;
import kg.aidarbek.smpp.endpoint.EndpointOptions;
import kg.aidarbek.smpp.endpoint.ExchangeConfig;
import kg.aidarbek.smpp.endpoint.ServerConfig;
import kg.aidarbek.smpp.endpoint.SmppClient;
import kg.aidarbek.smpp.endpoint.SmppServer;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Timeout(20)
class LifecycleEndpointTest {
    @ParameterizedTest
    @CsvSource({"3.4,true", "5.0,true", "3.4,false", "5.0,false"})
    void bothSimulatorRolesUseRealTlsAgainstAnExplicitlyEncryptedPeer(String profile, boolean toolClient)
            throws Exception {
        var version = profile.equals("3.4") ? SmppVersion.V3_4 : SmppVersion.V5_0;
        String store = Path.of(getClass()
                        .getResource("/lifecycle/development-only.p12")
                        .toURI())
                .toString();
        var serverPolicy = LifecycleSettings.parse(Map.of("tls", "on", "tls-key-store", store))
                .create(false, name -> "development-only");
        var clientPolicy = LifecycleSettings.parse(Map.of(
                        "tls",
                        "on",
                        "tls-trust-store",
                        store,
                        "tls-peer-name",
                        "localhost",
                        "keepalive-idle",
                        "PT0.05S"))
                .create(true, name -> "development-only");
        if (toolClient) {
            try (var peer = new SmppServer(
                    new ServerConfig(new InetSocketAddress("127.0.0.1", 0), Set.of(version), version, "sim", 1, 1),
                    EndpointOptions.defaults(),
                    (request, context) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                    session -> {},
                    null,
                    ExchangeConfig.defaults(),
                    serverPolicy)) {
                var address = peer.start().toCompletableFuture().get(3, TimeUnit.SECONDS);
                var config = SimulatorArguments.parse(
                        "client",
                        "--version=" + profile,
                        "--port=" + address.getPort(),
                        "--revision=0123456789012345678901234567890123456789");
                try (var endpoint = new SimulatorEndpoint(
                        config, EndpointHandlers.empty(), "sim", "sim", event -> {}, () -> {}, clientPolicy)) {
                    endpoint.start();
                    assertEquals(
                            0,
                            endpoint.session(0)
                                    .enquireLink()
                                    .result()
                                    .toCompletableFuture()
                                    .get(2, TimeUnit.SECONDS)
                                    .commandStatus());
                    assertTrue(endpoint.shutdown());
                }
                assertTrue(peer.shutdown(Duration.ofSeconds(2))
                        .toCompletableFuture()
                        .get(3, TimeUnit.SECONDS)
                        .complete());
            }
        } else {
            var ready = new CompletableFuture<Integer>();
            var bound = new CompletableFuture<Void>();
            var config = SimulatorArguments.parse(
                    "server",
                    "--version=" + profile,
                    "--port=0",
                    "--revision=0123456789012345678901234567890123456789");
            try (var endpoint = new SimulatorEndpoint(
                            config,
                            EndpointHandlers.empty(),
                            "sim",
                            "sim",
                            event -> ready.complete(Integer.parseInt(event.substring(11))),
                            () -> {},
                            serverPolicy);
                    var peer = new SmppClient(EndpointOptions.defaults(), ExchangeConfig.defaults(), clientPolicy)) {
                Thread owner = Thread.ofVirtual().start(() -> {
                    try {
                        endpoint.start();
                        bound.complete(null);
                    } catch (Exception failure) {
                        ready.completeExceptionally(failure);
                        bound.completeExceptionally(failure);
                    }
                });
                try {
                    var session = peer.connect(new ClientConfig(
                                    new InetSocketAddress("127.0.0.1", ready.get(3, TimeUnit.SECONDS)),
                                    new BindRequest(
                                            BindMode.TRANSCEIVER,
                                            "sim",
                                            "sim",
                                            "",
                                            version.interfaceVersion(),
                                            0,
                                            0,
                                            ""),
                                    true))
                            .toCompletableFuture()
                            .get(3, TimeUnit.SECONDS);
                    bound.get(3, TimeUnit.SECONDS);
                    assertEquals(
                            0,
                            session.enquireLink()
                                    .result()
                                    .toCompletableFuture()
                                    .get(2, TimeUnit.SECONDS)
                                    .commandStatus());
                    assertTrue(peer.shutdown(Duration.ofSeconds(2))
                            .toCompletableFuture()
                            .get(3, TimeUnit.SECONDS)
                            .complete());
                    assertTrue(endpoint.shutdown());
                } finally {
                    if (owner.isAlive()) owner.interrupt();
                    owner.join(3000);
                    assertFalse(owner.isAlive());
                }
            }
        }
    }
}
