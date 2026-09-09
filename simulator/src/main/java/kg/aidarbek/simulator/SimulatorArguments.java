package kg.aidarbek.simulator;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;

/** Parses explicit CLI values without starting sockets, workers or report files. */
final class SimulatorArguments {
    private static final Set<String> OPTIONS = Set.of(
            "host",
            "port",
            "version",
            "bind",
            "connections",
            "window",
            "connect-interval",
            "operation",
            "content",
            "model",
            "rates",
            "count",
            "duration",
            "warmup",
            "drain",
            "timeout",
            "payload",
            "seed",
            "reject",
            "delay",
            "stall",
            "delay-duration",
            "reject-status",
            "disconnect-after",
            "report",
            "run-id",
            "revision",
            "source",
            "destination",
            "expect-failures",
            "minimum-rate-ratio",
            "p99-ms",
            "holds",
            "scenario",
            "spin",
            "churn-rate",
            "churn-count",
            "consumer-delay",
            "sample",
            "verify-fault-mix",
            "fault-tolerance",
            "max-rss-mib",
            "max-heap-mib",
            "max-fd-growth",
            "max-thread-growth");

    private SimulatorArguments() {}

    public static SimulatorConfig parse(String... arguments) {
        if (arguments.length == 0) throw new IllegalArgumentException("Choose client or server");
        SimulatorConfig.Mode mode =
                switch (arguments[0]) {
                    case "client" -> SimulatorConfig.Mode.CLIENT;
                    case "server" -> SimulatorConfig.Mode.SERVER;
                    default -> throw new IllegalArgumentException("Choose client or server");
                };
        Map<String, String> values = new LinkedHashMap<>();
        for (int index = 1; index < arguments.length; index++) {
            String argument = arguments[index];
            int separator = argument.indexOf('=');
            if (!argument.startsWith("--") || separator < 3 || separator == argument.length() - 1)
                throw new IllegalArgumentException("Use --option=value arguments");
            String key = argument.substring(2, separator);
            if (!OPTIONS.contains(key) && !LifecycleSettings.optionNames().contains(key))
                throw new IllegalArgumentException("Unknown simulator option: " + key);
            if (values.putIfAbsent(key, argument.substring(separator + 1)) != null)
                throw new IllegalArgumentException("Duplicate simulator option: " + key);
        }
        LoadPlan.Model model =
                switch (values.getOrDefault("model", "arrival")) {
                    case "arrival" -> LoadPlan.Model.ARRIVAL_RATE;
                    case "concurrency" -> LoadPlan.Model.FIXED_CONCURRENCY;
                    default -> throw new IllegalArgumentException("Model must be arrival or concurrency");
                };
        List<Integer> rates = !values.containsKey("rates") && model == LoadPlan.Model.FIXED_CONCURRENCY
                ? List.of()
                : Arrays.stream(values.getOrDefault("rates", "10").split(",", -1))
                        .map(Integer::parseInt)
                        .toList();
        long seed = Long.parseLong(values.getOrDefault("seed", "1"));
        String runId = values.getOrDefault("run-id", UUID.randomUUID().toString());
        LoadPlan load = new LoadPlan(
                model,
                rates,
                Long.parseLong(values.getOrDefault("count", "100")),
                duration(values, "warmup", "PT0S"),
                duration(values, "duration", "PT10S"),
                duration(values, "drain", "PT30S"),
                duration(values, "timeout", "PT2S"),
                values.containsKey("holds")
                        ? Arrays.stream(values.get("holds").split(",", -1))
                                .map(Duration::parse)
                                .toList()
                        : List.of());
        FaultPolicy faults = new FaultPolicy(
                seed,
                integer(values, "reject", 0),
                integer(values, "delay", 0),
                integer(values, "stall", 0),
                duration(values, "delay-duration", "PT0.2S"),
                Long.decode(values.getOrDefault("reject-status", "0x58")),
                Long.parseLong(values.getOrDefault("disconnect-after", "0")));
        return new SimulatorConfig(
                mode,
                values.getOrDefault("host", "127.0.0.1"),
                integer(values, "port", 2775),
                switch (values.getOrDefault("version", "3.4")) {
                    case "3.4" -> SmppVersion.V3_4;
                    case "5.0" -> SmppVersion.V5_0;
                    default -> throw new IllegalArgumentException("Version must be 3.4 or 5.0");
                },
                switch (values.getOrDefault("bind", "trx")) {
                    case "rx" -> BindMode.RECEIVER;
                    case "tx" -> BindMode.TRANSMITTER;
                    case "trx" -> BindMode.TRANSCEIVER;
                    default -> throw new IllegalArgumentException("Bind must be rx, tx or trx");
                },
                integer(values, "connections", 1),
                integer(values, "window", 32),
                duration(values, "connect-interval", "PT0S"),
                values.getOrDefault("operation", mode == SimulatorConfig.Mode.CLIENT ? "submit" : "none"),
                values.getOrDefault("content", "raw"),
                load,
                integer(values, "payload", 160),
                seed,
                faults,
                Path.of(values.getOrDefault("report", "build/simulator/" + runId)),
                runId,
                values.get("revision"),
                values.getOrDefault("source", "1000"),
                values.getOrDefault("destination", "2000"),
                bool(values.getOrDefault("expect-failures", "false")),
                Double.parseDouble(values.getOrDefault("minimum-rate-ratio", "0")),
                Long.parseLong(values.getOrDefault("p99-ms", "0")),
                new LoadSettings(
                        values.getOrDefault("scenario", "custom"),
                        duration(values, "spin", "PT0.0001S"),
                        integer(values, "churn-rate", 0),
                        Long.parseLong(values.getOrDefault(
                                "churn-count", integer(values, "churn-rate", 0) == 0 ? "0" : "1000000")),
                        duration(values, "consumer-delay", "PT0S"),
                        duration(values, "sample", "PT1S"),
                        bool(values.getOrDefault("verify-fault-mix", "false")),
                        Double.parseDouble(values.getOrDefault("fault-tolerance", "0.05")),
                        (long) integer(values, "max-rss-mib", 0) * 1_048_576,
                        (long) integer(values, "max-heap-mib", 0) * 1_048_576,
                        Long.parseLong(values.getOrDefault("max-fd-growth", "-1")),
                        Long.parseLong(values.getOrDefault("max-thread-growth", "-1"))),
                LifecycleSettings.parse(values));
    }

    private static int integer(Map<String, String> values, String name, int fallback) {
        return Integer.parseInt(values.getOrDefault(name, Integer.toString(fallback)));
    }

    private static Duration duration(Map<String, String> values, String name, String fallback) {
        return Duration.parse(values.getOrDefault(name, fallback));
    }

    private static boolean bool(String value) {
        return switch (value) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("Boolean options require true or false");
        };
    }
}
