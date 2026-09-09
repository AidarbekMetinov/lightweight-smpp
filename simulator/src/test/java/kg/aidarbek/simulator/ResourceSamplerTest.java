package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourceSamplerTest {
    @TempDir
    Path directory;

    @Test
    void keepsPostWarmupBaselineFinalReadingsAndPeaksAsSeparateObservations() throws Exception {
        var observed = new AtomicReference<>(new RunEnvironment.Snapshot(100, 200, 300, 400, 10, 1, 10, 2));
        try (var writer = new ReportWriter(directory.resolve("baseline"))) {
            var sampler = new ResourceSampler(writer, observed::get, () -> 0);
            observed.set(new RunEnvironment.Snapshot(200, 300, 400, 500, 20, 2, 20, 5));
            sampler.baseline();
            observed.set(new RunEnvironment.Snapshot(1000, 2000, 2000, 2000, 30, 3, 50, 20));
            sampler.sample();
            observed.set(new RunEnvironment.Snapshot(250, 500, 350, 2000, 40, 4, 22, 6));
            sampler.sample();
            var summary = sampler.summary();
            assertEquals(100, summary.initial().heapBytes());
            assertEquals(200, summary.baseline().heapBytes());
            assertEquals(250, summary.latest().heapBytes());
            assertEquals(1000, summary.sampledPeakHeapBytes());
            assertEquals(2000, summary.sampledPeakRssBytes());
            assertEquals(50, summary.sampledPeakFileDescriptors());
            assertEquals(20, summary.sampledPeakPlatformThreads());
        }
    }

    @Test
    void unavailableDescriptorCountsStayUnknownUntilARealSampleArrives() throws Exception {
        var clock = new AtomicLong();
        var observation = new AtomicReference<>(snapshot(-1));
        try (var writer = new ReportWriter(directory.resolve("unknown"))) {
            var sampler = new ResourceSampler(writer, observation::get, clock::get);
            assertEquals(-1, sampler.summary().sampledPeakFileDescriptors());
            sampler.sample();
            assertEquals(-1, sampler.summary().sampledPeakFileDescriptors());
            observation.set(snapshot(9));
            clock.set(1_000_000_000);
            sampler.tick();
            assertEquals(9, sampler.summary().sampledPeakFileDescriptors());
            observation.set(snapshot(-1));
            sampler.sample();
            assertEquals(9, sampler.summary().sampledPeakFileDescriptors());
        }
    }

    private static RunEnvironment.Snapshot snapshot(long descriptors) {
        return new RunEnvironment.Snapshot(100, 200, -1, -1, -1, 0, descriptors, 1);
    }
}
