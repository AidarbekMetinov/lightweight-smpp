package kg.aidarbek.simulator;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;

/** Immutable non-secret run inputs; rates and count apply across all originating connections. */
record SimulatorConfig(
        Mode mode,
        String host,
        int port,
        SmppVersion version,
        BindMode bindMode,
        int connections,
        int window,
        Duration connectInterval,
        String operation,
        String content,
        LoadPlan load,
        int payloadBytes,
        long seed,
        FaultPolicy faults,
        Path report,
        String runId,
        String revision,
        String source,
        String destination,
        boolean expectFailures,
        double minimumRateRatio,
        long maximumP99Millis) {
    public SimulatorConfig {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(bindMode, "bindMode");
        Objects.requireNonNull(load, "load");
        Objects.requireNonNull(faults, "faults");
        Objects.requireNonNull(report, "report");
        if (host == null
                || host.isBlank()
                || host.length() > 253
                || host.chars().anyMatch(Character::isWhitespace))
            throw new IllegalArgumentException("Host must be a nonblank address or name");
        if (port < (mode == Mode.CLIENT ? 1 : 0) || port > 65535)
            throw new IllegalArgumentException("Port is outside the supported range");
        if (connections < 1
                || connections > 4096
                || window < 1
                || window > 65536
                || (long) connections * window > 65536)
            throw new IllegalArgumentException("Connections/window exceed the finite simulator work limit");
        if (payloadBytes < 0
                || payloadBytes > 65535
                || (long) connections * window * (payloadBytes + 1024L) > 128L * 1024 * 1024)
            throw new IllegalArgumentException("Payload or estimated outstanding-byte budget exceeds simulator limits");
        LoadPlan.bounded(connectInterval, true, Duration.ofSeconds(10));
        if (operation == null || !operation.matches("[a-z][a-z0-9-]{0,31}"))
            throw new IllegalArgumentException("Operation name is invalid");
        if (content == null || !content.matches("[a-z][a-z0-9-]{0,31}"))
            throw new IllegalArgumentException("Content name is invalid");
        if (runId == null || !runId.matches("[A-Za-z0-9_.-]{1,96}"))
            throw new IllegalArgumentException("Run identity is invalid");
        if (revision == null || !revision.matches("[0-9a-f]{40}([+][0-9a-f]{64})?"))
            throw new IllegalArgumentException(
                    "Revision must be a full Git SHA, optionally plus a dirty-source SHA256");
        for (String address : new String[] {source, destination}) {
            if (address == null
                    || address.isEmpty()
                    || address.length() > 20
                    || address.chars().anyMatch(character -> character == 0 || character > 127))
                throw new IllegalArgumentException("Traffic addresses must contain 1..20 non-NUL ASCII characters");
        }
        if (!Double.isFinite(minimumRateRatio)
                || minimumRateRatio < 0
                || minimumRateRatio > 1
                || maximumP99Millis < 0
                || maximumP99Millis > 3_600_000)
            throw new IllegalArgumentException("Invalid throughput or latency criterion");
    }

    public enum Mode {
        CLIENT,
        SERVER
    }
}
