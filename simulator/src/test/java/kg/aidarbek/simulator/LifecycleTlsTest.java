package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.endpoint.BindDecision;
import kg.aidarbek.smpp.endpoint.ClientConfig;
import kg.aidarbek.smpp.endpoint.EndpointOptions;
import kg.aidarbek.smpp.endpoint.ExchangeConfig;
import kg.aidarbek.smpp.endpoint.ServerConfig;
import kg.aidarbek.smpp.endpoint.SmppClient;
import kg.aidarbek.smpp.endpoint.SmppServer;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LifecycleTlsTest {
    @ParameterizedTest
    @EnumSource(SmppVersion.class)
    void adapterLoadsExplicitTrustAndIdentityForRealSecureEndpointTraffic(SmppVersion version) throws Exception {
        String store = fixture();
        LifecycleSettings serverPolicy = LifecycleSettings.parse(Map.of("tls", "on", "tls-key-store", store));
        LifecycleSettings clientPolicy = LifecycleSettings.parse(
                Map.of("tls", "on", "tls-trust-store", store, "tls-peer-name", "localhost", "keepalive-idle", "PT1S"));
        EndpointOptions options = EndpointOptions.defaults();
        SmppServer server = new SmppServer(
                new ServerConfig(new InetSocketAddress("127.0.0.1", 0), Set.of(version), version, "demo", 1, 1),
                options,
                (request, context) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                session -> {},
                null,
                ExchangeConfig.defaults(),
                serverPolicy.create(false, name -> "development-only"));
        SmppClient client = new SmppClient(
                options, ExchangeConfig.defaults(), clientPolicy.create(true, name -> "development-only"));
        try {
            InetSocketAddress address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            var session = client.connect(new ClientConfig(
                            address,
                            new BindRequest(
                                    BindMode.TRANSCEIVER, "demo", "demo", "", version.interfaceVersion(), 0, 0, ""),
                            true))
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS);
            assertEquals(
                    0,
                    session.enquireLink()
                            .result()
                            .toCompletableFuture()
                            .get(2, TimeUnit.SECONDS)
                            .commandStatus());
            assertEquals(
                    0,
                    session.unbind()
                            .result()
                            .toCompletableFuture()
                            .get(2, TimeUnit.SECONDS)
                            .commandStatus());
            session.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertFalse(serverPolicy.nonSecretReport().containsValue("development-only"));
            assertEquals(store, serverPolicy.nonSecretReport().get("tlsKeyStore"));
            assertFalse(clientPolicy.toString().contains("SMPP_TLS_TRUSTSTORE_PASSWORD"));
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
    void missingOrIncorrectSecretRejectsContextCreationWithoutLoggingItsValue() throws Exception {
        LifecycleSettings settings = LifecycleSettings.parse(
                Map.of("tls", "on", "tls-key-store", fixture(), "tls-key-password-env", "DEV_KEY_PASSWORD"));
        assertThrows(IllegalArgumentException.class, () -> settings.create(false, name -> null));
        IllegalArgumentException rejected = assertThrows(
                IllegalArgumentException.class, () -> settings.create(false, name -> "incorrect-fixture-password"));
        assertFalse(rejected.toString().contains("incorrect-fixture-password"));
        assertEquals("DEV_KEY_PASSWORD", settings.nonSecretReport().get("tlsKeyPasswordEnv"));
        assertTrue(settings.create(false, name -> "development-only").tls().isPresent());
    }

    private static String fixture() throws Exception {
        return Path.of(LifecycleTlsTest.class
                        .getResource("/lifecycle/development-only.p12")
                        .toURI())
                .toString();
    }
}
