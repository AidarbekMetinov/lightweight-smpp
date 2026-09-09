package kg.aidarbek.simulator;

import java.util.ArrayList;
import java.util.List;
import org.HdrHistogram.Histogram;

/** Fixed-range HDR samples in microseconds, with explicit overflows and mergeable buckets. */
final class Latencies {
    private static final long MAXIMUM_NANOS = 3_600_000_000_000L;
    private final Histogram histogram = new Histogram(MAXIMUM_NANOS / 1000, 3);
    private long overflow;
    private long maximum;

    public synchronized void record(long nanos) {
        if (nanos < 0) throw new IllegalArgumentException("Latency must be nonnegative");
        maximum = Math.max(maximum, nanos);
        if (nanos > MAXIMUM_NANOS) overflow++;
        else histogram.recordValue(nanos / 1000 + (nanos % 1000 == 0 ? 0 : 1));
    }

    public synchronized Snapshot snapshot() {
        List<Bucket> buckets = new ArrayList<>();
        for (var value : histogram.recordedValues())
            buckets.add(new Bucket(value.getValueIteratedTo(), value.getCountAddedInThisIterationStep()));
        return new Snapshot(
                histogram.getTotalCount(),
                overflow,
                maximum,
                histogram.getValueAtPercentile(50),
                histogram.getValueAtPercentile(95),
                histogram.getValueAtPercentile(99),
                histogram.getValueAtPercentile(99.9),
                buckets);
    }

    public record Bucket(long upperMicros, long count) {}

    public record Snapshot(
            long count,
            long overflow,
            long maximumNanos,
            long p50Micros,
            long p95Micros,
            long p99Micros,
            long p999Micros,
            List<Bucket> buckets) {
        public Snapshot {
            buckets = List.copyOf(buckets);
        }
    }
}
