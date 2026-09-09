package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import org.junit.jupiter.api.Test;

class EndpointValuesTest {
    @Test
    void authenticationDecisionsPreserveUnsignedStatusesAndRejectUnrepresentableValues() {
        assertEquals(0, BindDecision.ACCEPT.commandStatus());
        assertEquals(0xffff_ffffL, new BindDecision(0xffff_ffffL).commandStatus());
        assertThrows(IllegalArgumentException.class, () -> new BindDecision(-1));
        assertThrows(IllegalArgumentException.class, () -> new BindDecision(0x1_0000_0000L));
    }

    @Test
    void configurationsRejectUnboundedWorkAndPreserveIndependentVersionPolicy() {
        EndpointOptions defaults = EndpointOptions.defaults();
        assertThrows(
                IllegalArgumentException.class,
                () -> new EndpointOptions(
                        0,
                        1,
                        16,
                        1,
                        1,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        defaults.pduLimits()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EndpointOptions(
                        1,
                        1,
                        16,
                        1,
                        1,
                        Duration.ZERO,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        defaults.pduLimits()));
        BindRequest bind = new BindRequest(BindMode.TRANSCEIVER, "private-id", "secret", "", 0x34, 0, 0, "");
        assertThrows(
                IllegalArgumentException.class,
                () -> new ClientConfig(InetSocketAddress.createUnresolved("remote.invalid", 2775), bind, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ClientConfig(
                        new InetSocketAddress("127.0.0.1", 1),
                        new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x60, 0, 0, ""),
                        false));
        Set<SmppVersion> accepted = new HashSet<>(Set.of(SmppVersion.V3_4));
        ServerConfig server =
                new ServerConfig(new InetSocketAddress("127.0.0.1", 0), accepted, SmppVersion.V5_0, "server", 1, 1);
        accepted.clear();
        assertEquals(Set.of(SmppVersion.V3_4), server.acceptedVersions());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ServerConfig(
                        server.listenAddress(), Set.of(SmppVersion.V5_0), SmppVersion.V3_4, "server", 1, 0));
    }
}
