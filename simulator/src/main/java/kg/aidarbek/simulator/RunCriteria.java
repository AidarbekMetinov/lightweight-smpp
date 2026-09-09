package kg.aidarbek.simulator;

import java.util.ArrayList;
import java.util.List;

/** Declared pass/fail gates; expected faults never excuse incomplete accounting or resource cleanup. */
final class RunCriteria {
    private RunCriteria() {}

    public static Verdict evaluate(
            SimulatorConfig config,
            TrafficRunner.Result result,
            boolean cleanup,
            long invalidContent,
            long incompleteAssemblies) {
        List<String> failures = new ArrayList<>();
        failures.addAll(result.failures());
        if (!result.measurementStarted()) failures.add("measurement-not-started");
        for (var cohort : List.of(result.warmup(), result.measurement())) {
            if (!cohort.balanced() || cohort.pending() != 0) failures.add("incomplete-accounting");
            if (cohort.outcomes().getOrDefault(CohortMetrics.Outcome.UNFINISHED, 0L) != 0)
                failures.add("unfinished-requests");
            if (!config.expectFailures()
                    && (cohort.skipped() != 0
                            || cohort.rejected() != 0
                            || cohort.outcomes().entrySet().stream()
                                    .anyMatch(entry ->
                                            entry.getKey() != CohortMetrics.Outcome.SUCCESS && entry.getValue() != 0)))
                failures.add("unsuccessful-work");
        }
        if (!cleanup) failures.add("incomplete-cleanup");
        if (invalidContent != 0) failures.add("invalid-content");
        if (incompleteAssemblies != 0) failures.add("incomplete-assemblies");
        var measured = result.measurement();
        if (config.minimumRateRatio() > 0
                && !config.operation().equals("none")
                && (!result.measurementStarted()
                        || measured.planned() == 0
                        || result.measurementNanos() < config.load().duration().toNanos()
                        || (double) measured.successesDuringMeasurement()
                                        / measured.planned()
                                        * config.load().duration().toNanos()
                                        / result.measurementNanos()
                                < config.minimumRateRatio())) failures.add("successful-rate-below-threshold");
        if (config.maximumP99Millis() > 0
                && (measured.scheduledLatency().overflow() != 0
                        || measured.scheduledLatency().p99Micros() > config.maximumP99Millis() * 1000))
            failures.add("scheduled-p99-above-threshold");
        return new Verdict(failures);
    }

    public record Verdict(List<String> failures) {
        public Verdict {
            failures = List.copyOf(failures);
        }

        public boolean passed() {
            return failures.isEmpty();
        }
    }
}
