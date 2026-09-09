package kg.aidarbek.simulator;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import kg.aidarbek.smpp.codec.PduLimits;
import kg.aidarbek.smpp.endpoint.BindDecision;
import kg.aidarbek.smpp.endpoint.BoundSession;
import kg.aidarbek.smpp.endpoint.ClientConfig;
import kg.aidarbek.smpp.endpoint.EndpointHandlers;
import kg.aidarbek.smpp.endpoint.EndpointOptions;
import kg.aidarbek.smpp.endpoint.ExchangeConfig;
import kg.aidarbek.smpp.endpoint.ExchangeOptions;
import kg.aidarbek.smpp.endpoint.HandlerResponse;
import kg.aidarbek.smpp.endpoint.MessageOperations;
import kg.aidarbek.smpp.endpoint.ServerConfig;
import kg.aidarbek.smpp.endpoint.SmppClient;
import kg.aidarbek.smpp.endpoint.SmppServer;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.DeliverSmResponse;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.SubmitSmResponse;
import kg.aidarbek.smpp.request.RequestHandle;
import kg.aidarbek.smpp.request.RequestOptions;
import kg.aidarbek.smpp.session.SessionState;

/** Separate finite submission/receipt executable; it is not a throughput benchmark. */
public final class ReceiptScenario {
    private ReceiptScenario() {}
    /** Runs one loopback client or server role; reports are created exclusively in a fresh directory.
     * @param arguments explicit role and bounded --name=value options
     * @throws Exception if configuration, fresh report creation, artifact identification or report writing fails */
    public static void main(String[] arguments) throws Exception {
        ReceiptScenarioOptions options = ReceiptScenarioOptions.parse(arguments);
        Files.createDirectory(options.report());
        long started = System.nanoTime();
        Result result = options.server() ? server(options) : client(options);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("scenario", "synthetic-receipt-roundtrip");
        report.put("role", options.server() ? "server" : "client");
        report.put("version", options.version());
        report.put("fault", options.fault());
        report.put("count", options.count());
        report.put("window", options.window());
        report.put("rejectEvery", options.rejectEvery());
        report.put("timeoutNanos", options.timeout().toNanos());
        report.put("durationNanos", options.duration().toNanos());
        report.put("drainNanos", options.drain().toNanos());
        report.put("elapsedNanos", System.nanoTime() - started);
        report.put("javaVersion", System.getProperty("java.version"));
        report.put("os", System.getProperty("os.name"));
        report.put("architecture", System.getProperty("os.arch"));
        report.put("entryPoint", identity(ReceiptScenario.class));
        report.put("library", identity(SmppClient.class));
        report.put("counts", result.counts());
        report.put("cleanup", result.cleanup());
        report.put("remainingRequests", result.remainingRequests());
        report.put("remainingReplies", result.remainingReplies());
        report.put("failure", result.failure());
        report.put("passed", result.passed());
        Files.writeString(
                options.report().resolve("summary.json"), Json.encode(report) + "\n", StandardOpenOption.CREATE_NEW);
        System.out.println("RESULT passed=" + result.passed() + " report="
                + options.report().resolve("summary.json"));
        if (!result.passed()) System.exit(1);
    }

