package kg.aidarbek.examples;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.endpoint.ClientConfig;
import kg.aidarbek.smpp.endpoint.EndpointHandlers;
import kg.aidarbek.smpp.endpoint.EndpointOptions;
import kg.aidarbek.smpp.endpoint.ExchangeConfig;
import kg.aidarbek.smpp.endpoint.ExchangeOptions;
import kg.aidarbek.smpp.endpoint.HandlerResponse;
import kg.aidarbek.smpp.endpoint.MessageOperations;
import kg.aidarbek.smpp.endpoint.SmppClient;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.DeliverSmResponse;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.ShortMessage;
import kg.aidarbek.smpp.protocol.SubmitSm;

/** A loopback message exchange using the companion server's demonstration-only credentials. */
public final class ExampleClient {
    private ExampleClient() {}

    /** Binds, submits, accepts a separate delivery, enquires and gracefully drains one transceiver session. */
    public static void run(InetSocketAddress address) throws Exception {
        CompletableFuture<Void> delivered = new CompletableFuture<>();
        EndpointHandlers handlers = EndpointHandlers.builder()
                .on(MessageOperations.DELIVER_SM, incoming -> {
                    delivered.complete(null);
                    return CompletableFuture.completedFuture(new HandlerResponse<>(
                            0,
                            new DeliverSmResponse(
                                    new MessageResponse(Optional.of(""), new OptionalParameters(List.of())))));
                })
                .build();
        SmppClient client =
                new SmppClient(EndpointOptions.defaults(), new ExchangeConfig(ExchangeOptions.defaults(), handlers));
        try {
            var session = client.connect(new ClientConfig(
                            address, new BindRequest(BindMode.TRANSCEIVER, "demo", "demo", "", 0x34, 0, 0, ""), true))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS);
            if (session.submission()
                            .orElseThrow()
                            .send(submission())
                            .result()
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS)
                            .commandStatus()
                    != 0) throw new IllegalStateException("Peer rejected the example submission");
            delivered.get(5, TimeUnit.SECONDS);
            if (session.enquireLink()
                            .result()
                            .toCompletableFuture()
                            .get(5, TimeUnit.SECONDS)
                            .commandStatus()
                    != 0) throw new IllegalStateException("Peer rejected the example enquiry");
            if (!client.shutdown(Duration.ofSeconds(3))
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS)
                    .complete()) throw new IllegalStateException("Example message drain and unbind did not finish");
            session.termination().toCompletableFuture().get(5, TimeUnit.SECONDS);
        } finally {
            client.close();
            if (!client.termination()
                    .toCompletableFuture()
                    .get(5, TimeUnit.SECONDS)
                    .complete()) throw new IllegalStateException("Demo client cleanup did not finish within its bound");
        }
    }

    private static SubmitSm submission() {
        return new SubmitSm(new ShortMessage(
                "",
                new Address(0, 0, "esme"),
                new Address(0, 0, "demo"),
                0,
                0,
                0,
                "",
                "",
                0,
                0,
                0,
                0,
                new OctetString("Hello SMPP".getBytes(StandardCharsets.US_ASCII)),
                new OptionalParameters(List.of())));
    }

    /** Connects to loopback; the optional first argument is the server port. */
    public static void main(String[] arguments) throws Exception {
        run(new InetSocketAddress("127.0.0.1", arguments.length == 0 ? 2775 : Integer.parseInt(arguments[0])));
    }
}
