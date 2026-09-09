package kg.aidarbek.smpp.endpoint;

import java.net.InetSocketAddress;
import java.util.Objects;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;

/**
 * Immutable ESME listener address and the configured bind to send after authenticated outbind.
 * @param listenAddress resolved local TCP address; port zero requests an ephemeral listener
 * @param bind fixed outgoing bind credentials; 3.4 outbind requires RECEIVER, while 5.0 permits all modes
 * @param requireAdvertisement whether missing MC version advertisement fails the subsequent bind
 */
public record OutbindListenerConfig(InetSocketAddress listenAddress, BindRequest bind, boolean requireAdvertisement) {
    /** Validates explicit addressing/version/mode without opening a socket or resolving DNS. */
    public OutbindListenerConfig {
        Objects.requireNonNull(listenAddress, "listenAddress");
        Objects.requireNonNull(bind, "bind");
        if (listenAddress.isUnresolved() || (bind.interfaceVersion() != 0x34 && bind.interfaceVersion() != 0x50))
            throw new IllegalArgumentException(
                    "Outbind listener requires resolved addressing and an explicit supported version");
        if (bind.interfaceVersion() == 0x34 && bind.mode() != BindMode.RECEIVER)
            throw new IllegalArgumentException("SMPP 3.4 outbind requires a receiver bind");
    }

    @Override
    public String toString() {
        return "OutbindListenerConfig[listenAddress=" + listenAddress + ", mode=" + bind.mode() + ", interfaceVersion="
                + bind.interfaceVersion() + ", requireAdvertisement=" + requireAdvertisement + "]";
    }
}
