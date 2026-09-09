package kg.aidarbek.simulator;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Routes bounded counters to the original scheduled rate step and the complete cohort. */
final class TrafficMetrics {
    private final CohortMetrics total;
    private final List<Integer> rates;
    private final List<Duration> holds;
    private final List<CohortMetrics> steps = new ArrayList<>();
    private final long[] ends;
    private final ArrivalSchedule schedule;
    private long cursor;

    TrafficMetrics(ArrivalSchedule schedule, List<Integer> rates, List<Duration> holds) {
        this.schedule = schedule;
        this.rates = List.copyOf(rates);
        this.holds = List.copyOf(holds);
        total = new CohortMetrics(schedule == null ? 0 : schedule.plannedCount());
        ends = new long[rates.size()];
        long count = 0;
        for (int step = 0; step < rates.size(); step++) {
            long planned = schedule.plannedCount(step);
            steps.add(new CohortMetrics(planned));
            ends[step] = count += planned;
        }
    }

    void planOne() {
        total.planOne();
    }

    void skipped(long count) {
        total.skipped(count);
        long remaining = count;
        while (remaining > 0) {
            int step = schedule.stepOf(cursor);
            long part = Math.min(remaining, ends[step] - cursor);
            steps.get(step).skipped(part);
            remaining -= part;
            cursor += part;
        }
    }

    void attempt(long index, long lag) {
        total.attempt(lag);
        if (schedule != null) steps.get(schedule.stepOf(index)).attempt(lag);
        cursor = index + 1;
    }

    void rejected(long index, String reason) {
        total.rejected(reason);
        if (schedule != null) steps.get(schedule.stepOf(index)).rejected(reason);
    }

    void admitted(long index) {
        total.admitted();
        if (schedule != null) steps.get(schedule.stepOf(index)).admitted();
    }

    void terminal(
            long index,
            CohortMetrics.Outcome outcome,
            long status,
            boolean mayHaveBeenSent,
            long invoked,
            long planned,
            long finished,
            boolean duringMeasurement) {
        total.terminal(outcome, status, mayHaveBeenSent, invoked, planned, finished, duringMeasurement);
        if (schedule != null) {
            int step = schedule.stepOf(index);
            steps.get(step)
                    .terminal(
                            outcome,
                            status,
                            mayHaveBeenSent,
                            invoked,
                            planned,
                            finished,
                            finished - schedule.endNanos(step) < 0);
        }
    }

    CohortMetrics.Snapshot snapshot() {
        return total.snapshot();
    }

    List<TrafficRunner.Interval> intervals() {
        var values = new ArrayList<TrafficRunner.Interval>();
        long offset = 0;
        for (int step = 0; step < steps.size(); step++) {
            long duration = holds.get(step).toNanos();
            values.add(new TrafficRunner.Interval(
                    rates.get(step), offset, duration, steps.get(step).snapshot()));
            offset += duration;
        }
        return List.copyOf(values);
    }
}
