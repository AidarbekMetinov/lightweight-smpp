package kg.aidarbek.simulator;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongSupplier;
import kg.aidarbek.smpp.request.RequestFailure;

/** Single-owner bounded traffic generation and terminal observation, using an injected monotonic clock. */
final class TrafficRunner {
    private final LoadPlan plan;
    private final int maximumPending;
    private final LongFunction<PendingCall> source;
    private final LongSupplier clock;
    private final LongConsumer pause;
    private final Runnable maintenance;

    public TrafficRunner(
            LoadPlan plan,
            int maximumPending,
            LongFunction<PendingCall> source,
            LongSupplier clock,
            LongConsumer pause,
            Runnable maintenance) {
        this.plan = Objects.requireNonNull(plan, "plan");
        if (maximumPending < 1 || maximumPending > 65536) throw new IllegalArgumentException("Invalid pending bound");
        this.maximumPending = maximumPending;
        this.source = Objects.requireNonNull(source, "source");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.pause = Objects.requireNonNull(pause, "pause");
        this.maintenance = Objects.requireNonNull(maintenance, "maintenance");
    }

    public Result run() {
        Phase warmup = plan.warmup().isZero()
                ? new Phase(new CohortMetrics(0).snapshot(), 0, 0, List.of())
                : phase(
                        plan.warmup(),
                        plan.model() == LoadPlan.Model.ARRIVAL_RATE
                                ? List.of(plan.rates().getFirst())
                                : List.of(),
                        1_000_000_000);
        if (!warmup.failures().isEmpty()
                || warmup.metrics().outcomes().getOrDefault(CohortMetrics.Outcome.UNFINISHED, 0L) != 0)
            return new Result(
                    warmup.metrics(),
                    new CohortMetrics(0).snapshot(),
                    warmup.duration(),
                    0,
                    warmup.drain(),
                    false,
                    warmup.failures().isEmpty() ? List.of("warmup-did-not-drain") : warmup.failures());
        Phase measured = phase(plan.duration(), plan.rates(), plan.count());
        return new Result(
                warmup.metrics(),
                measured.metrics(),
                warmup.duration(),
                measured.duration(),
                measured.drain(),
                true,
                measured.failures());
    }

    private Phase phase(Duration duration, List<Integer> rates, long limit) {
        long started = clock.getAsLong();
        long length = duration.toNanos();
        ArrivalSchedule schedule = plan.model() == LoadPlan.Model.ARRIVAL_RATE
                ? new ArrivalSchedule(rates, duration, limit, started)
                : null;
        CohortMetrics metrics = new CohortMetrics(schedule == null ? 0 : schedule.plannedCount());
        List<Active> active = new ArrayList<>();
        long concurrencyIndex = 0;
        String failure = null;
        try {
            while (clock.getAsLong() - started < length) {
                maintenance.run();
                observe(active, metrics, started + length);
                long now = clock.getAsLong();
                if (now - started >= length) break;
                boolean issued = false;
                if (schedule != null) {
                    var arrival = schedule.poll(now);
                    if (arrival.isPresent()) {
                        var value = arrival.orElseThrow();
                        metrics.skipped(value.skippedBefore());
                        issue(active, metrics, value.index(), value.plannedNanos());
                        issued = true;
                    }
                } else if (active.size() < maximumPending && concurrencyIndex < limit) {
                    metrics.planOne();
                    issue(active, metrics, concurrencyIndex++, now);
                    issued = true;
                }
                if (!issued || active.isEmpty()) {
                    long until = started + length - clock.getAsLong();
                    if (schedule != null)
                        until = Math.min(until, Math.max(1, schedule.nextNanos() - clock.getAsLong()));
                    if (until > 0) pause.accept(Math.min(1_000_000, until));
                }
            }
        } catch (RuntimeException aborted) {
            failure = "generation-aborted:" + aborted.getClass().getSimpleName();
        }
        long stopped = clock.getAsLong();
        if (schedule != null) metrics.skipped(schedule.finish());
        long drain = plan.drain().toNanos();
        try {
            while (failure == null && !active.isEmpty()) {
                maintenance.run();
                observe(active, metrics, started + length);
                long remaining = drain - (clock.getAsLong() - stopped);
                if (active.isEmpty() || remaining <= 0) break;
                pause.accept(Math.min(1_000_000, remaining));
            }
        } catch (RuntimeException aborted) {
            failure = "drain-aborted:" + aborted.getClass().getSimpleName();
        }
        long finished = clock.getAsLong();
        for (Active value : active) {
            try {
                value.call().cancel();
            } catch (RuntimeException cancellation) {
                if (failure == null)
                    failure = "cancellation-failed:" + cancellation.getClass().getSimpleName();
            }
            metrics.terminal(
                    CohortMetrics.Outcome.UNFINISHED,
                    -1,
                    value.call().mayHaveBeenSent(),
                    value.invoked(),
                    value.planned(),
                    finished,
                    false);
        }
        return new Phase(
                metrics.snapshot(),
                stopped - started,
                finished - stopped,
                failure == null ? List.of() : List.of(failure));
    }

    private void issue(List<Active> active, CohortMetrics metrics, long index, long planned) {
        long invoked = clock.getAsLong();
        metrics.attempt(Math.max(0, invoked - planned));
        if (active.size() == maximumPending) {
            metrics.rejected("SIMULATOR_CAPACITY");
            return;
        }
        try {
            PendingCall call = Objects.requireNonNull(source.apply(index), "call");
            metrics.admitted();
            active.add(new Active(call, invoked, planned));
        } catch (RuntimeException failure) {
            metrics.rejected(
                    failure instanceof RequestFailure request ? request.reason().name() : "LOCAL_REJECTION");
        }
    }

    private void observe(List<Active> active, CohortMetrics metrics, long measurementEnd) {
        var iterator = active.iterator();
        while (iterator.hasNext()) {
            Active value = iterator.next();
            var completion = value.call().poll();
            if (completion.isPresent()) {
                long finished = clock.getAsLong();
                var outcome = completion.orElseThrow();
                metrics.terminal(
                        outcome.outcome(),
                        outcome.status(),
                        value.call().mayHaveBeenSent(),
                        value.invoked(),
                        value.planned(),
                        finished,
                        finished - measurementEnd < 0);
                iterator.remove();
            }
        }
    }

    private record Active(PendingCall call, long invoked, long planned) {}

    private record Phase(CohortMetrics.Snapshot metrics, long duration, long drain, List<String> failures) {}

    public record Result(
            CohortMetrics.Snapshot warmup,
            CohortMetrics.Snapshot measurement,
            long warmupNanos,
            long measurementNanos,
            long drainNanos,
            boolean measurementStarted,
            List<String> failures) {
        public Result {
            failures = List.copyOf(failures);
        }

        public Result(
                CohortMetrics.Snapshot warmup,
                CohortMetrics.Snapshot measurement,
                long warmupNanos,
                long measurementNanos,
                long drainNanos) {
            this(warmup, measurement, warmupNanos, measurementNanos, drainNanos, measurementNanos > 0, List.of());
        }
    }
}
