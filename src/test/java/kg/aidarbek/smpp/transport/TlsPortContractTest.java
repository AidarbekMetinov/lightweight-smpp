package kg.aidarbek.smpp.transport;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocket;
import kg.aidarbek.smpp.spi.FrameTransportContract;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TlsPortContractTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void encryptedPortsPreserveTheSharedOwnershipGuardAndCloseContract(boolean clientMode) throws Exception {
        var context = TlsTestMaterial.context(true, true);
        TlsConfig policy = clientMode
                ? TlsConfig.client(context, "localhost", Duration.ofSeconds(2))
                : TlsConfig.server(context, Duration.ofSeconds(2));
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                Socket rawPeer = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
                SSLSocket peer = (SSLSocket)
                        context.getSocketFactory().createSocket(rawPeer, "localhost", listener.getLocalPort(), true);
                TcpTransport transport = TcpTransport.adopt(listener.accept(), TcpTransportConfig.defaults(), policy)) {
            peer.setUseClientMode(!clientMode);
            peer.setSoTimeout(3000);
            CompletableFuture<Void> ready = new CompletableFuture<>();
            Thread handshake = Thread.ofVirtual().start(() -> {
                try {
                    peer.startHandshake();
                    ready.complete(null);
                } catch (IOException failure) {
                    ready.completeExceptionally(failure);
                }
            });
            try {
                FrameTransportContract.verify(
                        transport,
                        frame -> {
                            try {
                                peer.getOutputStream().write(frame);
                            } catch (IOException failure) {
                                throw new UncheckedIOException(failure);
                            }
                        },
                        () -> {
                            ready.get(3, TimeUnit.SECONDS);
                            return peer.getInputStream().readNBytes(16);
                        });
            } finally {
                handshake.join(3500);
                assertFalse(handshake.isAlive());
            }
        }
    }
}
