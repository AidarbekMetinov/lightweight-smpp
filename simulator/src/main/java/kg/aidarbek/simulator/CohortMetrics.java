package kg.aidarbek.simulator;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded aggregate accounting for one warmup or measurement cohort. */
final class CohortMetrics {
    public enum Outcome {
        SUCCESS,
        PEER_NEGATIVE,
        GENERIC_NACK,
        TIMEOUT,
        CANCELLED,
        LOCAL_FAILURE,
        UNFINISHED
    }

    private long planned,
            skipped,
            attempted,
            rejected,
            admitted,
            pending,
            peakPending,
            completions,
            successes,
            maybeSent,
            excessStatuses;
    private final Map<Outcome, Long> outcomes = new EnumMap<>(Outcome.class);
    private final Map<String, Long> rejections = new LinkedHashMap<>();
    private final Map<Long, Long> statuses = new LinkedHashMap<>();
    private final Latencies invocation = new Latencies();
    private final Latencies scheduled = new Latencies();
    private final Latencies lag = new Latencies();

    public CohortMetrics(long planned) {
        if (planned < 0 || planned > 1_000_000_000L) throw new IllegalArgumentException("Invalid planned count");
        this.planned = planned;
    }

    public synchronized void planOne() {
        if (planned == 1_000_000_000L) throw new IllegalStateException("Planned count limit reached");
        planned++;
    }

    public synchronized void skipped(long count) {
        if (count < 0 || count > planned - skipped - attempted)
            throw new IllegalArgumentException("Invalid skipped count");
        skipped += count;
    }

    public synchronized void attempt(long lagNanos) {
        if (attempted + skipped >= planned) throw new IllegalStateException("No planned arrival available");
        lag.record(lagNanos);
        attempted++;
    }

    public synchronized void rejected(String reason) {
        if (attempted <= rejected + admitted) throw new IllegalStateException("No unclassified attempt");
        if (reason == null || reason.isEmpty()) throw new IllegalArgumentException("Rejection category required");
        rejected++;
        String category = rejections.containsKey(reason) || rejections.size() < 32 ? reason : "OTHER";
        rejections.merge(category, 1L, Long::sum);
    }

    public synchronized void admitted() {
        if (attempted <= rejected + admitted) throw new IllegalStateException("No unclassified attempt");
        admitted++;
        peakPending = Math.max(peakPending, ++pending);
    }

    public synchronized void terminal(
            Outcome outcome,
            long status,
            boolean mayHaveBeenSent,
            long invocationNanos,
            long scheduledNanos,
            long finishedNanos,
            boolean duringMeasurement) {
        if (outcome == null || pending == 0) throw new IllegalStateException("No admitted pending work");
        if (finishedNanos - invocationNanos < 0 || finishedNanos - scheduledNanos < 0)
            throw new IllegalArgumentException("Terminal precedes invocation or schedule");
        pending--;
        outcomes.merge(outcome, 1L, Long::sum);
        if (mayHaveBeenSent) maybeSent++;
        if (outcome != Outcome.UNFINISHED) {
            invocation.record(finishedNanos - invocationNanos);
            scheduled.record(finishedNanos - scheduledNanos);
            if (duringMeasurement) {
                completions++;
                if (outcome == Outcome.SUCCESS) successes++;
            }
        }
        if (status >= 0) {
            if (statuses.containsKey(status) || statuses.size() < 256) statuses.merge(status, 1L, Long::sum);
            else excessStatuses++;
        }
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                planned,
                skipped,
                attempted,
                rejected,
                admitted,
                pending,
                peakPending,
                completions,
                successes,
                maybeSent,
                outcomes,
                rejections,
                statuses,
                excessStatuses,
                invocation.snapshot(),
                scheduled.snapshot(),
                lag.snapshot());
    }

    public record Snapshot(
            long planned,
            long skipped,
            long attempted,
            long rejected,
            long admitted,
            long pending,
            long peakPending,
            long completionsDuringMeasurement,
            long successesDuringMeasurement,
            long mayHaveBeenSent,
            Map<Outcome, Long> outcomes,
            Map<String, Long> rejections,
            Map<Long, Long> statuses,
            long excessStatuses,
            Latencies.Snapshot invocationLatency,
            Latencies.Snapshot scheduledLatency,
            Latencies.Snapshot schedulingLag) {
        public Snapshot {
            outcomes = Map.copyOf(outcomes);
            rejections = Map.copyOf(rejections);
            statuses = Map.copyOf(statuses);
        }

        public boolean balanced() {
            return planned == skipped + attempted
                    && attempted == rejected + admitted
                    && admitted
                            == pending
                                    + outcomes.values().stream()
                                            .mapToLong(Long::longValue)
                                            .sum();
        }
    }
}
