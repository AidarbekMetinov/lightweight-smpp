package kg.aidarbek.smpp.endpoint;

import java.net.InetSocketAddress;
import java.util.Objects;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindRequest;

/**
 * One explicit connection and bind attempt, safe to share as an immutable value.
 *
 * @param remoteAddress resolved remote TCP address
 * @param bind credentials and requested bind mode/version
 * @param requireAdvertisement whether missing server version advertisement fails binding
 */
public record ClientConfig(InetSocketAddress remoteAddress, BindRequest bind, boolean requireAdvertisement) {
    /**
     * Requires resolved addressing and an explicitly supported outgoing version.
     *
     * @throws IllegalArgumentException for unresolved addresses, zero port or an unknown version
     * @throws NullPointerException if a field is null
     */
    public ClientConfig {
        Objects.requireNonNull(remoteAddress, "remoteAddress");
        Objects.requireNonNull(bind, "bind");
        if (remoteAddress.isUnresolved() || remoteAddress.getPort() == 0) {
            throw new IllegalArgumentException("Client requires a resolved address and a nonzero port");
        }
        if (bind.interfaceVersion() != 0x34 && bind.interfaceVersion() != 0x50) {
            throw new IllegalArgumentException("Client requested version must be SMPP 3.4 or 5.0");
        }
    }

    /**
     * Returns the validated explicit client requirement, independent of peer advertisement.
     *
     * @return requested protocol version
     */
    public SmppVersion requestedVersion() {
        return bind.interfaceVersion() == 0x34 ? SmppVersion.V3_4 : SmppVersion.V5_0;
    }
}
