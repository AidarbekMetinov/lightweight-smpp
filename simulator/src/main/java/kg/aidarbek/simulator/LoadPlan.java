package kg.aidarbek.simulator;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Finite aggregate originating traffic, measured separately from warmup and drain. */
record LoadPlan(
        Model model,
        List<Integer> rates,
        long count,
        Duration warmup,
        Duration duration,
        Duration drain,
        Duration requestTimeout,
        List<Duration> holds) {
    LoadPlan(
            Model model,
            List<Integer> rates,
            long count,
            Duration warmup,
            Duration duration,
            Duration drain,
            Duration requestTimeout) {
        this(model, rates, count, warmup, duration, drain, requestTimeout, List.of());
    }

    public LoadPlan {
        Objects.requireNonNull(model, "model");
        rates = List.copyOf(rates);
        holds = List.copyOf(holds);
        if (count < 1 || count > 1_000_000_000L) throw new IllegalArgumentException("Count must be 1..1000000000");
        if (rates.size() > 64 || rates.stream().anyMatch(rate -> rate < 1 || rate > 1_000_000))
            throw new IllegalArgumentException("At most 64 rates, each 1..1000000 per second, are supported");
        if ((model == Model.ARRIVAL_RATE) == rates.isEmpty())
            throw new IllegalArgumentException("Arrival mode requires rates; concurrency mode must not declare rates");
        bounded(warmup, true, Duration.ofDays(1));
        if (model == Model.ARRIVAL_RATE && !warmup.isZero() && warmup.toNanos() < 1_000_000)
            throw new IllegalArgumentException("A nonzero arrival warmup must last at least one millisecond");
        bounded(duration, false, Duration.ofDays(1));
        bounded(drain, false, Duration.ofHours(1));
        bounded(requestTimeout, false, Duration.ofHours(1));
        if (!rates.isEmpty() && duration.toNanos() / rates.size() < 1_000_000)
            throw new IllegalArgumentException("Every rate step must last at least one millisecond");
        if (holds.isEmpty() && !rates.isEmpty()) {
            var equal = new ArrayList<Duration>();
            long length = duration.toNanos() / rates.size();
            for (int index = 0; index < rates.size(); index++)
                equal.add(Duration.ofNanos(index == rates.size() - 1 ? duration.toNanos() - index * length : length));
            holds = List.copyOf(equal);
        }
        if (holds.size() != rates.size()) throw new IllegalArgumentException("Each rate requires one hold duration");
        long held = 0;
        for (Duration hold : holds) {
            bounded(hold, false, Duration.ofDays(1));
            if (hold.toNanos() < 1_000_000)
                throw new IllegalArgumentException("Every hold must last at least one millisecond");
            held += hold.toNanos();
        }
        if (!holds.isEmpty() && held != duration.toNanos())
            throw new IllegalArgumentException("Rate hold durations must sum to the measurement duration");
    }

    static void bounded(Duration value, boolean zeroAllowed, Duration maximum) {
        Objects.requireNonNull(value, "duration");
        if (value.isNegative() || (!zeroAllowed && value.isZero()) || value.compareTo(maximum) > 0)
            throw new IllegalArgumentException("Duration is outside its finite supported bounds");
    }

    /** Selects independent arrivals or refill of the bounded request window. */
    public enum Model {
        ARRIVAL_RATE,
        FIXED_CONCURRENCY
    }
}