    private static Result client(ReceiptScenarioOptions options) {
        Duration logicalTimeout = options.timeout().multipliedBy(3);
        ReceiptScenarioLedger ledger =
                new ReceiptScenarioLedger(options.count(), options.window(), logicalTimeout.toNanos());
        EndpointHandlers handlers = EndpointHandlers.builder()
                .on(MessageOperations.DELIVER_SM, incoming -> {
                    String id;
                    try {
                        id = ReceiptScenarioMessages.receiptId(incoming.pdu().command());
                    } catch (IllegalArgumentException malformed) {
                        id = null;
                    }
                    boolean accepted = ledger.receipt(id, System.nanoTime());
                    return CompletableFuture.completedFuture(new HandlerResponse<>(
                            accepted ? 0 : 0x0c,
                            new DeliverSmResponse(new MessageResponse(
                                    accepted ? Optional.of("") : Optional.empty(),
                                    new OptionalParameters(List.of())))));
                })
                .build();
        List<Pending> pending = new ArrayList<>();
        BoundSession session = null;
        boolean cleanup = false;
        String failure = "";
        try (SmppClient endpoint = new SmppClient(endpointOptions(options), exchange(options, handlers))) {
            try {
                long deadline = System.nanoTime() + options.duration().toNanos();
                session = endpoint.connect(new ClientConfig(
                                new InetSocketAddress("127.0.0.1", options.port()),
                                new BindRequest(
                                        BindMode.TRANSCEIVER,
                                        "receipt",
                                        "receipt",
                                        "",
                                        options.version().interfaceVersion(),
                                        0,
                                        0,
                                        ""),
                                true))
                        .toCompletableFuture()
                        .get(options.timeout().multipliedBy(3).toNanos(), TimeUnit.NANOSECONDS);
                while (System.nanoTime() - deadline < 0 && session.state() != SessionState.CLOSED) {
                    for (var iterator = pending.iterator(); iterator.hasNext(); ) {
                        Pending current = iterator.next();
                        var outcome = current.handle().terminalOutcome();
                        if (outcome.isEmpty()) continue;
                        if (!ledger.settled(current.index())) {
                            if (outcome.get().response().isPresent()) {
                                var response = outcome.get().response().orElseThrow();
                                ledger.submission(
                                        current.index(),
                                        response.commandStatus(),
                                        response.command().fields().messageId().orElse(""),
                                        System.nanoTime());
                            } else ledger.failedSubmission(current.index());
                        }
                        iterator.remove();
                    }
                    ledger.expire(System.nanoTime());
                    for (Pending current : pending)
                        if (ledger.settled(current.index())) current.handle().cancel();
                    while (pending.size() < options.window()) {
                        int index = ledger.admit(System.nanoTime());
                        if (index < 0) break;
                        try {
                            pending.add(new Pending(
                                    index,
                                    session.submission()
                                            .orElseThrow()
                                            .send(
                                                    ReceiptScenarioMessages.submission(index),
                                                    new RequestOptions(logicalTimeout))));
                        } catch (RuntimeException rejected) {
                            ledger.failedSubmission(index);
                        }
                    }
                    var snapshot = ledger.snapshot();
                    if (snapshot.attempted() == options.count() && snapshot.active() == 0 && pending.isEmpty()) break;
                    Thread.sleep(1);
                }
            } catch (Exception problem) {
                failure = problem.getClass().getName();
            } finally {
                ledger.finish();
                for (Pending current : pending) current.handle().cancel();
                try {
                    cleanup = endpoint.shutdown(options.drain())
                            .toCompletableFuture()
                            .get(shutdownWait(options), TimeUnit.NANOSECONDS)
                            .complete();
                } catch (Exception problem) {
                    failure = problem.getClass().getName();
                }
            }
        }
        var snapshot = ledger.snapshot();
        return result(
                session,
                snapshot,
                snapshot.passed() && snapshot.negativeSubmissions() == expectedNegatives(options),
                cleanup,
                failure);
    }

