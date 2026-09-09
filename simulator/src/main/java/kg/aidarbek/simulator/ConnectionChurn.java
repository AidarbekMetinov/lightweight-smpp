package kg.aidarbek.simulator;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.IntPredicate;

/** Single-owner finite replacement schedule; missed churn slots never form a retry backlog. */
final class ConnectionChurn {
    private final int connections;
    private final LoadPlan plan;
    private final IntPredicate initiate;
    private ArrivalSchedule schedule;
    private long skipped, attempted, initiated, busy, failed;
    private boolean stopped;

    ConnectionChurn(int connections, int rate, long count, Duration duration, IntPredicate initiate) {
        if (connections < 1 || connections > 4096 || rate < 1 || rate > 1000 || count < 1 || count > 1_000_000)
            throw new IllegalArgumentException("Churn requires finite connection/rate/event bounds");
        this.connections = connections;
        this.initiate = Objects.requireNonNull(initiate, "initiate");
        plan = new LoadPlan(
                LoadPlan.Model.ARRIVAL_RATE,
                List.of(rate),
                count,
                Duration.ZERO,
                duration,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1));
    }

    void start(long now) {
        if (schedule != null || stopped) throw new IllegalStateException("Churn can start once before stop");
        schedule = new ArrivalSchedule(plan, now);
    }

    void advance(long now) {
        if (schedule == null || stopped) return;
        schedule.poll(now).ifPresent(arrival -> {
            skipped += arrival.skippedBefore();
            attempted++;
            try {
                if (initiate.test((int) (arrival.index() % connections))) initiated++;
                else busy++;
            } catch (RuntimeException failure) {
                failed++;
            }
        });
    }

    void stop() {
        if (!stopped && schedule != null) skipped += schedule.finish();
        stopped = true;
    }

    Snapshot snapshot() {
        return new Snapshot(
                schedule == null ? 0 : schedule.plannedCount(),
                skipped,
                attempted,
                initiated,
                busy,
                failed,
                schedule != null,
                stopped);
    }

    record Snapshot(
            long planned,
            long skipped,
            long attempted,
            long initiated,
            long busy,
            long failed,
            boolean started,
            boolean stopped) {}
}
