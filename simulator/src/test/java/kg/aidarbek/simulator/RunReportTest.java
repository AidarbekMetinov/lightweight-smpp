package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunReportTest {
    @TempDir
    Path directory;

    @Test
    void distinguishesOfferedAchievedAndDrainSuccessWithoutInventingAConcurrencyRate() throws Exception {
        var config = SimulatorArguments.parse(
                "client",
                "--revision=0123456789012345678901234567890123456789",
                "--rates=10",
                "--duration=PT0.2S",
                "--count=2");
        var metrics = new CohortMetrics(2);
        metrics.attempt(0);
        metrics.admitted();
        metrics.attempt(0);
        metrics.admitted();
        metrics.terminal(CohortMetrics.Outcome.SUCCESS, 0, true, 0, 0, 100_000_000, true);
        metrics.terminal(CohortMetrics.Outcome.SUCCESS, 0, true, 100_000_000, 100_000_000, 250_000_000, false);
        var traffic = new TrafficRunner.Result(
                new CohortMetrics(0).snapshot(), metrics.snapshot(), 0, 200_000_000, 50_000_000);
        var receiver = new ReplyController.Snapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        try (var writer = new ReportWriter(directory)) {
            var sampler =
                    new ResourceSampler(writer, () -> new RunEnvironment.Snapshot(1, 2, -1, -1, -1, 0, -1, 1), () -> 0);
            var report = RunReport.create(
                    config, Instant.EPOCH, Map.of(), traffic, receiver, sampler.summary(), true, List.of());
            assertInstanceOf(Map.class, report.get("performance"));
            var performance = (Map<?, ?>) report.get("performance");
            assertEquals(10.0, performance.get("offeredRequestsPerSecond"));
            assertEquals(10.0, performance.get("attemptedRequestsPerSecond"));
            assertEquals(5.0, performance.get("successfulCompletionsPerSecond"));
            assertEquals(true, performance.get("offeredLoadEstablished"));
            assertEquals(2, report.get("schema"));
            assertTrue(Json.encode(report).contains("\"spinNanos\":100000"));
            var closed = SimulatorArguments.parse(
                    "client", "--revision=0123456789012345678901234567890123456789", "--model=concurrency");
            var closedReport = RunReport.create(
                    closed, Instant.EPOCH, Map.of(), traffic, receiver, sampler.summary(), true, List.of());
            assertNull(((Map<?, ?>) closedReport.get("performance")).get("offeredRequestsPerSecond"));
            var listening = SimulatorArguments.parse(
                    "server", "--revision=0123456789012345678901234567890123456789", "--operation=none");
            var listeningReport = RunReport.create(
                    listening, Instant.EPOCH, Map.of(), traffic, receiver, sampler.summary(), true, List.of());
            var listeningPerformance = (Map<?, ?>) listeningReport.get("performance");
            assertNull(listeningPerformance.get("offeredRequestsPerSecond"));
            assertEquals(false, listeningPerformance.get("offeredLoadEstablished"));
        }
    }
}
