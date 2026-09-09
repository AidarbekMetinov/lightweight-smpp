package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
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
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@Timeout(30)
class ReceiverGraceTest {
    @ParameterizedTest
    @EnumSource(LoadPlan.Model.class)
    void localCompletionKeepsOnlyTheRemainderOfTheOriginalDrainAcrossClockWrap(LoadPlan.Model model) {
        long started = Long.MAX_VALUE - 3_000_000;
        var clock = new AtomicLong(started);
        var call = new ScheduledCall(clock, started + 5_000_000);
        var issued = new AtomicInteger();
        var peerServed = new AtomicBoolean();
        var plan = plan(model, Duration.ofMillis(10));
        var result = new TrafficRunner(
                        plan,
                        1,
                        index -> {
                            issued.incrementAndGet();
                            return call;
                        },
                        clock::get,
                        clock::addAndGet,
                        () -> {
                            if (clock.get() - started == 8_000_000) {
                                assertEquals(
                                        CohortMetrics.Outcome.SUCCESS,
                                        call.poll().orElseThrow().outcome());
                                peerServed.set(true);
                            }
                        })
                .run();
        assertTrue(peerServed.get(), "Receiver maintenance stopped when the local call settled");
        assertEquals(1, issued.get());
        assertEquals(1, result.measurement().outcomes().get(CohortMetrics.Outcome.SUCCESS));
        assertEquals(0, result.measurement().successesDuringMeasurement());
        assertEquals(0, call.cancellations);
        assertEquals(10_000_000, result.drainNanos());
        assertEquals(11_000_000, clock.get() - started);
    }

    @Test
    void anAlreadyExpiredDrainDeadlineDoesNotStartAnotherReceiveGrace() {
        var clock = new AtomicLong();
        var stopped = new AtomicBoolean();
        var postStopPauses = new AtomicInteger();
        var result = new TrafficRunner(
                        plan(LoadPlan.Model.FIXED_CONCURRENCY, Duration.ofMillis(10)),
                        1,
                        index -> new ScheduledCall(clock, 0),
                        clock::get,
                        nanos -> {
                            if (stopped.get()) postStopPauses.incrementAndGet();
                            clock.addAndGet(nanos);
                        },
                        () -> {},
                        () -> {},
                        () -> {
                            stopped.set(true);
                            clock.addAndGet(15_000_000);
                        })
                .run();
        assertEquals(0, postStopPauses.get());
        assertEquals(16_000_000, clock.get());
        assertEquals(15_000_000, result.drainNanos());
        assertEquals(1, result.measurement().outcomes().get(CohortMetrics.Outcome.SUCCESS));
    }

    @Test
    void theSmallestSupportedDrainIsNotRoundedUpToAMaintenanceTick() {
        var clock = new AtomicLong();
        var stopped = new AtomicBoolean();
        var waited = new AtomicLong();
        var result = new TrafficRunner(
                        plan(LoadPlan.Model.FIXED_CONCURRENCY, Duration.ofNanos(1)),
                        1,
                        index -> new ScheduledCall(clock, 0),
                        clock::get,
                        nanos -> {
                            if (stopped.get()) waited.addAndGet(nanos);
                            clock.addAndGet(nanos);
                        },
                        () -> {},
                        () -> {},
                        () -> stopped.set(true))
                .run();
        assertEquals(1, result.drainNanos());
        assertEquals(1, waited.get());
        assertEquals(1_000_001, clock.get());
    }

    @Test
    void interruptionDuringReceiveGracePreservesCompletedTrafficAndTheInterruptFlag() {
        var clock = new AtomicLong();
        try {
            var result = new TrafficRunner(
                            plan(LoadPlan.Model.FIXED_CONCURRENCY, Duration.ofMillis(10)),
                            1,
                            index -> new ScheduledCall(clock, 0),
                            clock::get,
                            nanos -> {
                                if (clock.get() >= 1_000_000) {
                                    Thread.currentThread().interrupt();
                                    throw new IllegalStateException("Interrupted receiver grace");
                                }
                                clock.addAndGet(nanos);
                            },
                            () -> {})
                    .run();
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(List.of("drain-aborted:IllegalStateException"), result.failures());
            assertEquals(1, result.measurement().outcomes().get(CohortMetrics.Outcome.SUCCESS));
            assertEquals(0, result.measurement().pending());
            assertTrue(result.measurement().balanced());
            assertEquals(0, result.drainNanos());
        } finally {
            Thread.interrupted();
        }
    }

    private static LoadPlan plan(LoadPlan.Model model, Duration drain) {
        return new LoadPlan(
                model,
                model == LoadPlan.Model.ARRIVAL_RATE ? List.of(1000) : List.of(),
                1,
                Duration.ZERO,
                Duration.ofMillis(1),
                drain,
                Duration.ofSeconds(5));
    }

