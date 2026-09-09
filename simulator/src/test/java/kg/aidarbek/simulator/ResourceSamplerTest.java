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
