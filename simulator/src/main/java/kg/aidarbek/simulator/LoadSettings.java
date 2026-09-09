package kg.aidarbek.simulator;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** Non-secret, finite simulator pacing, lifecycle, observation and acceptance settings. */
record LoadSettings(
        String scenario,
        Duration spin,
        int churnRate,
        long churnCount,
        Duration consumerDelay,
        Duration sampleInterval,
        boolean verifyFaultMix,
        double faultTolerance,
        long maximumRssBytes,
        long maximumHeapBytes,
        long maximumDescriptorGrowth,
        long maximumThreadGrowth) {
    LoadSettings {
        if (scenario == null || !scenario.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}"))
            throw new IllegalArgumentException("Scenario must be a bounded non-secret label");
        LoadPlan.bounded(spin, true, Duration.ofMillis(1));
        LoadPlan.bounded(consumerDelay, true, Duration.ofHours(1));
        LoadPlan.bounded(sampleInterval, false, Duration.ofMinutes(1));
        if (sampleInterval.compareTo(Duration.ofMillis(10)) < 0)
            throw new IllegalArgumentException("Resource sampling must be at least 10ms apart");
        if (churnRate < 0
                || churnRate > 1000
                || churnCount < 0
                || churnCount > 1_000_000
                || (churnRate == 0) != (churnCount == 0))
            throw new IllegalArgumentException("Churn requires rate 1..1000/s and count 1..1000000, or both zero");
        if (!Double.isFinite(faultTolerance) || faultTolerance < 0 || faultTolerance > 1)
            throw new IllegalArgumentException("Fault tolerance must be a finite fraction in 0..1");
        if (maximumRssBytes < 0
                || maximumHeapBytes < 0
                || maximumDescriptorGrowth < -1
                || maximumDescriptorGrowth > 1_000_000
                || maximumThreadGrowth < -1
                || maximumThreadGrowth > 1_000_000)
            throw new IllegalArgumentException(
                    "Resource thresholds must use finite nonnegative limits or explicit disabled values");
    }

    static LoadSettings defaults() {
        return new LoadSettings(
                "custom",
                Duration.ofNanos(100_000),
                0,
                0,
                Duration.ZERO,
                Duration.ofSeconds(1),
                false,
                0.05,
                0,
                0,
                -1,
                -1);
    }

    Map<String, Object> report() {
        var values = new LinkedHashMap<String, Object>();
        values.put("scenario", scenario);
        values.put("spinNanos", spin.toNanos());
        values.put("churnRate", churnRate);
        values.put("churnCount", churnCount);
        values.put("consumerDelayNanos", consumerDelay.toNanos());
        values.put("sampleIntervalNanos", sampleInterval.toNanos());
        values.put("verifyFaultMix", verifyFaultMix);
        values.put("faultTolerance", faultTolerance);
        values.put("maximumRssBytes", maximumRssBytes);
        values.put("maximumHeapBytes", maximumHeapBytes);
        values.put("maximumDescriptorGrowth", maximumDescriptorGrowth);
        values.put("maximumThreadGrowth", maximumThreadGrowth);
        return Map.copyOf(values);
    }
}
