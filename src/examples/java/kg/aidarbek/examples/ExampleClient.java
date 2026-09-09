package kg.aidarbek.examples;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.endpoint.ClientConfig;
import kg.aidarbek.smpp.endpoint.SmppClient;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;

/** A loopback control-session demonstration using the companion server's demo credentials. */
public final class ExampleClient {
    private ExampleClient() {}

    /** Binds, enquires and unbinds one transceiver session. */
    public static void run(InetSocketAddress address) throws Exception {
        SmppClient client = new SmppClient();
        try {
            var session = client.connect(new ClientConfig(
                            address, new BindRequest(BindMode.TRANSCEIVER, "demo", "demo", "", 0x34, 0, 0, ""), true))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            if (session.enquireLink()
                            .result()
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS)
                            .commandStatus()
                    != 0) throw new IllegalStateException("Peer rejected the example enquiry");
            if (session.unbind()
                            .result()
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS)
                            .commandStatus()
                    != 0) throw new IllegalStateException("Peer rejected the example unbind");
            session.termination().toCompletableFuture().get(5, TimeUnit.SECONDS);
        } finally {
            client.close();
            if (!client.termination()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS)
                    .complete()) throw new IllegalStateException("Demo client cleanup did not finish within its bound");
        }
    }

    /** Connects to loopback; the optional first argument is the server port. */
    public static void main(String[] arguments) throws Exception {
        run(new InetSocketAddress("127.0.0.1", arguments.length == 0 ? 2775 : Integer.parseInt(arguments[0])));
    }
}
