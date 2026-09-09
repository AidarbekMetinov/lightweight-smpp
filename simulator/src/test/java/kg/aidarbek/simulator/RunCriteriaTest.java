package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RunCriteriaTest {
    private static SimulatorConfig config(String... extras) {
        var args = new ArrayList<>(List.of("client", "--revision=e79b9f48d7f2c94aad96b2bb734abe338d420a15"));
        args.addAll(List.of(extras));
        return SimulatorArguments.parse(args.toArray(String[]::new));
    }

    @Test
    void expectsSuccessfulAccountingByDefaultButCanAdmitDeclaredFaultRuns() {
        var metrics = new CohortMetrics(1);
        metrics.attempt(0);
        metrics.admitted();
        metrics.terminal(CohortMetrics.Outcome.PEER_NEGATIVE, 0x58, true, 0, 0, 10_000, true);
        var run = new TrafficRunner.Result(new CohortMetrics(0).snapshot(), metrics.snapshot(), 0, 1_000_000_000, 0);
        assertFalse(RunCriteria.evaluate(config(), run, true, 0, 0).passed());
        assertTrue(RunCriteria.evaluate(config("--expect-failures=true"), run, true, 0, 0)
                .passed());
        assertFalse(RunCriteria.evaluate(config("--expect-failures=true"), run, false, 0, 0)
                .passed());
        assertFalse(RunCriteria.evaluate(config("--expect-failures=true"), run, true, 1, 0)
                .passed());
    }

    @Test
    void throughputCountsOnlySuccessfulMeasurementCompletionsAndLatencyIncludesQueueing() {
        var metrics = new CohortMetrics(2);
        for (int i = 0; i < 2; i++) {
            metrics.attempt(0);
            metrics.admitted();
        }
        metrics.terminal(CohortMetrics.Outcome.SUCCESS, 0, true, 90_000_000, 0, 100_000_000, true);
        metrics.terminal(CohortMetrics.Outcome.SUCCESS, 0, true, 90_000_000, 0, 1_100_000_000, false);
        var run = new TrafficRunner.Result(
                new CohortMetrics(0).snapshot(), metrics.snapshot(), 0, 1_000_000_000, 100_000_000);
        assertFalse(RunCriteria.evaluate(config("--minimum-rate-ratio=0.99"), run, true, 0, 0)
                .passed());
        assertFalse(RunCriteria.evaluate(config("--p99-ms=20"), run, true, 0, 0).passed());
        assertTrue(RunCriteria.evaluate(config(), run, true, 0, 0).passed());
    }

    @Test
    void unfinishedWorkAndMissingContentAlwaysFail() {
        var metrics = new CohortMetrics(1);
        metrics.attempt(0);
        metrics.admitted();
        metrics.terminal(CohortMetrics.Outcome.UNFINISHED, -1, true, 0, 0, 1, false);
        var run = new TrafficRunner.Result(new CohortMetrics(0).snapshot(), metrics.snapshot(), 0, 1, 1);
        assertFalse(RunCriteria.evaluate(config("--expect-failures=true"), run, true, 0, 0)
                .passed());
        assertFalse(RunCriteria.evaluate(config("--expect-failures=true"), run, true, 0, 1)
                .passed());
    }
}
