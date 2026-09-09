package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import org.junit.jupiter.api.Test;

class OutbindConfigTest {
    @Test
    void gracefulShutdownBeforeStartPermanentlyStopsListenerAdmission() throws Exception {
        try (OutbindListener listener = OutbindLifecycleTest.listener(
                EndpointOptions.defaults(),
                (request, peer) -> java.util.concurrent.CompletableFuture.completedFuture(true),
                ignored -> {})) {
            listener.shutdown(java.time.Duration.ofSeconds(1))
                    .toCompletableFuture()
                    .get(2, java.util.concurrent.TimeUnit.SECONDS);
            assertThrows(
                    java.util.concurrent.ExecutionException.class,
                    () -> listener.start().toCompletableFuture().get(2, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    @Test
    void listenerPreflightsTheConfiguredOutgoingBindBeforeOpeningTheListener() {
        BindRequest invalidOutgoing = new BindRequest(BindMode.RECEIVER, "", "", "", 0x34, 255, 0, "");
        try (OutbindListener listener = new OutbindListener(
                new OutbindListenerConfig(new InetSocketAddress("127.0.0.1", 0), invalidOutgoing, false),
                EndpointOptions.defaults(),
                (request, peer) -> java.util.concurrent.CompletableFuture.completedFuture(true),
                ignored -> {},
                ExchangeConfig.defaults())) {
            java.util.concurrent.ExecutionException error = assertThrows(
                    java.util.concurrent.ExecutionException.class,
                    () -> listener.start().toCompletableFuture().get(2, java.util.concurrent.TimeUnit.SECONDS));
            org.junit.jupiter.api.Assertions.assertInstanceOf(IllegalArgumentException.class, error.getCause());
            assertEquals(0, listener.connectionCount());
        }
    }

    @Test
    void listenerRequiresExplicitResolvedAddressAndSupportedBindWithoutLeakingCredentials() {
        BindRequest bind = new BindRequest(BindMode.RECEIVER, "secret-id", "secretpw", "", 0x34, 0, 0, "");
        OutbindListenerConfig config =
                new OutbindListenerConfig(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), bind, true);
        assertEquals(bind, config.bind());
        assertFalse(config.toString().contains("secret"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OutbindListenerConfig(InetSocketAddress.createUnresolved("localhost", 2775), bind, true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OutbindListenerConfig(
                        config.listenAddress(), new BindRequest(BindMode.RECEIVER, "", "", "", 0x33, 0, 0, ""), false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OutbindListenerConfig(
                        config.listenAddress(),
                        new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x34, 0, 0, ""),
                        false));
        for (BindMode mode : BindMode.values())
            assertEquals(
                    mode,
                    new OutbindListenerConfig(
                                    config.listenAddress(), new BindRequest(mode, "", "", "", 0x50, 0, 0, ""), true)
                            .bind()
                            .mode());
        assertThrows(NullPointerException.class, () -> new OutbindListenerConfig(null, bind, true));
        assertThrows(NullPointerException.class, () -> new OutbindListenerConfig(config.listenAddress(), null, false));
    }

    @Test
    void connectorOwnsVersionSetAndValidatesBoundedAuthenticationPolicy() {
        HashSet<SmppVersion> versions = new HashSet<>(Set.of(SmppVersion.V3_4));
        OutbindConnectorConfig config = new OutbindConnectorConfig(versions, SmppVersion.V5_0, "mc-secret", 1, 0);
        versions.clear();
        assertEquals(Set.of(SmppVersion.V3_4), config.acceptedVersions());
        assertThrows(
                UnsupportedOperationException.class,
                () -> config.acceptedVersions().clear());
        assertFalse(config.toString().contains("mc-secret"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OutbindConnectorConfig(Set.of(), SmppVersion.V5_0, "mc", 1, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OutbindConnectorConfig(Set.of(SmppVersion.V5_0), SmppVersion.V3_4, "mc", 1, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OutbindConnectorConfig(Set.of(SmppVersion.V3_4), SmppVersion.V3_4, "mc", 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OutbindConnectorConfig(Set.of(SmppVersion.V3_4), SmppVersion.V3_4, "mc", 1, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new OutbindConnectorConfig(Set.of(SmppVersion.V3_4), SmppVersion.V3_4, "a".repeat(16), 1, 0));
    }
}
