package kg.aidarbek.simulator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import kg.aidarbek.smpp.endpoint.ConnectionLifecycle;
import kg.aidarbek.smpp.endpoint.KeepalivePolicy;
import kg.aidarbek.smpp.endpoint.ReconnectPolicy;
import kg.aidarbek.smpp.transport.TlsConfig;

/** Simulator-only lifecycle configuration; credential values never enter this value or reports. */
record LifecycleSettings(Map<String, String> values) {
    LifecycleSettings {
        Map<String, String> selected = new LinkedHashMap<>();
        for (String name : optionNames()) if (values.containsKey(name)) selected.put(name, values.get(name));
        values = Map.copyOf(selected);
        String tls = values.getOrDefault("tls", "off");
        if (!tls.equals("off") && !tls.equals("on")) throw new IllegalArgumentException("tls must be off or on");
        if (tls.equals("off") && values.keySet().stream().anyMatch(name -> name.startsWith("tls-")))
            throw new IllegalArgumentException("TLS settings require tls=on");
        bool(values, "tls-client-certificate");
        duration(values, "tls-handshake-timeout", "PT5S", false);
        duration(values, "keepalive-idle", "PT30S", false);
        duration(values, "keepalive-timeout", "PT5S", false);
        duration(values, "reconnect-delay", "PT1S", true);
        if (values.containsKey("keepalive-timeout") && !values.containsKey("keepalive-idle"))
            throw new IllegalArgumentException("keepalive-timeout requires keepalive-idle");
        if (values.containsKey("reconnect-delay") && !values.containsKey("reconnect-attempts"))
            throw new IllegalArgumentException("reconnect-delay requires reconnect-attempts");
        if (values.containsKey("reconnect-attempts")) {
            int attempts = Integer.parseInt(values.get("reconnect-attempts"));
            if (attempts < 1 || attempts > 1024)
                throw new IllegalArgumentException("Reconnect attempts must be 1..1024");
        }
        for (String name : Set.of("tls-key-password-env", "tls-trust-password-env")) {
            if (values.containsKey(name) && !values.get(name).matches("[A-Za-z_][A-Za-z0-9_]{0,127}"))
                throw new IllegalArgumentException("TLS password settings must name environment variables");
        }
    }

    static Set<String> optionNames() {
        return Set.of(
                "tls",
                "tls-key-store",
                "tls-trust-store",
                "tls-key-password-env",
                "tls-trust-password-env",
                "tls-peer-name",
                "tls-handshake-timeout",
                "tls-client-certificate",
                "keepalive-idle",
                "keepalive-timeout",
                "reconnect-attempts",
                "reconnect-delay");
    }

    static LifecycleSettings parse(Map<String, String> values) {
        return new LifecycleSettings(values);
    }

    ConnectionLifecycle create(boolean tlsClientMode) {
        return create(tlsClientMode, System::getenv);
    }

    ConnectionLifecycle create(boolean tlsClientMode, Function<String, String> environment) {
        ConnectionLifecycle lifecycle = ConnectionLifecycle.defaults();
        if (values.containsKey("keepalive-idle"))
            lifecycle = lifecycle.withKeepalive(new KeepalivePolicy(
                    duration(values, "keepalive-idle", "PT30S", false),
                    duration(values, "keepalive-timeout", "PT5S", false)));
        if (!values.getOrDefault("tls", "off").equals("on")) return lifecycle;
        if (tlsClientMode && !values.containsKey("tls-peer-name"))
            throw new IllegalArgumentException("TLS clients require an explicit tls-peer-name certificate identity");
        if (!tlsClientMode && !values.containsKey("tls-key-store"))
            throw new IllegalArgumentException("TLS servers require a PKCS12 tls-key-store");
        if (tlsClientMode && bool(values, "tls-client-certificate"))
            throw new IllegalArgumentException("tls-client-certificate is a TLS server requirement");
        SSLContext context = context(environment);
        Duration timeout = duration(values, "tls-handshake-timeout", "PT5S", false);
        return lifecycle.withTls(
                tlsClientMode
                        ? TlsConfig.client(context, values.get("tls-peer-name"), timeout)
                        : TlsConfig.server(context, timeout, bool(values, "tls-client-certificate")));
    }

