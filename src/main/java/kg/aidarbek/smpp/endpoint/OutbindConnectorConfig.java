package kg.aidarbek.smpp.endpoint;

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
 * Immutable MC policy shared by explicitly requested outbind connections.
 * @param acceptedVersions supported bind requirements, copied on construction
 * @param advertisedVersion implemented MC version advertised on successful binding
 * @param systemId MC identity returned in bind responses
 * @param authenticationConcurrency maximum physically active bind authentication invocations/stages
 * @param authenticationQueue maximum queued bind authentication invocations
 */
public record OutbindConnectorConfig(
        Set<SmppVersion> acceptedVersions,
        SmppVersion advertisedVersion,
        String systemId,
        int authenticationConcurrency,
        int authenticationQueue) {
    /** Copies version policy and validates identifier/physical authentication bounds. */
    public OutbindConnectorConfig {
        acceptedVersions = Set.copyOf(acceptedVersions);
        Objects.requireNonNull(advertisedVersion, "advertisedVersion");
        if (authenticationConcurrency < 1 || authenticationQueue < 0)
            throw new IllegalArgumentException("Outbind connector requires bounded authentication");
        VersionNegotiation.forMessageCenter(advertisedVersion.interfaceVersion(), acceptedVersions, advertisedVersion);
        new BindResponse(BindMode.RECEIVER, Optional.of(systemId), new OptionalParameters(List.of()));
    }

    @Override
    public String toString() {
        return "OutbindConnectorConfig[acceptedVersions=" + acceptedVersions + ", advertisedVersion="
                + advertisedVersion + ", authenticationConcurrency=" + authenticationConcurrency
                + ", authenticationQueue=" + authenticationQueue + "]";
    }
}
