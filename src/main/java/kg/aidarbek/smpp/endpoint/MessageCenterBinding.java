package kg.aidarbek.smpp.endpoint;

import java.util.Set;
import kg.aidarbek.smpp.profile.SmppVersion;

/** Coordinator input shared by listening and explicitly connecting MC owners, without socket configuration. */
record MessageCenterBinding(Set<SmppVersion> acceptedVersions, SmppVersion advertisedVersion, String systemId) {
    MessageCenterBinding {
        acceptedVersions = Set.copyOf(acceptedVersions);
    }

    static MessageCenterBinding from(ServerConfig config) {
        return new MessageCenterBinding(config.acceptedVersions(), config.advertisedVersion(), config.systemId());
    }

    static MessageCenterBinding from(OutbindConnectorConfig config) {
        return new MessageCenterBinding(config.acceptedVersions(), config.advertisedVersion(), config.systemId());
    }
}
