package kg.aidarbek.smpp.transport;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TcpTransportConfigTest {
    @Test
    void rejectsUnboundedOrImpossibleCapacityBeforeAnySocketExists() {
        assertThrows(IllegalArgumentException.class, () -> new TcpTransportConfig(15, 1, 16, 1, 16, 0));
        assertThrows(IllegalArgumentException.class, () -> new TcpTransportConfig(16, -1, 16, 1, 16, 0));
        assertThrows(IllegalArgumentException.class, () -> new TcpTransportConfig(16, 1, -1, 1, 16, 0));
        assertThrows(IllegalArgumentException.class, () -> new TcpTransportConfig(16, 1, 16, 0, 16, 0));
        assertThrows(IllegalArgumentException.class, () -> new TcpTransportConfig(16, 1, 16, 1, 15, 0));
        assertThrows(IllegalArgumentException.class, () -> new TcpTransportConfig(16, 1, 16, 1, 16, -1));
    }
}
