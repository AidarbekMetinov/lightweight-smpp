package kg.aidarbek.smpp.endpoint;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindResponse;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.session.VersionNegotiation;

/**
 * Message-center listener, version and authentication admission configuration.
 *
 * @param listenAddress resolved local TCP address; port zero requests an ephemeral port
 * @param acceptedVersions explicit permitted client versions, copied on construction
 * @param advertisedVersion server's implemented version advertised on successful binding
 * @param systemId server identifier returned in successful bind responses
 * @param authenticationConcurrency maximum in-flight authentication invocations/stages
 * @param authenticationQueue maximum queued authentication requests
 */
public record ServerConfig(
        InetSocketAddress listenAddress,
        Set<SmppVersion> acceptedVersions,
        SmppVersion advertisedVersion,
        String systemId,
        int authenticationConcurrency,
        int authenticationQueue) {
    /**
     * Copies the accepted set and validates advertisement, identifier and authentication bounds.
     *
     * @throws IllegalArgumentException for unresolved addressing or invalid policy/capacities
     * @throws NullPointerException if a field or accepted version is null
     */
    public ServerConfig {
        Objects.requireNonNull(listenAddress, "listenAddress");
        acceptedVersions = Set.copyOf(acceptedVersions);
        Objects.requireNonNull(advertisedVersion, "advertisedVersion");
        if (listenAddress.isUnresolved() || authenticationConcurrency < 1 || authenticationQueue < 0) {
            throw new IllegalArgumentException("Server requires resolved addressing and bounded authentication");
        }
        VersionNegotiation.forMessageCenter(advertisedVersion.interfaceVersion(), acceptedVersions, advertisedVersion);
        new BindResponse(BindMode.TRANSCEIVER, Optional.of(systemId), new OptionalParameters(List.of()));
    }

    @Override
    public String toString() {
        return "ServerConfig[listenAddress=" + listenAddress + ", acceptedVersions=" + acceptedVersions
                + ", advertisedVersion=" + advertisedVersion + "]";
    }
}
