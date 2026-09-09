package kg.aidarbek.simulator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Streams resource observations at most once a second on the simulator owner thread. */
final class ResourceSampler {
    private final ReportWriter writer;
    private final Supplier<RunEnvironment.Snapshot> observations;
    private final LongSupplier clock;
    private final long started;
    private long sampled;
    private RunEnvironment.Snapshot initial;
    private RunEnvironment.Snapshot latest;
    private long peakHeap;
    private long peakDescriptors = -1;

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

    public void tick() {
        if (clock.getAsLong() - sampled >= 1_000_000_000) sample();
    }

    public void sample() {
        sampled = clock.getAsLong();
        latest = observations.get();
        if (initial == null) initial = latest;
        peakHeap = Math.max(peakHeap, latest.heapBytes());
        peakDescriptors = Math.max(peakDescriptors, latest.fileDescriptors());
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
    }

    public Summary summary() {
        return new Summary(initial, latest, peakHeap, peakDescriptors);
    }

    public record Summary(
            RunEnvironment.Snapshot initial,
            RunEnvironment.Snapshot latest,
            long sampledPeakHeapBytes,
            long sampledPeakFileDescriptors) {}
}
