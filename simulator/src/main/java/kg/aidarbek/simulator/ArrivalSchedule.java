package kg.aidarbek.simulator;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** A single-owner virtual schedule; one poll emits at most the newest due arrival, never a catch-up burst. */
final class ArrivalSchedule {
    private static final long SECOND = 1_000_000_000L;
    private final int[] rates;
    private final long[] starts;
    private final long[] counts;
    private final long duration;
    private final long started;
    private final long total;
    private long cursor;

    ArrivalSchedule(LoadPlan plan, long started) {
        if (plan.model() != LoadPlan.Model.ARRIVAL_RATE)
            throw new IllegalArgumentException("An arrival plan is required");
        this.rates = plan.rates().stream().mapToInt(Integer::intValue).toArray();
        this.duration = plan.duration().toNanos();
        this.started = started;
        starts = new long[this.rates.length];
        counts = new long[this.rates.length];
        long planned = 0;
        long offset = 0;
        for (int step = 0; step < this.rates.length; step++) {
            starts[step] = offset;
            long stepLength = plan.holds().get(step).toNanos();
            counts[step] = Math.min(plan.count() - planned, ceilingRateProduct(stepLength, this.rates[step]));
            planned += counts[step];
            offset += stepLength;
        }
        total = planned;
    }

    public ArrivalSchedule(List<Integer> rates, Duration duration, long limit, long started) {
        this(
                new LoadPlan(
                        LoadPlan.Model.ARRIVAL_RATE,
                        rates,
                        limit,
                        Duration.ZERO,
                        duration,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1)),
                started);
    }

    public Optional<Arrival> poll(long now) {
        long elapsed = now - started;
        if (elapsed < 0 || elapsed >= duration || cursor == total) return Optional.empty();
        long due = 0;
        for (int step = 0; step < rates.length && starts[step] <= elapsed; step++) {
            long offset = elapsed - starts[step];
            long through = offset / SECOND * rates[step] + offset % SECOND * rates[step] / SECOND + 1;
            due += Math.min(counts[step], through);
        }
        if (due <= cursor) return Optional.empty();
        long index = due - 1;
        Arrival arrival = new Arrival(index, plannedTime(index), index - cursor);
        cursor = due;
        return Optional.of(arrival);
    }

    public long finish() {
        long skipped = total - cursor;
        cursor = total;
        return skipped;
    }

    public long plannedCount() {
        return total;
    }

    long plannedCount(int step) {
        return counts[step];
    }

    long endNanos(int step) {
        return started + (step + 1 < starts.length ? starts[step + 1] : duration);
    }

    int stepOf(long index) {
        if (index < 0 || index >= total) throw new IllegalArgumentException("Arrival identity is outside the schedule");
        for (int step = 0; step < counts.length; step++) {
            if (index < counts[step]) return step;
            index -= counts[step];
        }
        throw new IllegalStateException("Schedule count mismatch");
    }

    public long nextNanos() {
        return cursor == total ? started + duration : plannedTime(cursor);
    }

    private long plannedTime(long index) {
        for (int step = 0; step < rates.length; step++) {
            if (index < counts[step]) {
                long seconds = index / rates[step];
                long numerator = index % rates[step] * SECOND;
                long rounded = numerator / rates[step] + (numerator % rates[step] == 0 ? 0 : 1);
                return started + starts[step] + seconds * SECOND + rounded;
            }
            index -= counts[step];
        }
        throw new IllegalArgumentException("Arrival index is outside this finite schedule");
    }

    private static long ceilingRateProduct(long duration, int rate) {
        long tail = duration % SECOND * rate;
        return duration / SECOND * rate + tail / SECOND + (tail % SECOND == 0 ? 0 : 1);
    }

    /** Original planned identity/time and arrivals skipped immediately before it. */
    public record Arrival(long index, long plannedNanos, long skippedBefore) {}
}
