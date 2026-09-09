package kg.aidarbek.smpp.transport;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;

class TlsConfigTest {
    @Test
    void rejectsInvalidIdentityAndDeadlineBeforeOpeningResources() throws Exception {
        SSLContext context = TlsTestMaterial.context(false, true);
        for (String name : new String[] {"", " ", "host name", "host/path"})
            assertThrows(IllegalArgumentException.class, () -> TlsConfig.client(context, name, Duration.ofSeconds(1)));
        for (Duration timeout :
                new Duration[] {Duration.ZERO, Duration.ofNanos(-1), Duration.ofSeconds(Long.MAX_VALUE)})
            assertThrows(IllegalArgumentException.class, () -> TlsConfig.server(context, timeout));
        assertThrows(
                IllegalArgumentException.class,
                () -> TlsConfig.server(SSLContext.getInstance("TLS"), Duration.ofSeconds(1)));
    }

    @Test
    void rejectsOppositeTlsRoleBeforeConnectingOrBinding() throws Exception {
        SSLContext context = TlsTestMaterial.context(true, true);
        InetSocketAddress local = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> TcpTransport.connect(
                                local,
                                TcpTransportConfig.defaults(),
                                TcpTransportTest.deadline(),
                                TlsConfig.server(context, Duration.ofSeconds(1)))
                        .close());
        assertThrows(
                IllegalArgumentException.class,
                () -> TcpListener.bind(
                                local,
                                1,
                                TcpTransportConfig.defaults(),
                                TlsConfig.client(context, "localhost", Duration.ofSeconds(1)),
                                (transport, peer) -> false)
                        .close());
    }
}
