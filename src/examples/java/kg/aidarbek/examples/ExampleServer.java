package kg.aidarbek.examples;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import kg.aidarbek.smpp.endpoint.BindDecision;
import kg.aidarbek.smpp.endpoint.BoundSession;
import kg.aidarbek.smpp.endpoint.EndpointOptions;
import kg.aidarbek.smpp.endpoint.ServerConfig;
import kg.aidarbek.smpp.endpoint.SmppServer;
import kg.aidarbek.smpp.profile.SmppVersion;

/** A finite loopback demonstration; its literal credentials are for this example only. */
public final class ExampleServer {
    private ExampleServer() {}

    /** Creates an unstarted demo server with an explicit session observer. */
    public static SmppServer create(int port, Consumer<BoundSession> boundListener) {
        return new SmppServer(
                new ServerConfig(
                        new InetSocketAddress("127.0.0.1", port),
                        Set.of(SmppVersion.V3_4, SmppVersion.V5_0),
                        SmppVersion.V5_0,
                        "demo",
                        2,
                        8),
                EndpointOptions.defaults(),
                (request, peer) -> CompletableFuture.completedFuture(
                        request.systemId().equals("demo") && request.password().equals("demo")
                                ? BindDecision.ACCEPT
                                : new BindDecision(0x0e)),
                boundListener);
    }

    /** Runs for a bounded number of seconds; arguments are optional port and lifetime seconds. */
    public static void main(String[] arguments) throws Exception {
        int port = arguments.length == 0 ? 2775 : Integer.parseInt(arguments[0]);
        int lifetime = arguments.length < 2 ? 60 : Integer.parseInt(arguments[1]);
        if (lifetime < 1 || lifetime > 3600) throw new IllegalArgumentException("Lifetime must be 1..3600 seconds");
        SmppServer server = create(port, ignored -> {});
        try {
            System.out.println(
                    "Listening on " + server.start().toCompletableFuture().get(3, TimeUnit.SECONDS));
            new CountDownLatch(1).await(lifetime, TimeUnit.SECONDS);
            if (!server.shutdown(Duration.ofSeconds(2))
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete()) throw new IllegalStateException("Demo server cleanup did not finish within its bound");
        } finally {
            server.close();
        }
    }
}
