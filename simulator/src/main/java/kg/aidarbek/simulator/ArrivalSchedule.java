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

    public ArrivalSchedule(List<Integer> rates, Duration duration, long limit, long started) {
        LoadPlan checked = new LoadPlan(
                LoadPlan.Model.ARRIVAL_RATE,
                rates,
                limit,
                Duration.ZERO,
                duration,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1));
        this.rates = checked.rates().stream().mapToInt(Integer::intValue).toArray();
        this.duration = duration.toNanos();
        this.started = started;
        starts = new long[this.rates.length];
        counts = new long[this.rates.length];
        long length = this.duration / this.rates.length;
        long planned = 0;
        for (int step = 0; step < this.rates.length; step++) {
            starts[step] = step * length;
            long stepLength = step == this.rates.length - 1 ? this.duration - starts[step] : length;
            counts[step] = Math.min(limit - planned, ceilingRateProduct(stepLength, this.rates[step]));
            planned += counts[step];
        }
        total = planned;
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