    private SSLContext context(Function<String, String> environment) {
        char[] keyPassword = new char[0], trustPassword = new char[0];
        try {
            KeyManager[] keys = null;
            TrustManager[] trust = null;
            if (values.containsKey("tls-key-store")) {
                keyPassword = password(environment, "tls-key-password-env", "SMPP_TLS_KEYSTORE_PASSWORD");
                KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                factory.init(store(values.get("tls-key-store"), keyPassword), keyPassword);
                keys = factory.getKeyManagers();
            }
            if (values.containsKey("tls-trust-store")) {
                trustPassword = password(environment, "tls-trust-password-env", "SMPP_TLS_TRUSTSTORE_PASSWORD");
                TrustManagerFactory factory =
                        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                factory.init(store(values.get("tls-trust-store"), trustPassword));
                trust = factory.getTrustManagers();
            }
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keys, trust, null);
            return context;
        } catch (GeneralSecurityException | IOException failure) {
            throw new IllegalArgumentException("Unable to initialize configured TLS key or trust material", failure);
        } finally {
            Arrays.fill(keyPassword, '\0');
            Arrays.fill(trustPassword, '\0');
        }
    }

    private char[] password(Function<String, String> environment, String option, String defaultName) {
        String name = values.getOrDefault(option, defaultName);
        String secret = environment.apply(name);
        if (secret == null || secret.isEmpty())
            throw new IllegalArgumentException("Missing TLS password environment variable: " + name);
        return secret.toCharArray();
    }

    private static KeyStore store(String location, char[] password) throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(Path.of(location))) {
            store.load(input, password);
        }
        return store;
    }

    Map<String, Object> nonSecretReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("tls", values.getOrDefault("tls", "off").equals("on"));
        report.put("tlsKeyConfigured", values.containsKey("tls-key-store"));
        report.put("tlsTrustConfigured", values.containsKey("tls-trust-store"));
        report.put("tlsKeyStore", values.getOrDefault("tls-key-store", ""));
        report.put("tlsTrustStore", values.getOrDefault("tls-trust-store", ""));
        report.put(
                "tlsKeyPasswordEnv",
                values.containsKey("tls-key-store")
                        ? values.getOrDefault("tls-key-password-env", "SMPP_TLS_KEYSTORE_PASSWORD")
                        : "");
        report.put(
                "tlsTrustPasswordEnv",
                values.containsKey("tls-trust-store")
                        ? values.getOrDefault("tls-trust-password-env", "SMPP_TLS_TRUSTSTORE_PASSWORD")
                        : "");
        report.put("tlsPeerName", values.getOrDefault("tls-peer-name", ""));
        report.put("tlsClientCertificate", bool(values, "tls-client-certificate"));
        report.put(
                "tlsHandshakeTimeout",
                duration(values, "tls-handshake-timeout", "PT5S", false).toString());
        report.put("keepaliveIdle", values.getOrDefault("keepalive-idle", "disabled"));
        report.put(
                "keepaliveTimeout",
                values.containsKey("keepalive-idle")
                        ? duration(values, "keepalive-timeout", "PT5S", false).toString()
                        : "disabled");
        report.put(
                "reconnectAttempts",
                reconnectPolicy().map(ReconnectPolicy::maximumAttempts).orElse(0));
        report.put(
                "reconnectDelay",
                reconnectPolicy().map(policy -> policy.retryDelay().toString()).orElse("disabled"));
        return Map.copyOf(report);
    }

    Optional<ReconnectPolicy> reconnectPolicy() {
        return values.containsKey("reconnect-attempts")
                ? Optional.of(new ReconnectPolicy(
                        Integer.parseInt(values.get("reconnect-attempts")),
                        duration(values, "reconnect-delay", "PT1S", true)))
                : Optional.empty();
    }

    private static boolean bool(Map<String, String> values, String name) {
        String value = values.getOrDefault(name, "false");
        if (!value.equals("true") && !value.equals("false"))
            throw new IllegalArgumentException(name + " must be true or false");
        return Boolean.parseBoolean(value);
    }

    private static Duration duration(Map<String, String> values, String name, String fallback, boolean zero) {
        Duration value = Duration.parse(values.getOrDefault(name, fallback));
        if (value.isNegative() || (!zero && value.isZero()) || value.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException(name + " must be a finite duration within one hour");
        return value;
    }
}