    private static Result server(ReceiptScenarioOptions options) {
        ReceiptScenarioServer state = new ReceiptScenarioServer(options);
        AtomicReference<BoundSession> bound = new AtomicReference<>();
        EndpointHandlers handlers = EndpointHandlers.builder()
                .on(MessageOperations.SUBMIT_SM, state::submit)
                .build();
        boolean cleanup = false;
        String failure = "";
        try (SmppServer endpoint = new SmppServer(
                new ServerConfig(
                        new InetSocketAddress("127.0.0.1", options.port()),
                        Set.of(options.version()),
                        options.version(),
                        "receipt-mc",
                        1,
                        1),
                endpointOptions(options),
                (request, peer) -> CompletableFuture.completedFuture(
                        request.mode() == BindMode.TRANSCEIVER
                                        && request.systemId().equals("receipt")
                                        && request.password().equals("receipt")
                                ? BindDecision.ACCEPT
                                : new BindDecision(0x0e)),
                bound::set,
                null,
                exchange(options, handlers))) {
            try {
                long deadline = System.nanoTime() + options.duration().toNanos();
                var address = endpoint.start()
                        .toCompletableFuture()
                        .get(options.timeout().toNanos(), TimeUnit.NANOSECONDS);
                System.out.println("READY port=" + address.getPort());
                while (System.nanoTime() - deadline < 0) {
                    state.tick();
                    if (bound.get() != null && bound.get().state() == SessionState.CLOSED) break;
                    Thread.sleep(1);
                }
            } catch (Exception problem) {
                failure = problem.getClass().getName();
            } finally {
                state.close();
                try {
                    cleanup = endpoint.shutdown(options.drain())
                            .toCompletableFuture()
                            .get(shutdownWait(options), TimeUnit.NANOSECONDS)
                            .complete();
                } catch (Exception problem) {
                    failure = problem.getClass().getName();
                }
            }
        }
        var snapshot = state.snapshot();
        return result(
                bound.get(),
                snapshot,
                snapshot.passed() && snapshot.rejectedSubmissions() == expectedNegatives(options),
                cleanup,
                failure);
    }

    private static Result result(
            BoundSession session, Object counts, boolean countsPassed, boolean cleanup, String failure) {
        int requests = session == null ? 0 : session.resources().pendingRequests();
        int replies = session == null ? 0 : session.resources().pendingReplies();
        return new Result(
                counts,
                cleanup,
                requests,
                replies,
                failure,
                countsPassed && cleanup && requests == 0 && replies == 0 && failure.isEmpty());
    }

    private static int expectedNegatives(ReceiptScenarioOptions options) {
        return options.rejectEvery() == 0 ? 0 : options.count() / options.rejectEvery();
    }

    private static long shutdownWait(ReceiptScenarioOptions options) {
        return options.drain().plusSeconds(3).toNanos();
    }

    private static EndpointOptions endpointOptions(ReceiptScenarioOptions options) {
        return new EndpointOptions(
                1,
                options.window(),
                65536,
                2,
                4 * options.window() + 16,
                options.timeout(),
                options.timeout().multipliedBy(2),
                options.timeout(),
                options.drain(),
                new PduLimits(1024, 512, 8));
    }

    private static ExchangeConfig exchange(ReceiptScenarioOptions options, EndpointHandlers handlers) {
        return new ExchangeConfig(
                new ExchangeOptions(
                        options.window(),
                        options.window(),
                        2 * options.window(),
                        65536,
                        options.timeout().multipliedBy(3)),
                handlers);
    }

    private static Map<String, String> identity(Class<?> type) throws Exception {
        Map<String, String> identity = new LinkedHashMap<>();
        try (InputStream stream = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            identity.put("classSha256", digest(stream));
        }
        Path artifact =
                Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (Files.isRegularFile(artifact)) {
            try (InputStream stream = Files.newInputStream(artifact)) {
                identity.put("artifactSha256", digest(stream));
            }
        } else identity.put("artifactSha256", "classes-directory");
        return Map.copyOf(identity);
    }

    private static String digest(InputStream stream) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = new byte[8192];
        for (int length; (length = stream.read(bytes)) != -1; ) digest.update(bytes, 0, length);
        return HexFormat.of().formatHex(digest.digest());
    }

    private record Pending(int index, RequestHandle<SubmitSmResponse> handle) {}

    private record Result(
            Object counts,
            boolean cleanup,
            int remainingRequests,
            int remainingReplies,
            String failure,
            boolean passed) {}
}
