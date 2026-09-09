package kg.aidarbek.smpp.transport;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.Objects;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

/** Explicit transport TLS identity, trust and handshake policy.
 * The policy retains a caller-owned, initialized {@link SSLContext}; it does not copy key or trust
 * managers. The caller owns credential/trust selection and must not reinitialize the context or
 * mutate its managers concurrently with transport use. Immutable policy fields do not make that
 * external cryptographic state immutable. */
public final class TlsConfig {
    private final SSLContext context;
    private final String peerName;
    private final Duration handshakeTimeout;
    private final boolean clientCertificate;

    private TlsConfig(SSLContext context, String peerName, Duration handshakeTimeout, boolean clientCertificate) {
        this.context = Objects.requireNonNull(context, "context");
        this.peerName = peerName;
        this.handshakeTimeout = Objects.requireNonNull(handshakeTimeout, "handshakeTimeout");
        this.clientCertificate = clientCertificate;
        try {
            if (handshakeTimeout.toNanos() <= 0)
                throw new IllegalArgumentException("TLS handshake timeout must be positive");
            context.getSocketFactory();
        } catch (ArithmeticException | IllegalStateException invalid) {
            throw new IllegalArgumentException(
                    "TLS requires an initialized context and a representable positive deadline", invalid);
        }
        if (peerName != null
                && (peerName.isBlank()
                        || peerName.length() > 253
                        || peerName.chars()
                                .anyMatch(value ->
                                        Character.isWhitespace(value) || value == '/' || value == '\\' || value == 0)))
            throw new IllegalArgumentException("TLS peer identity must be one certificate hostname or IP literal");
    }
    /** Creates TLS client policy with mandatory peer identity verification.
     * @param context initialized caller-supplied trust/key configuration
     * @param expectedPeerName certificate DNS name or IP identity, independent of resolved TCP address
     * @param handshakeTimeout positive handshake bound
     * @return immutable policy */
    public static TlsConfig client(SSLContext context, String expectedPeerName, Duration handshakeTimeout) {
        return new TlsConfig(
                context, Objects.requireNonNull(expectedPeerName, "expectedPeerName"), handshakeTimeout, false);
    }
    /** Creates TLS server policy without requiring a client certificate.
     * @param context initialized caller-supplied key/trust configuration
     * @param handshakeTimeout positive handshake bound
     * @return immutable policy */
    public static TlsConfig server(SSLContext context, Duration handshakeTimeout) {
        return server(context, handshakeTimeout, false);
    }
    /** Creates TLS server policy with explicit mutual authentication.
     * @param context initialized caller-supplied key/trust configuration
     * @param handshakeTimeout positive handshake bound
     * @param requireClientCertificate whether the TLS client must present a trusted certificate
     * @return immutable policy */
    public static TlsConfig server(SSLContext context, Duration handshakeTimeout, boolean requireClientCertificate) {
        return new TlsConfig(context, null, handshakeTimeout, requireClientCertificate);
    }

    SSLSocket layer(Socket socket) throws IOException {
        return (SSLSocket) context.getSocketFactory().createSocket(socket, peerName, socket.getPort(), true);
    }

    void configure(SSLSocket secured) {
        secured.setUseClientMode(clientMode());
        secured.setEnabledProtocols(new String[] {"TLSv1.3", "TLSv1.2"});
        secured.setNeedClientAuth(clientCertificate);
        if (clientMode()) {
            var parameters = secured.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            secured.setSSLParameters(parameters);
        }
    }
    /** Returns whether this is the TLS initiating role, independent of SMPP role.
     * @return true for TLS clients */
    public boolean clientMode() {
        return peerName != null;
    }
    /** Returns the configured total handshake bound.
     * @return positive duration */
    public Duration handshakeTimeout() {
        return handshakeTimeout;
    }
}
