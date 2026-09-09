package kg.aidarbek.simulator;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import kg.aidarbek.smpp.profile.SmppVersion;

/** Validated finite loopback fixture policy, separate from throughput simulator configuration. */
record ReceiptScenarioOptions(
        boolean server,
        int port,
        SmppVersion version,
        int count,
        int window,
        int rejectEvery,
        Fault fault,
        Duration timeout,
        Duration duration,
        Duration drain,
        Path report) {
    ReceiptScenarioOptions {
        if (version == null
                || fault == null
                || report == null
                || count < 1
                || count > 10000
                || window < 1
                || window > 32
                || rejectEvery < 0
                || rejectEvery > count
                || port < (server ? 0 : 1)
                || port > 65535) throw new IllegalArgumentException("Invalid finite receipt scenario configuration");
        bounded(timeout, 30);
        bounded(duration, 120);
        bounded(drain, 30);
        if (duration.compareTo(timeout.multipliedBy(2)) < 0)
            throw new IllegalArgumentException("Overall duration must allow two request timeouts");
        if (fault != Fault.NONE && (!server || rejectEvery == 1))
            throw new IllegalArgumentException("Receipt faults require a server with positive submissions");
    }

    static ReceiptScenarioOptions parse(String[] arguments) {
        if (arguments.length < 1 || (!arguments[0].equals("server") && !arguments[0].equals("client")))
            throw new IllegalArgumentException(
                    "Usage: ReceiptScenario server|client --report=fresh-directory [--version=3.4|5.0 --port=N --count=N --window=N --reject-every=N --fault=none|missing|duplicate|mismatch --timeout=PT2S --duration=PT15S --drain=PT2S]");
        Set<String> allowed = Set.of(
                "port",
                "version",
                "count",
                "window",
                "reject-every",
                "fault",
                "timeout",
                "duration",
                "drain",
                "report");
        Map<String, String> values = new HashMap<>();
        for (int index = 1; index < arguments.length; index++) {
            String argument = arguments[index];
            int separator = argument.indexOf('=');
            if (!argument.startsWith("--") || separator < 3 || separator == argument.length() - 1)
                throw new IllegalArgumentException("Options require --name=value");
            String key = argument.substring(2, separator);
            if (!allowed.contains(key) || values.putIfAbsent(key, argument.substring(separator + 1)) != null)
                throw new IllegalArgumentException("Unknown or repeated option: " + key);
        }
        if (!values.containsKey("report")) throw new IllegalArgumentException("A fresh report directory is required");
        SmppVersion version =
                switch (values.getOrDefault("version", "3.4")) {
                    case "3.4" -> SmppVersion.V3_4;
                    case "5.0" -> SmppVersion.V5_0;
                    default -> throw new IllegalArgumentException("Expected version 3.4 or 5.0");
                };
        return new ReceiptScenarioOptions(
                arguments[0].equals("server"),
                Integer.parseInt(values.getOrDefault("port", "0")),
                version,
                Integer.parseInt(values.getOrDefault("count", "8")),
                Integer.parseInt(values.getOrDefault("window", "4")),
                Integer.parseInt(values.getOrDefault("reject-every", "0")),
                Fault.valueOf(values.getOrDefault("fault", "none").toUpperCase(Locale.ROOT)),
                Duration.parse(values.getOrDefault("timeout", "PT2S")),
                Duration.parse(values.getOrDefault("duration", "PT15S")),
                Duration.parse(values.getOrDefault("drain", "PT2S")),
                Path.of(values.get("report")));
    }

    private static void bounded(Duration duration, int maximumSeconds) {
        if (duration == null
                || duration.isNegative()
                || duration.isZero()
                || duration.compareTo(Duration.ofSeconds(maximumSeconds)) > 0)
            throw new IllegalArgumentException("Duration outside finite scenario bounds");
    }

    enum Fault {
        NONE,
        MISSING,
        DUPLICATE,
        MISMATCH
    }
}