    @ParameterizedTest
    @ValueSource(strings = {"3.4", "5.0"})
    void answersAnIndependentPeerAfterTheLocalMeasuredCohortHasDrained(String version) throws Exception {
        var clock = new AtomicLong();
        var permit = new CountDownLatch(1);
        var response = new CompletableFuture<Long>();
        try (var peers = new DuplexPeers(version)) {
            Thread remote = Thread.startVirtualThread(() -> {
                try {
                    assertTrue(permit.await(8, TimeUnit.SECONDS));
                    var request = MessageTraffic.find("data", peers.remoteConfig)
                            .orElseThrow()
                            .send(peers.remoteSession, new RawContent(160, 1).next(0), 0);
                    response.complete(request.result()
                            .toCompletableFuture()
                            .get(8, TimeUnit.SECONDS)
                            .commandStatus());
                } catch (Throwable failure) {
                    response.completeExceptionally(failure);
                }
            });
            try {
                var plan = new LoadPlan(
                        LoadPlan.Model.FIXED_CONCURRENCY,
                        List.of(),
                        1,
                        Duration.ZERO,
                        Duration.ofMillis(1),
                        Duration.ofMillis(10),
                        Duration.ofSeconds(5));
                TrafficRunner.Result result;
                try {
                    result = new TrafficRunner(
                                    plan,
                                    1,
                                    index -> {
                                        var call = MessageTraffic.find("data", peers.localConfig)
                                                .orElseThrow()
                                                .send(peers.localSession, new RawContent(160, 1).next(index), index);
                                        await(call.result().toCompletableFuture());
                                        return new RequestObservation(call);
                                    },
                                    clock::get,
                                    nanos -> {
                                        clock.addAndGet(nanos);
                                        if (clock.get() > 1_000_000 && permit.getCount() != 0) {
                                            permit.countDown();
                                            await(response);
                                        }
                                    },
                                    () -> peers.localReplies.advance(clock.get()))
                            .run();
                } finally {
                    // Mirrors SimulatorRun's receiver closure immediately after TrafficRunner returns.
                    // The endpoint deliberately remains live so an independent peer's outcome is observable.
                    peers.localReplies.close();
                    permit.countDown();
                }
                long status = response.get(8, TimeUnit.SECONDS);
                assertEquals(0, status, "Live peer response; receiver=" + peers.localReplies.snapshot());
                assertEquals(1, peers.localReplies.snapshot().received());
                assertEquals(1, peers.localReplies.snapshot().accepted());
                assertEquals(1, result.measurement().outcomes().get(CohortMetrics.Outcome.SUCCESS));
                assertEquals(1_000_000, result.measurementNanos());
                assertEquals(10_000_000, result.drainNanos());
                assertEquals(11_000_000, clock.get());
            } finally {
                permit.countDown();
                remote.join(Duration.ofSeconds(9));
                assertFalse(remote.isAlive());
            }
        }
    }

    private static <T> T await(CompletableFuture<T> completion) {
        try {
            return completion.get(8, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) {
            throw new AssertionError(failure);
        }
    }

    private static final class ScheduledCall implements PendingCall {
        private final AtomicLong clock;
        private final long completedAt;
        private int cancellations;

        ScheduledCall(AtomicLong clock, long completedAt) {
            this.clock = clock;
            this.completedAt = completedAt;
        }

        @Override
        public Optional<Completion> poll() {
            if (cancellations != 0) return Optional.of(new Completion(CohortMetrics.Outcome.CANCELLED, -1));
            return clock.get() - completedAt >= 0
                    ? Optional.of(new Completion(CohortMetrics.Outcome.SUCCESS, 0))
                    : Optional.empty();
        }

        @Override
        public boolean mayHaveBeenSent() {
            return true;
        }

        @Override
        public void cancel() {
            if (poll().isEmpty()) cancellations++;
        }
    }

    private static final class DuplexPeers implements AutoCloseable {
        private final SimulatorConfig localConfig;
        private final SimulatorConfig remoteConfig;
        private final ReplyController localReplies;
        private final ReplyController remoteReplies;
        private final SmppClient client;
        private final SmppServer server;
        private final BoundSession localSession;
        private final BoundSession remoteSession;

        DuplexPeers(String version) throws Exception {
            localConfig = configuration("client", version);
            remoteConfig = configuration("server", version);
            localReplies = new ReplyController(localConfig, () -> new RawContent(160, 1));
            remoteReplies = new ReplyController(remoteConfig, () -> new RawContent(160, 1));
            var localHandlers = EndpointHandlers.builder();
            var remoteHandlers = EndpointHandlers.builder();
            MessageTraffic.register(localHandlers, localReplies);
            MessageTraffic.register(remoteHandlers, remoteReplies);
            client = new SmppClient(
                    EndpointOptions.defaults(), new ExchangeConfig(ExchangeOptions.defaults(), localHandlers.build()));
            var bound = new CompletableFuture<BoundSession>();
            server = new SmppServer(
                    new ServerConfig(
                            new InetSocketAddress("127.0.0.1", 0),
                            Set.of(remoteConfig.version()),
                            remoteConfig.version(),
                            "sim",
                            2,
                            8),
                    EndpointOptions.defaults(),
                    (request, address) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                    bound::complete,
                    null,
                    new ExchangeConfig(ExchangeOptions.defaults(), remoteHandlers.build()));
            try {
                var address = server.start().toCompletableFuture().get(8, TimeUnit.SECONDS);
                localSession = client.connect(new ClientConfig(
                                address,
                                new BindRequest(
                                        BindMode.TRANSCEIVER,
                                        "sim",
                                        "sim",
                                        "",
                                        localConfig.version().interfaceVersion(),
                                        0,
                                        0,
                                        ""),
                                true))
                        .toCompletableFuture()
                        .get(8, TimeUnit.SECONDS);
                remoteSession = bound.get(8, TimeUnit.SECONDS);
            } catch (Exception failure) {
                close();
                throw failure;
            }
        }

        private static SimulatorConfig configuration(String mode, String version) {
            return SimulatorArguments.parse(
                    mode,
                    "--revision=0123456789012345678901234567890123456789",
                    "--version=" + version,
                    "--operation=data",
                    "--timeout=PT5S");
        }

        @Override
        public void close() {
            try {
                try {
                    assertTrue(await(client.shutdown(Duration.ofSeconds(3)).toCompletableFuture())
                            .complete());
                } finally {
                    assertTrue(await(server.shutdown(Duration.ofSeconds(3)).toCompletableFuture())
                            .complete());
                }
            } finally {
                client.close();
                server.close();
                localReplies.close();
                remoteReplies.close();
            }
        }
    }
}
