package kg.aidarbek.simulator;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kg.aidarbek.smpp.endpoint.BindDecision;
import kg.aidarbek.smpp.endpoint.BoundSession;
import kg.aidarbek.smpp.endpoint.ClientConfig;
import kg.aidarbek.smpp.endpoint.EndpointHandlers;
import kg.aidarbek.smpp.endpoint.EndpointOptions;
import kg.aidarbek.smpp.endpoint.ExchangeConfig;
import kg.aidarbek.smpp.endpoint.ExchangeOptions;
import kg.aidarbek.smpp.endpoint.OutbindConnector;
import kg.aidarbek.smpp.endpoint.OutbindConnectorConfig;
import kg.aidarbek.smpp.endpoint.OutbindListener;
import kg.aidarbek.smpp.endpoint.OutbindListenerConfig;
import kg.aidarbek.smpp.endpoint.ServerConfig;
import kg.aidarbek.smpp.endpoint.SmppClient;
import kg.aidarbek.smpp.endpoint.SmppServer;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.AlertNotification;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Outbind;
import kg.aidarbek.smpp.protocol.Tlv;

/** Finite real-loopback checks for operations that have no paired response or request pacing. */
final class CommonOperationSmoke {
    private CommonOperationSmoke() {}

    static Result run(String operation, SmppVersion version) throws Exception {
        return switch (operation) {
            case "alert" -> alert(version);
            case "outbind" -> outbind(version);
            default -> throw new IllegalArgumentException("Unsupported one-way smoke operation");
        };
    }

    private static Result alert(SmppVersion version) throws Exception {
        AtomicInteger authentications = new AtomicInteger();
        CompletableFuture<BoundSession> bound = new CompletableFuture<>();
        CompletableFuture<AlertNotification> received = new CompletableFuture<>();
        EndpointHandlers handlers = EndpointHandlers.builder()
                .onAlert(incoming -> {
                    received.complete(incoming.pdu().command());
                    return CompletableFuture.completedFuture(null);
                })
                .build();
        try (SmppServer server = new SmppServer(
                        new ServerConfig(
                                new InetSocketAddress("127.0.0.1", 0), Set.of(version), version, "smoke", 1, 1),
                        EndpointOptions.defaults(),
                        (request, peer) -> {
                            authentications.incrementAndGet();
                            return CompletableFuture.completedFuture(authenticate(request));
                        },
                        bound::complete);
                SmppClient client = new SmppClient(
                        EndpointOptions.defaults(), new ExchangeConfig(ExchangeOptions.defaults(), handlers))) {
            InetSocketAddress address = server.start().toCompletableFuture().get(3, TimeUnit.SECONDS);
            BoundSession esme = client.connect(new ClientConfig(address, bind(version), true))
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS);
            BoundSession mc = bound.get(3, TimeUnit.SECONDS);
            AlertNotification notification = new AlertNotification(
                    new Address(1, 1, "1000"),
                    new Address(1, 1, "2000"),
                    new OptionalParameters(List.of(new Tlv(0x0422, new byte[] {0}))));
            mc.alerts()
                    .orElseThrow()
                    .send(notification)
                    .result()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS);
            if (!notification.equals(received.get(3, TimeUnit.SECONDS)))
                throw new IllegalStateException("Alert changed in transit");
            boolean cleanup = client.shutdown(Duration.ofSeconds(2))
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete();
            cleanup &= server.shutdown(Duration.ofSeconds(2))
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete();
            return new Result("alert", version, 1, authentications.get(), esme != mc ? 2 : 1, cleanup);
        }
    }

    private static Result outbind(SmppVersion version) throws Exception {
        AtomicInteger authentications = new AtomicInteger(), notifications = new AtomicInteger();
        CompletableFuture<BoundSession> bound = new CompletableFuture<>();
        try (OutbindListener listener = new OutbindListener(
                        new OutbindListenerConfig(new InetSocketAddress("127.0.0.1", 0), bind(version), true),
                        EndpointOptions.defaults(),
                        (request, peer) -> {
                            notifications.incrementAndGet();
                            authentications.incrementAndGet();
                            return CompletableFuture.completedFuture(
                                    request.systemId().equals("smoke-mc")
                                            && request.password().equals("smoke"));
                        },
                        bound::complete,
                        ExchangeConfig.defaults());
                OutbindConnector connector = new OutbindConnector(
                        new OutbindConnectorConfig(Set.of(version), version, "smoke-mc", 1, 1),
                        EndpointOptions.defaults(),
                        (request, peer) -> {
                            authentications.incrementAndGet();
                            return CompletableFuture.completedFuture(authenticate(request));
                        },
                        ExchangeConfig.defaults())) {
            InetSocketAddress address = listener.start().toCompletableFuture().get(3, TimeUnit.SECONDS);
            BoundSession mc = connector
                    .connectAttempt(address, new Outbind("smoke-mc", "smoke", new OptionalParameters(List.of())))
                    .result()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS);
            BoundSession esme = bound.get(3, TimeUnit.SECONDS);
            esme.enquireLink().result().toCompletableFuture().get(3, TimeUnit.SECONDS);
            boolean cleanup = connector
                    .shutdown(Duration.ofSeconds(2))
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete();
            cleanup &= listener.shutdown(Duration.ofSeconds(2))
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete();
            return new Result(
                    "outbind", version, notifications.get(), authentications.get(), mc != esme ? 2 : 1, cleanup);
        }
    }

    private static BindRequest bind(SmppVersion version) {
        return new BindRequest(BindMode.RECEIVER, "smoke", "smoke", "", version.interfaceVersion(), 0, 0, "");
    }

    private static BindDecision authenticate(BindRequest request) {
        return request.systemId().equals("smoke") && request.password().equals("smoke")
                ? BindDecision.ACCEPT
                : new BindDecision(0x0e);
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2)
            throw new IllegalArgumentException("Usage: CommonOperationSmoke alert|outbind 3.4|5.0");
        SmppVersion version =
                switch (arguments[1]) {
                    case "3.4" -> SmppVersion.V3_4;
                    case "5.0" -> SmppVersion.V5_0;
                    default -> throw new IllegalArgumentException("Unsupported profile");
                };
        Result result = run(arguments[0], version);
        System.out.println(result);
        if (!result.cleanup())
            throw new IllegalStateException("Owned work did not terminate within the smoke deadline");
    }
    /** Immutable finite-run observations, without throughput or production-capacity claims. */
    record Result(
            String operation,
            SmppVersion version,
            int notifications,
            int authentications,
            int boundSessions,
            boolean cleanup) {}
}
