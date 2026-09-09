package kg.aidarbek.smpp.transport;

import java.io.IOException;
import java.io.UncheckedIOException;
import kg.aidarbek.smpp.spi.FrameTransportContract;
import org.junit.jupiter.api.Test;

class TcpPortContractTest {
    @Test
    void satisfiesTheSharedFrameTransportContract() throws Exception {
        try (TestPeer peer = new TestPeer(TcpTransportConfig.defaults())) {
            FrameTransportContract.verify(
                    peer.transport,
                    frame -> {
                        try {
                            peer.peer.getOutputStream().write(frame);
                        } catch (IOException failure) {
                            throw new UncheckedIOException(failure);
                        }
                    },
                    () -> peer.peer.getInputStream().readNBytes(16));
        }
    }
}
