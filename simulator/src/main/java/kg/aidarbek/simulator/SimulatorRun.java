package kg.aidarbek.simulator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
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
        var environment = RunEnvironment.describe();
        Instant started = Instant.now();
        try (var writer = new ReportWriter(config.report());
                var replies = new ReplyController(config, () -> content(config))) {
            var sampler = new ResourceSampler(writer);
            Runnable maintenance = () -> {
                replies.advance(System.nanoTime());
                sampler.tick();
            };
            var registrations = EndpointHandlers.builder();
            MessageTraffic.register(registrations, replies);
            CommonTraffic.register(registrations, replies);
            var failures = new ArrayList<String>();
            boolean cleanup = false;
            TrafficRunner.Result traffic =
                    new TrafficRunner.Result(new CohortMetrics(0).snapshot(), new CohortMetrics(0).snapshot(), 0, 0, 0);
            try (var endpoint =
                    new SimulatorEndpoint(config, registrations.build(), systemId, password, events, maintenance)) {
                try {
                    endpoint.start();
                    if (operation.isPresent()) {
                        var sessions = endpoint.sessions();
                        traffic = new TrafficRunner(
                                        config.load(),
                                        config.connections() * config.window(),
                                        index -> new RequestObservation(operation
                                                .orElseThrow()
                                                .send(
                                                        sessions.get(
                                                                (int) Math.floorMod(index, (long) sessions.size())),
                                                        content.next(index / sessions.size()),
                                                        index)),
                                        System::nanoTime,
                                        SimulatorRun::pause,
                                        maintenance)
                                .run();
                    } else {
                        long until = System.nanoTime()
                                + config.load().warmup().toNanos()
                                + config.load().duration().toNanos()
                                + config.load().drain().toNanos();
                        while (until - System.nanoTime() > 0) {
                            maintenance.run();
                            pause(Math.min(1_000_000, until - System.nanoTime()));
                        }
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
                }
            }
            sampler.sample();
            var received = replies.snapshot();
            failures.addAll(RunCriteria.evaluate(
                            config, traffic, cleanup, received.invalidContent(), received.incompleteAssemblies())
                    .failures());
            writer.finish(RunReport.create(
                    config, started, environment, traffic, received, sampler.summary(), cleanup, failures));
            events.accept("RESULT passed=" + failures.isEmpty() + " attempted="
                    + traffic.measurement().attempted()
                    + " successful=" + traffic.measurement().outcomes().getOrDefault(CohortMetrics.Outcome.SUCCESS, 0L)
                    + " report=" + config.report().resolve("report.json"));
            return failures.isEmpty() ? 0 : 1;
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
}
