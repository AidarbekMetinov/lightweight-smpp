package kg.aidarbek.simulator;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import kg.aidarbek.smpp.endpoint.BoundSession;
import kg.aidarbek.smpp.endpoint.EndpointHandlers;

/** Composes one finite workload, receiver, observations, cleanup and final report. */
final class SimulatorRun {
    private SimulatorRun() {}

    public static int execute(SimulatorConfig config, String systemId, String password, Consumer<String> events)
            throws Exception {
        Optional<TrafficOperation> operation = config.operation().equals("none")
                ? Optional.empty()
                : Optional.of(MessageTraffic.find(config.operation(), config)
                        .or(() -> CommonTraffic.find(config.operation(), config))
                        .or(() -> BroadcastTraffic.find(config.operation(), config))
                        .orElseThrow(() -> new IllegalArgumentException("Unknown operation")));
        if (!config.content().equals("raw")
                && operation.isPresent()
                && !HelperContentPlans.supports(config.content(), config.operation()))
            throw new IllegalArgumentException("Content does not support the originating operation");
        if (operation.isPresent()
                && config.content().startsWith("receipt")
                && config.mode() != SimulatorConfig.Mode.SERVER)
            throw new IllegalArgumentException("Receipt traffic must originate from the message center");
        var content = content(config);
        var connectionLifecycle = config.lifecycle().create(config.mode() == SimulatorConfig.Mode.CLIENT);
        var environment = RunEnvironment.describe();
        Instant started = Instant.now();
        try (var writer = new ReportWriter(config.report());
                var replies = new ReplyController(config, () -> content(config))) {
            var sampler = new ResourceSampler(writer);
            var observedEndpoint = new AtomicReference<SimulatorEndpoint>();
            var pressure = new PressureSampler(
                    writer,
                    () -> {
                        var endpoint = observedEndpoint.get();
                        var received = replies.snapshot();
                        return PressureSampler.observe(
                                endpoint == null ? 0 : endpoint.connectionCount(),
                                endpoint == null
                                        ? List.of()
                                        : endpoint.sessions().stream()
                                                .map(BoundSession::resources)
                                                .toList(),
                                received.pendingDecisions(),
                                received.retainedStreams());
                    },
                    System::nanoTime);
            var pacer = new DeadlinePacer(
                    config.settings().spin().toNanos(), System::nanoTime, LockSupport::parkNanos, Thread::onSpinWait);
            try (var sampling = new SamplingLoop(
                    () -> {
                        sampler.sample();
                        pressure.sample();
                    },
                    config.settings().sampleInterval())) {
                Runnable receiverMaintenance = () -> {
                    replies.advance(System.nanoTime());
                    if (sampling.failure() != null)
                        throw new IllegalStateException("Resource observation failed", sampling.failure());
                };
                var registrations = EndpointHandlers.builder();
                MessageTraffic.register(registrations, replies);
                CommonTraffic.register(registrations, replies);
                BroadcastTraffic.register(registrations, replies);
                var failures = new ArrayList<String>();
                boolean cleanup = false;
                SimulatorEndpoint.LifecycleSnapshot lifecycle = null;
                ConnectionChurn.Snapshot churn = null;
                SimulatorEndpoint.ReconnectSnapshot reconnect = null;
                TrafficRunner.Result traffic = new TrafficRunner.Result(
                        new CohortMetrics(0).snapshot(), new CohortMetrics(0).snapshot(), 0, 0, 0);
                try (var endpoint = new SimulatorEndpoint(
                        config,
                        registrations.build(),
                        systemId,
                        password,
                        events,
                        receiverMaintenance,
                        connectionLifecycle)) {
                    Runnable maintenance = () -> {
                        receiverMaintenance.run();
                        endpoint.advance(System.nanoTime());
                    };
                    Runnable measurementStart = () -> {
                        sampler.baseline();
                        pressure.baseline();
                        endpoint.startChurn(System.nanoTime());
                    };
                    sampling.start();
                    observedEndpoint.set(endpoint);
                    try {
                        endpoint.start();
                        if (operation.isPresent()) {
                            traffic = new TrafficRunner(
                                            config.load(),
                                            config.connections() * config.window(),
                                            new SessionTrafficSource(
                                                    config.load().model(),
                                                    config.connections(),
                                                    config.window(),
                                                    (slot, index) -> new RequestObservation(operation
                                                            .orElseThrow()
                                                            .send(
                                                                    endpoint.session(slot),
                                                                    content.next(endpoint.nextOrdinal(slot)),
                                                                    index))),
                                            System::nanoTime,
                                            pacer::pause,
                                            maintenance,
                                            measurementStart,
                                            endpoint::stopChurn)
                                    .run();
                        } else {
                            serve(config.load().warmup().toNanos(), maintenance);
                            measurementStart.run();
                            serve(config.load().duration().toNanos(), maintenance);
                            endpoint.stopChurn();
                            serve(config.load().drain().toNanos(), maintenance);
                            traffic = new TrafficRunner.Result(
                                    new CohortMetrics(0).snapshot(),
                                    new CohortMetrics(0).snapshot(),
                                    config.load().warmup().toNanos(),
                                    config.load().duration().toNanos(),
                                    config.load().drain().toNanos());
                        }
                    } catch (Exception failure) {
                        failures.add("execution-failed:" + failure.getClass().getSimpleName());
                    } finally {
                        stopReceiver(replies);
                        try {
                            cleanup = endpoint.shutdown();
                        } catch (Exception failure) {
                            failures.add("shutdown-failed:" + failure.getClass().getSimpleName());
                        }
                        replies.advance(System.nanoTime());
                        lifecycle = endpoint.lifecycleSnapshot();
                        churn = endpoint.churnSnapshot();
                        reconnect = endpoint.reconnectSnapshot();
                    }
                }
                boolean samplingTerminated = sampling.stop(Duration.ofSeconds(5));
                if (!samplingTerminated) {
                    cleanup = false;
                    failures.add("sampling-did-not-terminate");
                } else {
                    sampler.sample();
                    pressure.sample();
                }
                if (sampling.failure() != null)
                    failures.add(
                            "sampling-failed:" + sampling.failure().getClass().getSimpleName());
                var received = replies.snapshot();
                failures.addAll(RunCriteria.evaluate(
                                config, traffic, cleanup, received.invalidContent(), received.incompleteAssemblies())
                        .failures());
                failures.addAll(ResourceCriteria.evaluate(config.settings(), sampler.summary()));
                failures.addAll(PressureCriteria.evaluate(config, pressure.summary()));
                failures.addAll(FaultCriteria.evaluate(config, received));
                if (churn != null) {
                    if (churn.planned() != churn.skipped() + churn.attempted()
                            || churn.attempted() != churn.initiated() + churn.busy() + churn.failed())
                        failures.add("churn-accounting-incomplete");
                    if (churn.skipped() != 0 || churn.busy() != 0) failures.add("churn-rate-not-established");
                    if (churn.failed() != 0 || lifecycle.replacementFailures() != 0)
                        failures.add("churn-replacement-failed");
                    if (lifecycle.replacementAborted() != 0) failures.add("churn-incomplete-at-cleanup");
                    if (lifecycle.replacementStarted()
                            != lifecycle.replacementBound()
                                    + lifecycle.replacementFailures()
                                    + lifecycle.replacementAborted()) failures.add("replacement-accounting-incomplete");
                }
                if (reconnect != null
                        && (reconnect.unfinishedLoops() != 0
                                || reconnect.terminalReasons().keySet().stream()
                                        .anyMatch(reason -> reason.equals("CLEANUP_FAILED")
                                                || reason.equals("CALLBACK_FAILED")
                                                || reason.equals("NOTIFICATION_CAPACITY")
                                                || reason.equals("EXCEPTIONAL_TERMINATION"))))
                    failures.add("reconnect-ownership-incomplete");
                var report = RunReport.create(
                        config, started, environment, traffic, received, sampler.summary(), cleanup, failures);
                report.put("samplingTerminated", samplingTerminated);
                report.put("pressure", pressure.summary());
                report.put(
                        "pressureSemantics",
                        "point-in-time per-generation request/reply reservations sampled through the last facade in each fixed slot; independent fields and per-field peaks are not an atomic transport-queue observation; request bytes include controls, reply bytes include received frames and reserved fallback/ready responses");
                report.put("connections", lifecycle);
                report.put("churn", churn);
                report.put("reconnect", reconnect);
                report.put("skippedResourceSamples", sampling.skippedSamples());
                report.put(
                        "resourceBaselinePhase",
                        operation.isPresent() ? "post-warmup-drain-before-measurement" : "post-local-receive-warmup");
                writer.finish(report);
                events.accept("RESULT passed=" + failures.isEmpty() + " attempted="
                        + traffic.measurement().attempted()
                        + " successful="
                        + traffic.measurement().outcomes().getOrDefault(CohortMetrics.Outcome.SUCCESS, 0L)
                        + " report=" + config.report().resolve("report.json"));
                return failures.isEmpty() ? 0 : 1;
            }
        }
    }

    private static ContentPlan content(SimulatorConfig config) {
        return config.content().equals("raw")
                ? new RawContent(config.payloadBytes(), config.seed())
                : HelperContentPlans.create(config.content(), config.payloadBytes(), config.seed());
    }

    private static void stopReceiver(ReplyController replies) {
        replies.close();
    }

    private static void pause(long nanos) {
        if (nanos > 0) LockSupport.parkNanos(nanos);
        if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Simulator interrupted");
    }

    private static void serve(long duration, Runnable maintenance) {
        long until = System.nanoTime() + duration;
        while (until - System.nanoTime() > 0) {
            maintenance.run();
            pause(Math.min(1_000_000, until - System.nanoTime()));
        }
    }
}
