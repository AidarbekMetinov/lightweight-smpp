package kg.aidarbek.simulator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Serializes resource samples and publishes immutable summaries for the run's bounded observation worker. */
final class ResourceSampler {
    private final ReportWriter writer;
    private final Supplier<RunEnvironment.Snapshot> observations;
    private final LongSupplier clock;
    private final long started;
    private long sampled;
    private RunEnvironment.Snapshot initial;
    private RunEnvironment.Snapshot latest;
    private RunEnvironment.Snapshot baseline;
    private long peakHeap;
    private long peakDescriptors = -1;
    private long peakRss = -1;
    private int peakThreads;
    private long samples;
    private volatile Summary published;

    public ResourceSampler(ReportWriter writer) {
        this(writer, RunEnvironment::sample, System::nanoTime);
    }

    ResourceSampler(ReportWriter writer, Supplier<RunEnvironment.Snapshot> observations, LongSupplier clock) {
        this.writer = Objects.requireNonNull(writer, "writer");
        this.observations = Objects.requireNonNull(observations, "observations");
        this.clock = Objects.requireNonNull(clock, "clock");
        started = clock.getAsLong();
        sampled = started;
        sample();
    }

    public synchronized void tick() {
        if (clock.getAsLong() - sampled >= 1_000_000_000) sample();
    }

    public synchronized void sample() {
        sampled = clock.getAsLong();
        latest = observations.get();
        if (initial == null) initial = latest;
        peakHeap = Math.max(peakHeap, latest.heapBytes());
        peakDescriptors = Math.max(peakDescriptors, latest.fileDescriptors());
        peakRss = Math.max(peakRss, latest.rssBytes());
        peakThreads = Math.max(peakThreads, latest.platformThreads());
        samples++;
        try {
            writer.sample(
                    sampled - started,
                    latest.heapBytes(),
                    latest.rssBytes(),
                    latest.cpuNanos(),
                    latest.gcMillis(),
                    latest.fileDescriptors());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        publish();
    }

    public Summary summary() {
        return published;
    }

    synchronized void baseline() {
        sample();
        baseline = latest;
        publish();
    }

    private void publish() {
        published = new Summary(
                initial,
                latest,
                peakHeap,
                peakDescriptors,
                baseline == null ? initial : baseline,
                peakRss,
                peakThreads,
                samples,
                baseline != null);
    }

    public record Summary(
            RunEnvironment.Snapshot initial,
            RunEnvironment.Snapshot latest,
            long sampledPeakHeapBytes,
            long sampledPeakFileDescriptors,
            RunEnvironment.Snapshot baseline,
            long sampledPeakRssBytes,
            int sampledPeakPlatformThreads,
            long samples,
            boolean baselineRecorded) {}
}
