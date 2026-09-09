package kg.aidarbek.simulator;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import kg.aidarbek.smpp.codec.PduLimits;
import kg.aidarbek.smpp.endpoint.BindDecision;
import kg.aidarbek.smpp.endpoint.BoundSession;
import kg.aidarbek.smpp.endpoint.ClientConfig;
import kg.aidarbek.smpp.endpoint.EndpointHandlers;
import kg.aidarbek.smpp.endpoint.EndpointOptions;
import kg.aidarbek.smpp.endpoint.ExchangeConfig;
import kg.aidarbek.smpp.endpoint.ExchangeOptions;
import kg.aidarbek.smpp.endpoint.ServerConfig;
import kg.aidarbek.smpp.endpoint.SmppClient;
import kg.aidarbek.smpp.endpoint.SmppServer;
import kg.aidarbek.smpp.protocol.BindRequest;

/** Owns one finite client cohort or one listening server and its endpoint cleanup. */
final class SimulatorEndpoint implements AutoCloseable {
    private final SimulatorConfig config;
    private final EndpointHandlers handlers;
    private final String systemId;
    private final String password;
    private final Consumer<String> events;
    private final Runnable maintenance;
    private final List<BoundSession> sessions = new ArrayList<>();
    private SmppClient client;
    private SmppServer server;
    private boolean started;

    public SimulatorEndpoint(
            SimulatorConfig config,
            EndpointHandlers handlers,
            String systemId,
            String password,
            Consumer<String> events,
            Runnable maintenance) {
        this.config = config;
        this.handlers = handlers;
        this.systemId = systemId;
        this.password = password;
        this.events = events;
        this.maintenance = maintenance;
        bind(); // Validate credentials before endpoint allocation; values are never reported.
    }

    public void start() throws Exception {
        if (started) throw new IllegalStateException("Endpoint can start once");
        started = true;
        int total = config.connections() * config.window();
        long bytes = Math.max(1_048_576L, (long) config.window() * (config.payloadBytes() + 1024));
        var options = new EndpointOptions(
                config.connections(),
                config.window(),
                bytes,
                4,
                total + config.connections() * 2,
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                config.load().requestTimeout(),
                Duration.ofSeconds(5),
                new PduLimits(1_048_576, 1_048_560, 65));
        var exchange = new ExchangeConfig(
                new ExchangeOptions(
                        Math.min(64, total),
                        total,
                        config.window() + 8,
                        bytes,
                        config.load().requestTimeout().plusSeconds(1)),
                handlers);
        try {
            if (config.mode() == SimulatorConfig.Mode.CLIENT) {
                client = new SmppClient(options, exchange);
                var address = new InetSocketAddress(config.host(), config.port());
                for (int index = 0; index < config.connections(); index++) {
                    if (index > 0)
                        waitUntil(System.nanoTime() + config.connectInterval().toNanos());
                    sessions.add(client.connect(new ClientConfig(address, bind(), true))
                            .toCompletableFuture()
                            .get(16, TimeUnit.SECONDS));
                    maintenance.run();
                }
            } else {
                var ready = new ArrayBlockingQueue<BoundSession>(config.connections());
                server = new SmppServer(
                        new ServerConfig(
                                new InetSocketAddress(config.host(), config.port()),
                                Set.of(config.version()),
                                config.version(),
                                "simulator",
                                4,
                                config.connections()),
                        options,
                        (request, peer) -> CompletableFuture.completedFuture(new BindDecision(
                                request.systemId().equals(systemId)
                                                && request.password().equals(password)
                                        ? 0
                                        : 0x0d)),
                        session -> {
                            if (!ready.offer(session)) session.close();
                        },
                        null,
                        exchange);
                var address = server.start().toCompletableFuture().get(5, TimeUnit.SECONDS);
                events.accept("READY port=" + address.getPort());
                long deadline = System.nanoTime()
                        + Duration.ofSeconds(16).toNanos()
                        + config.connectInterval().toNanos() * config.connections();
                while (sessions.size() < config.connections()) {
                    maintenance.run();
                    var session = ready.poll(1, TimeUnit.MILLISECONDS);
                    if (session != null) sessions.add(session);
                    if (System.nanoTime() - deadline >= 0)
                        throw new IllegalStateException("Required bound cohort did not arrive");
                }
            }
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            close();
            throw failure;
        }
    }

    private BindRequest bind() {
        return new BindRequest(
                config.bindMode(), systemId, password, "", config.version().interfaceVersion(), 0, 0, "");
    }

    private void waitUntil(long deadline) throws InterruptedException {
        while (deadline - System.nanoTime() > 0) {
            maintenance.run();
            LockSupport.parkNanos(Math.min(1_000_000, deadline - System.nanoTime()));
            if (Thread.interrupted()) throw new InterruptedException("Simulator startup interrupted");
        }
    }

    public List<BoundSession> sessions() {
        return List.copyOf(sessions);
    }

    public boolean shutdown() throws Exception {
        try {
            if (client != null)
                return client.shutdown(Duration.ofSeconds(5))
                        .toCompletableFuture()
                        .get(7, TimeUnit.SECONDS)
                        .complete();
            if (server != null)
                return server.shutdown(Duration.ofSeconds(5))
                        .toCompletableFuture()
                        .get(7, TimeUnit.SECONDS)
                        .complete();
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        }
    }

    @Override
    public void close() {
        if (client != null) client.close();
        if (server != null) server.close();
    }
}
