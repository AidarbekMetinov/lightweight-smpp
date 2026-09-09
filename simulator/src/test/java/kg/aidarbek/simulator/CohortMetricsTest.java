package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class CohortMetricsTest {
    @Test
    void accountsForSkippedRejectedNegativeTimedOutAndUnfinishedWorkWithoutMixingPhases() {
        var metrics = new CohortMetrics(7);
        metrics.skipped(2);
        metrics.attempt(1000);
        metrics.rejected("WINDOW_FULL");
        for (int i = 0; i < 4; i++) {
            metrics.attempt(1_000_000);
            metrics.admitted();
        }
        metrics.terminal(CohortMetrics.Outcome.SUCCESS, 0, true, 1_000_000, 0, 2_000_000, true);
        metrics.terminal(CohortMetrics.Outcome.PEER_NEGATIVE, 0x58, true, 1_000_000, 0, 3_000_000, true);
        metrics.terminal(CohortMetrics.Outcome.TIMEOUT, -1, true, 1_000_000, 0, 10_000_000, false);
        metrics.terminal(CohortMetrics.Outcome.UNFINISHED, -1, false, 1_000_000, 0, 20_000_000, false);
        var result = metrics.snapshot();
        assertEquals(7, result.planned());
        assertEquals(5, result.attempted());
        assertEquals(1, result.rejected());
        assertEquals(4, result.admitted());
        assertEquals(4, result.peakPending());
        assertEquals(0, result.pending());
        assertEquals(2, result.completionsDuringMeasurement());
        assertEquals(3, result.mayHaveBeenSent());
        assertEquals(1, result.outcomes().get(CohortMetrics.Outcome.TIMEOUT));
        assertEquals(1, result.statuses().get(0x58L));
        assertEquals(3, result.invocationLatency().count());
        assertEquals(3, result.scheduledLatency().count());
        assertEquals(5, result.schedulingLag().count());
        assertTrue(result.balanced());
        assertThrows(
                UnsupportedOperationException.class, () -> result.outcomes().clear());
    }

    @Test
    void histogramKeepsOverflowAndMergeablePopulationSeparate() {
        var latencies = new Latencies();
        latencies.record(1_000_000);
        latencies.record(2_000_000);
        latencies.record(10_000_000);
        latencies.record(3_600_000_000_001L);
        var result = latencies.snapshot();
        assertEquals(3, result.count());
        assertEquals(1, result.overflow());
        assertEquals(3_600_000_000_001L, result.maximumNanos());
        assertTrue(result.p50Micros() >= 2000 && result.p50Micros() < 2010);
        assertTrue(result.p99Micros() >= 10000 && result.p99Micros() < 10020);
        assertEquals(
                3, result.buckets().stream().mapToLong(Latencies.Bucket::count).sum());
        assertThrows(IllegalArgumentException.class, () -> latencies.record(-1));
    }

    @Test
    void boundsPeerStatusCardinalityAndRejectsImpossibleTransitions() {
        var metrics = new CohortMetrics(300);
        for (int i = 0; i < 300; i++) {
            metrics.attempt(0);
            metrics.admitted();
            metrics.terminal(CohortMetrics.Outcome.PEER_NEGATIVE, i + 1, true, 0, 0, 1, true);
        }
        assertEquals(256, metrics.snapshot().statuses().size());
        assertEquals(44, metrics.snapshot().excessStatuses());
        assertTrue(metrics.snapshot().balanced());
        assertThrows(IllegalStateException.class, metrics::admitted);
        assertThrows(IllegalArgumentException.class, () -> metrics.skipped(-1));
    }
}
