package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LifecycleSettingsTest {
    @Test
    void reportsRetainExactNonsecretMaterialLocationsAndEnvironmentReferences() {
        LifecycleSettings settings = LifecycleSettings.parse(Map.of(
                "tls",
                "on",
                "tls-key-store",
                "/fixture/identity.p12",
                "tls-trust-store",
                "/fixture/trust.p12",
                "tls-key-password-env",
                "FIXTURE_KEY_PASSWORD"));
        assertEquals("/fixture/identity.p12", settings.nonSecretReport().get("tlsKeyStore"));
        assertEquals("/fixture/trust.p12", settings.nonSecretReport().get("tlsTrustStore"));
        assertEquals("FIXTURE_KEY_PASSWORD", settings.nonSecretReport().get("tlsKeyPasswordEnv"));
        assertEquals("SMPP_TLS_TRUSTSTORE_PASSWORD", settings.nonSecretReport().get("tlsTrustPasswordEnv"));
        assertEquals("", LifecycleSettings.parse(Map.of()).nonSecretReport().get("tlsKeyPasswordEnv"));
    }

    @Test
    void explicitLifecycleSettingsCreateFinitePoliciesWithoutPersistingSecrets() {
        LifecycleSettings settings = LifecycleSettings.parse(Map.of(
                "keepalive-idle",
                "PT2S",
                "keepalive-timeout",
                "PT1S",
                "reconnect-attempts",
                "3",
                "reconnect-delay",
                "PT0.1S",
                "unrelated-option",
                "not-retained"));
        assertEquals(
                Duration.ofSeconds(2),
                settings.create(true).keepalive().orElseThrow().idleInterval());
        assertEquals(
                Duration.ofSeconds(1),
                settings.create(true).keepalive().orElseThrow().responseTimeout());
        assertEquals(3, settings.reconnectPolicy().orElseThrow().maximumAttempts());
        assertEquals(
                Duration.ofMillis(100), settings.reconnectPolicy().orElseThrow().retryDelay());
        assertFalse(settings.toString().contains("not-retained"));
        assertEquals(false, settings.nonSecretReport().get("tls"));
        assertTrue(LifecycleSettings.parse(Map.of()).create(true).tls().isEmpty());
    }

    @Test
    void contradictoryOrUnboundedInputsFailBeforeCreatingWorkersOrReadingKeys() {
        for (Map<String, String> invalid : java.util.List.of(
                Map.of("tls", "maybe"),
                Map.of("tls", "off", "tls-key-store", "unused"),
                Map.of("tls", "on", "tls-client-certificate", "yes"),
                Map.of("keepalive-timeout", "PT1S"),
                Map.of("keepalive-idle", "PT0S"),
                Map.of("reconnect-delay", "PT1S"),
                Map.of("reconnect-attempts", "0"),
                Map.of("reconnect-attempts", "2048"),
                Map.of("keepalive-idle", "PT24H"),
                Map.of("tls", "on", "tls-key-password-env", "not an env name"))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> LifecycleSettings.parse(invalid),
                    invalid.keySet().toString());
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> LifecycleSettings.parse(Map.of("tls", "on")).create(true));
        assertThrows(
                IllegalArgumentException.class,
                () -> LifecycleSettings.parse(Map.of("tls", "on")).create(false));
    }
}
