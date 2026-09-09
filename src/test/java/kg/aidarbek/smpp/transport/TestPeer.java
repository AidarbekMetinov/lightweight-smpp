package kg.aidarbek.smpp.transport;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

final class TestPeer implements AutoCloseable {
    final Socket peer;
    final TcpTransport transport;

    TestPeer(TcpTransportConfig config) throws IOException {
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            peer = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
            Socket accepted = listener.accept();
            try {
                transport = TcpTransport.adopt(accepted, config);
            } catch (IOException | RuntimeException failure) {
                accepted.close();
                peer.close();
                throw failure;
            }
        }
        peer.setSoTimeout(5000);
    }

    @Override
    public void close() throws IOException {
        transport.close();
        peer.close();
        transport
                .termination()
                .toCompletableFuture()
                .orTimeout(5, TimeUnit.SECONDS)
                .join();
    }
}
