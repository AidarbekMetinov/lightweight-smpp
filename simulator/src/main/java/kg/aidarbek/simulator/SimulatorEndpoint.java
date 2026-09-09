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
import kg.aidarbek.smpp.endpoint.ConnectionAttempt;
import kg.aidarbek.smpp.endpoint.ConnectionLifecycle;
import kg.aidarbek.smpp.endpoint.EndpointHandlers;
import kg.aidarbek.smpp.endpoint.EndpointOptions;
import kg.aidarbek.smpp.endpoint.ExchangeConfig;
import kg.aidarbek.smpp.endpoint.ExchangeOptions;
import kg.aidarbek.smpp.endpoint.ReconnectHandle;
import kg.aidarbek.smpp.endpoint.ReconnectResult;
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
    private final List<Slot> slots = new ArrayList<>();
    private final List<ReconnectHandle> reconnects = new ArrayList<>();
    private final List<CompletableFuture<ReconnectResult>> reconnectTerminations = new ArrayList<>();
    private final ArrayBlockingQueue<BoundSession> ready;
    private final ConnectionChurn churn;
    private final ConnectionLifecycle lifecycle;
    private ClientConfig target;
    private long initialBound, replacementStarted, replacementBound, replacementFailures, replacementAborted;
    private int peakConnections;
    private volatile SmppClient client;
    private volatile SmppServer server;
    private volatile List<BoundSession> observedSessions = List.of();
    private boolean started;

    public SimulatorEndpoint(
            SimulatorConfig config,
            EndpointHandlers handlers,
            String systemId,
            String password,
            Consumer<String> events,
            Runnable maintenance) {
        this(
                config,
                handlers,
                systemId,
                password,
                events,
                maintenance,
                config.lifecycle().create(config.mode() == SimulatorConfig.Mode.CLIENT));
    }

    SimulatorEndpoint(
            SimulatorConfig config,
            EndpointHandlers handlers,
            String systemId,
            String password,
            Consumer<String> events,
            Runnable maintenance,
            ConnectionLifecycle lifecycle) {
        this.lifecycle = java.util.Objects.requireNonNull(lifecycle, "lifecycle");
        this.config = config;
        this.handlers = handlers;
        this.systemId = systemId;
        this.password = password;
        this.events = events;
        this.maintenance = maintenance;
        ready = new ArrayBlockingQueue<>(config.connections());
        churn = config.settings().churnRate() == 0
                ? null
                : new ConnectionChurn(
                        config.connections(),
                        config.settings().churnRate(),
                        config.settings().churnCount(),
                        config.load().duration(),
                        this::replace);
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
                total
                        + config.connections()
                                * (config.lifecycle().reconnectPolicy().isPresent() ? 4 : 2),
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
                client = new SmppClient(options, exchange, lifecycle);
                var address = new InetSocketAddress(config.host(), config.port());
                target = new ClientConfig(address, bind(), true);
                for (int index = 0; index < config.connections(); index++) {
                    if (index > 0)
                        waitUntil(System.nanoTime() + config.connectInterval().toNanos());
                    if (config.lifecycle().reconnectPolicy().isPresent()) {
                        var reconnect = client.reconnect(
                                target, config.lifecycle().reconnectPolicy().orElseThrow(), session -> {});
                        reconnects.add(reconnect);
                        var terminated = reconnect.termination().toCompletableFuture();
                        reconnectTerminations.add(terminated);
                        install(index, awaitFirst(reconnect, terminated));
                    } else {
                        install(
                                index,
                                client.connect(target).toCompletableFuture().get(16, TimeUnit.SECONDS));
                    }
                    initialBound++;
                    observeConnections();
                    maintenance.run();
                }
            } else {
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
                        exchange,
                        lifecycle);
                var address = server.start().toCompletableFuture().get(5, TimeUnit.SECONDS);
                events.accept("READY port=" + address.getPort());
                long deadline = System.nanoTime()
                        + Duration.ofSeconds(16).toNanos()
                        + config.connectInterval().toNanos() * config.connections();
                while (sessions.size() < config.connections()) {
                    maintenance.run();
                    var session = ready.poll(1, TimeUnit.MILLISECONDS);
                    if (session != null) {
                        install(slots.size(), session);
                        initialBound++;
                        observeConnections();
                    }
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

    private BoundSession awaitFirst(ReconnectHandle reconnect, CompletableFuture<ReconnectResult> terminated)
            throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(16).toNanos();
        while (deadline - System.nanoTime() > 0) {
            var session = reconnect.currentSession();
            if (session.isPresent()) return session.orElseThrow();
            if (terminated.isDone()) throw new IllegalStateException("Reconnect ended before the initial bound cohort");
            waitUntil(System.nanoTime() + 1_000_000);
        }
        throw new IllegalStateException("Initial reconnect cohort deadline expired");
    }

    private void waitUntil(long deadline) throws InterruptedException {
        while (deadline - System.nanoTime() > 0) {
            maintenance.run();
            LockSupport.parkNanos(Math.min(1_000_000, deadline - System.nanoTime()));
            if (Thread.interrupted()) throw new InterruptedException("Simulator startup interrupted");
        }
    }

    public List<BoundSession> sessions() {
        return observedSessions;
    }

    public BoundSession session(int slot) {
        return sessions.get(slot);
    }

    public long nextOrdinal(int slot) {
        return slots.get(slot).ordinal++;
    }

    public void startChurn(long now) {
        if (churn != null) churn.start(now);
    }

    public void advance(long now) {
        if (!reconnects.isEmpty()) {
            for (int index = 0; index < reconnects.size(); index++) {
                var current = reconnects.get(index).currentSession();
                if (current.isPresent()
                        && !current.orElseThrow()
                                .id()
                                .equals(slots.get(index).session.id())) {
                    install(index, current.orElseThrow());
                    replacementBound++;
                }
            }
        } else if (client != null) {
            for (int index = 0; index < slots.size(); index++) {
                var slot = slots.get(index);
                if (!slot.replacing) continue;
                try {
                    if (slot.attempt == null) {
                        if (!slot.terminated.isDone()) continue;
                        slot.terminated.join();
                        slot.attempt = client.connectAttempt(target);
                        slot.connected = slot.attempt.result().toCompletableFuture();
                        observeConnections();
                    }
                    if (slot.connected.isDone()) {
                        var replacement = slot.connected.join();
                        install(index, replacement);
                        replacementBound++;
                    }
                } catch (RuntimeException failure) {
                    slot.retirementFailed = slot.terminated.isCompletedExceptionally();
                    slot.replacing = false;
                    slot.attempt = null;
                    slot.connected = null;
                    replacementFailures++;
                }
            }
        } else if (server != null) {
            for (int index = 0; index < slots.size() && !ready.isEmpty(); index++) {
                var slot = slots.get(index);
                if (slot.terminated.isDone() && !slot.terminated.isCompletedExceptionally()) {
                    var replacement = ready.poll();
                    if (replacement != null) {
                        install(index, replacement);
                        replacementBound++;
                    }
                }
            }
        }
        if (churn != null) churn.advance(now);
        observeConnections();
    }

    private boolean replace(int index) {
        var slot = slots.get(index);
        if (slot.replacing || slot.retirementFailed) return false;
        slot.replacing = true;
        replacementStarted++;
        slot.session.close();
        return true;
    }

    private void install(int index, BoundSession session) {
        var slot = new Slot(session);
        if (index == slots.size()) {
            slots.add(slot);
            sessions.add(session);
        } else {
            slots.set(index, slot);
            sessions.set(index, session);
        }
        observedSessions = List.copyOf(sessions);
    }

    public void stopChurn() {
        if (churn != null) churn.stop();
    }

    public LifecycleSnapshot lifecycleSnapshot() {
        return new LifecycleSnapshot(
                initialBound,
                replacementStarted,
                replacementBound,
                replacementFailures,
                replacementAborted,
                connectionCount(),
                peakConnections);
    }

    public int connectionCount() {
        return client != null ? client.connectionCount() : server != null ? server.connectionCount() : 0;
    }

    private void observeConnections() {
        peakConnections = Math.max(peakConnections, connectionCount());
    }

    public ConnectionChurn.Snapshot churnSnapshot() {
        return churn == null ? null : churn.snapshot();
    }

    public ReconnectSnapshot reconnectSnapshot() {
        long attempts = 0, published = 0;
        int unfinished = 0;
        var reasons = new java.util.LinkedHashMap<String, Long>();
        for (int index = 0; index < reconnects.size(); index++) {
            attempts += reconnects.get(index).attempts();
            var terminal = reconnectTerminations.get(index);
            if (!terminal.isDone()) unfinished++;
            else {
                try {
                    var result = terminal.join();
                    published += result.publishedSessions();
                    reasons.merge(result.reason().name(), 1L, Long::sum);
                } catch (RuntimeException failure) {
                    reasons.merge("EXCEPTIONAL_TERMINATION", 1L, Long::sum);
                }
            }
        }
        return new ReconnectSnapshot(reconnects.size(), attempts, published, unfinished, reasons);
    }

    record ReconnectSnapshot(
            int loops,
            long attempts,
            long publishedSessions,
            int unfinishedLoops,
            java.util.Map<String, Long> terminalReasons) {
        ReconnectSnapshot {
            terminalReasons = java.util.Map.copyOf(terminalReasons);
        }
    }

    record LifecycleSnapshot(
            long initialBound,
            long replacementStarted,
            long replacementBound,
            long replacementFailures,
            long replacementAborted,
            int currentConnections,
            int peakConnections) {}

    public boolean shutdown() throws Exception {
        stopChurn();
        for (var reconnect : reconnects) reconnect.cancel();
        for (var slot : slots) {
            if (slot.replacing) {
                if (slot.attempt != null) slot.attempt.cancel();
                slot.replacing = false;
                replacementAborted++;
            }
        }
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
        stopChurn();
        for (var reconnect : reconnects) reconnect.cancel();
        if (client != null) client.close();
        if (server != null) server.close();
    }

    private static final class Slot {
        private final BoundSession session;
        private final CompletableFuture<Void> terminated;
        private ConnectionAttempt attempt;
        private CompletableFuture<BoundSession> connected;
        private long ordinal;
        private boolean replacing;
        private boolean retirementFailed;

        Slot(BoundSession session) {
            this.session = session;
            terminated = session.termination().toCompletableFuture();
        }
    }
}
