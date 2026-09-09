package kg.aidarbek.simulator;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Assembles explicit report semantics; credentials and arbitrary endpoint diagnostics are excluded. */
final class RunReport {
    private RunReport() {}

    public static Map<String, Object> create(
            SimulatorConfig config,
            Instant started,
            Map<String, Object> environment,
            TrafficRunner.Result traffic,
            ReplyController.Snapshot received,
            ResourceSampler.Summary resources,
            boolean cleanup,
            List<String> failures) {
        var inputs = new LinkedHashMap<String, Object>();
        inputs.put("mode", config.mode());
        inputs.put("host", config.host());
        inputs.put("port", config.port());
        inputs.put("version", config.version());
        inputs.put("bind", config.bindMode());
        inputs.put("connections", config.connections());
        inputs.put("window", config.window());
        inputs.put("connectIntervalNanos", config.connectInterval().toNanos());
        inputs.put("operation", config.operation());
        inputs.put("content", "raw");
        inputs.put("payloadBytes", config.payloadBytes());
        inputs.put("seed", config.seed());
        inputs.put("source", config.source());
        inputs.put("destination", config.destination());
        inputs.put("model", config.load().model());
        inputs.put("rates", config.load().rates());
        inputs.put("count", config.load().count());
        inputs.put("warmupNanos", config.load().warmup().toNanos());
        inputs.put("measurementNanos", config.load().duration().toNanos());
        inputs.put("drainNanos", config.load().drain().toNanos());
        inputs.put("requestTimeoutNanos", config.load().requestTimeout().toNanos());
        inputs.put(
                "faults",
                Map.of(
                        "rejectPercent",
                        config.faults().rejectPercent(),
                        "delayPercent",
                        config.faults().delayPercent(),
                        "stallPercent",
                        config.faults().stallPercent(),
                        "delayNanos",
                        config.faults().delay().toNanos(),
                        "rejectionStatus",
                        config.faults().rejectionStatus(),
                        "disconnectAfter",
                        config.faults().disconnectAfter(),
                        "identity",
                        "per-connection incoming sequence with configured seed; identical sequences select identical decisions"));
        var report = new LinkedHashMap<String, Object>();
        report.put("schema", 1);
        report.put("runId", config.runId());
        report.put("suppliedSourceRevision", config.revision());
        report.put("startedAt", started.toString());
        report.put("finishedAt", Instant.now().toString());
        report.put("configuration", inputs);
        report.put("environment", environment);
        report.put("traffic", traffic);
        report.put("receiver", received);
        report.put("resources", resources);
        report.put(
                "latencySemantics",
                Map.of(
                        "invocationLatency",
                        "tool operation invocation including payload generation to terminal observation",
                        "scheduledLatency",
                        "original planned arrival to terminal observation; no synthetic missed-arrival latency samples",
                        "schedulingLag",
                        "planned arrival to tool invocation, including locally rejected attempts",
                        "population",
                        "all admitted terminal outcomes except unfinished requests; warmup and measurement are separate",
                        "histogram",
                        "HDR, three significant digits, microseconds rounded upward, range 0..one hour; overflow counted separately",
                        "writeCompletion",
                        "not exposed by request handles; transmission certainty is reported without assuming peer acceptance"));
        report.put(
                "receiverCohort",
                "application-observed requests entering handlers before receiver closure, including peer warmup/drain; excludes frames rejected by decoding or endpoint capacity before handler invocation");
        report.put(
                "limits",
                Map.of(
                        "toolPendingCalls",
                        config.connections() * config.window(),
                        "receiverStreams",
                        config.connections(),
                        "delayDecisions",
                        config.connections() * config.window(),
                        "peerStatusCategories",
                        256,
                        "maximumPduBytes",
                        1_048_576,
                        "handlerConcurrency",
                        Math.min(64, config.connections() * config.window()),
                        "handlerQueue",
                        config.connections() * config.window(),
                        "handlerTimeoutNanos",
                        config.load().requestTimeout().plusSeconds(1).toNanos()));
        report.put(
                "criteria",
                Map.of(
                        "expectFailures",
                        config.expectFailures(),
                        "minimumSuccessfulRateRatio",
                        config.minimumRateRatio(),
                        "maximumScheduledP99Millis",
                        config.maximumP99Millis()));
        report.put("cleanupComplete", cleanup);
        report.put("failures", List.copyOf(failures));
        report.put("passed", failures.isEmpty());
        return report;
    }
}
