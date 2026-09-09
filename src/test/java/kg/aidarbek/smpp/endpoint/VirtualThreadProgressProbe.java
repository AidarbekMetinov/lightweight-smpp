package kg.aidarbek.smpp.endpoint;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.ControlCommand;
import kg.aidarbek.smpp.request.BoundedNotifications;
import kg.aidarbek.smpp.request.RequestHandle;
import kg.aidarbek.smpp.request.RequestOptions;

/** Finite child JVM that exposes carrier starvation through real bounded notification contention. */
public final class VirtualThreadProgressProbe {
    private VirtualThreadProgressProbe() {}

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) throw new IllegalArgumentException("scenario");
        String scenario = arguments[0];
        switch (scenario) {
            case "responses", "admission" -> requests(scenario);
            case "reconnect" -> reconnect();
            case "server-start",
                    "server-shutdown",
                    "server-close",
                    "outbind-start",
                    "outbind-shutdown",
                    "outbind-close" -> listener(scenario);
            default -> throw new IllegalArgumentException("scenario");
        }
        System.out.println(scenario + " completed and all notifications retired");
    }

    private static void requests(String scenario) throws Exception {
        BoundedNotifications notifications = new BoundedNotifications(32, 1);
        List<EndpointConnection> connections = new ArrayList<>();
        List<FakeFrameTransport> transports = new ArrayList<>();
        List<RequestHandle<ControlCommand>> requests = new ArrayList<>();
        List<BoundSession> sessions = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            FakeFrameTransport transport = new FakeFrameTransport();
            EndpointConnection connection = EndpointConnection.client(
                    transport,
                    new ClientConfig(
                            new InetSocketAddress("127.0.0.1", 1),
                            new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x34, 0, 0, ""),
                            false),
                    EndpointOptions.defaults(),
                    notifications);
            connections.add(connection);
            transports.add(transport);
            connection.start();
            require(transport.writes.poll(2, TimeUnit.SECONDS) != null, "bind write missing");
            transport.receive(HexFormat.of().parseHex("00000016800000090000000000000001000210000134"));
            BoundSession session = connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS);
            sessions.add(session);
            if (scenario.equals("responses")) {
                requests.add(session.enquireLink(RequestOptions.timeout(Duration.ofSeconds(10))));
                require(transport.writes.poll(2, TimeUnit.SECONDS) != null, "enquiry write missing");
            }
        }
        List<CompletableFuture<RequestHandle<ControlCommand>>> admitted = new ArrayList<>();
        List<Runnable> work = new ArrayList<>();
        for (int index = 0; index < transports.size(); index++) {
            FakeFrameTransport transport = transports.get(index);
            BoundSession session = sessions.get(index);
            long sequence = scenario.equals("responses")
                    ? requests.get(index).identity().sequenceNumber()
                    : 0;
            CompletableFuture<RequestHandle<ControlCommand>> admission = new CompletableFuture<>();
            admitted.add(admission);
            work.add(() -> {
                if (scenario.equals("responses")) transport.receive(response(sequence));
                else admission.complete(session.enquireLink(RequestOptions.timeout(Duration.ofSeconds(10))));
            });
        }
        CarrierContention.run(CarrierContention.lock(notifications), work);
        if (scenario.equals("admission")) {
            for (int index = 0; index < transports.size(); index++) {
                RequestHandle<ControlCommand> request = admitted.get(index).get(2, TimeUnit.SECONDS);
                requests.add(request);
                require(transports.get(index).writes.poll(2, TimeUnit.SECONDS) != null, "enquiry write missing");
                transports.get(index).receive(response(request.identity().sequenceNumber()));
            }
        }
        for (RequestHandle<ControlCommand> request : requests) {
            require(
                    request.result()
                                    .toCompletableFuture()
                                    .get(2, TimeUnit.SECONDS)
                                    .commandStatus()
                            == 0,
                    "enquiry did not complete successfully");
        }
        for (EndpointConnection connection : connections) connection.close();
        notifications.close();
        require(notifications.awaitTermination(Duration.ofSeconds(2)), "notification workers did not retire");
        require(notifications.outstandingCount() == 0, "notification reservations remain");
    }

    private static void reconnect() throws Exception {
        EndpointResources resources = new EndpointResources(EndpointOptions.defaults(), null);
        List<ReconnectHandle> handles = new ArrayList<>();
        List<Runnable> work = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            ReconnectHandle handle = new ReconnectHandle(
                    resources,
                    new ReconnectPolicy(2, Duration.ZERO),
                    () -> {
                        throw new AssertionError("Idle cancellation must not connect");
                    },
                    ignored -> {
                        throw new AssertionError("Idle cancellation must not publish a session");
                    });
            handles.add(handle);
            work.add(() -> require(handle.cancel(), "cancellation did not select the terminal reason"));
        }
        CarrierContention.run(CarrierContention.lock(resources.notifications), work);
        for (ReconnectHandle handle : handles) {
            ReconnectResult result = handle.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            require(
                    result.reason() == ReconnectResult.Reason.CANCELLED && result.attempts() == 0,
                    "idle cancellation changed connection ownership");
        }
        resources.close();
        require(
                resources
                        .termination()
                        .toCompletableFuture()
                        .get(3, TimeUnit.SECONDS)
                        .complete(),
                "endpoint callback ownership did not retire");
    }

    private static byte[] response(long sequence) {
        return ByteBuffer.allocate(16)
                .putInt(16)
                .putInt(0x80000015)
                .putInt(0)
                .putInt((int) sequence)
                .array();
    }

    private static void listener(String scenario) throws Exception {
        List<ReentrantLock> locks = new ArrayList<>();
        List<Runnable> work = new ArrayList<>();
        List<Runnable> cleanup = new ArrayList<>();
        List<CompletionStage<EndpointTermination>> terminations = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            Object endpoint;
            Runnable start;
            Runnable close;
            Runnable shutdown;
            if (scenario.startsWith("server-")) {
                SmppServer server = new SmppServer(
                        new ServerConfig(
                                new InetSocketAddress("127.0.0.1", 0),
                                Set.of(SmppVersion.V3_4),
                                SmppVersion.V3_4,
                                "",
                                1,
                                1),
                        EndpointOptions.defaults(),
                        (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                        ignored -> {
                            throw new AssertionError("No peer was connected");
                        });
                endpoint = server;
                start = () ->
                        require(server.start().toCompletableFuture().join().getPort() > 0, "listener did not bind");
                close = server::close;
                shutdown = () -> server.shutdown(Duration.ofSeconds(2));
                terminations.add(server.termination());
            } else {
                OutbindListener listener = new OutbindListener(
                        new OutbindListenerConfig(
                                new InetSocketAddress("127.0.0.1", 0),
                                new BindRequest(BindMode.RECEIVER, "", "", "", 0x34, 0, 0, ""),
                                false),
                        EndpointOptions.defaults(),
                        (notification, peer) -> CompletableFuture.completedFuture(true),
                        ignored -> {
                            throw new AssertionError("No peer was connected");
                        },
                        ExchangeConfig.defaults());
                endpoint = listener;
                start = () ->
                        require(listener.start().toCompletableFuture().join().getPort() > 0, "listener did not bind");
                close = listener::close;
                shutdown = () -> listener.shutdown(Duration.ofSeconds(2));
                terminations.add(listener.termination());
            }
            var field = endpoint.getClass().getDeclaredField("resources");
            field.setAccessible(true);
            locks.add(CarrierContention.lock(field.get(endpoint)));
            if (!scenario.endsWith("-start")) start.run();
            work.add(scenario.endsWith("-start") ? start : scenario.endsWith("-close") ? close : shutdown);
            cleanup.add(close);
        }
        CarrierContention.run(locks, work);
        for (Runnable close : cleanup) close.run();
        for (CompletionStage<EndpointTermination> termination : terminations)
            require(
                    termination.toCompletableFuture().get(3, TimeUnit.SECONDS).complete(),
                    "listener resources did not retire");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
