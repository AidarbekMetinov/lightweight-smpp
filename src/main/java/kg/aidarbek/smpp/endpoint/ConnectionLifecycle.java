package kg.aidarbek.smpp.endpoint;

import java.util.Objects;
import java.util.Optional;
import kg.aidarbek.smpp.transport.TlsConfig;

/** Opt-in transport security and automatic idle-link monitoring; old constructors use plain TCP.
 * @param tls explicit TLS role, trust, identity and handshake settings, or empty for plain TCP
 * @param keepalive optional bounded idle enquiry policy */
public record ConnectionLifecycle(Optional<TlsConfig> tls, Optional<KeepalivePolicy> keepalive) {
    /** Rejects missing optional containers. */
    public ConnectionLifecycle {
        Objects.requireNonNull(tls, "tls");
        Objects.requireNonNull(keepalive, "keepalive");
    }

    void validateRole(boolean tlsClient, EndpointOptions options) {
        startupNanos(Objects.requireNonNull(options, "options"), tlsClient);
        if (tls.isPresent() && tls.orElseThrow().clientMode() != tlsClient)
            throw new IllegalArgumentException("TLS role must follow TCP connection origin");
    }

    long startupNanos(EndpointOptions options, boolean outgoing) {
        return outgoing
                ? EndpointOptions.durationNanos(options.connectTimeout())
                        + tls.map(value -> EndpointOptions.durationNanos(value.handshakeTimeout()))
                                .orElse(0L)
                : EndpointOptions.durationNanos(options.bindTimeout());
    }
    /** Returns plain TCP with automatic enquiries disabled.
     * @return immutable defaults */
    public static ConnectionLifecycle defaults() {
        return new ConnectionLifecycle(Optional.empty(), Optional.empty());
    }
    /** Copies this policy with explicit TLS settings.
     * @param value non-null TLS settings
     * @return independent policy */
    public ConnectionLifecycle withTls(TlsConfig value) {
        return new ConnectionLifecycle(Optional.of(value), keepalive);
    }
    /** Copies this policy with automatic enquiries enabled.
     * @param value non-null idle enquiry policy
     * @return independent policy */
    public ConnectionLifecycle withKeepalive(KeepalivePolicy value) {
        return new ConnectionLifecycle(tls, Optional.of(value));
    }
}
