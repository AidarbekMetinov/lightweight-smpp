package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import kg.aidarbek.smpp.endpoint.SessionResources;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PressureSamplerTest {
    @TempDir
    Path directory;

    @Test
    void preservesSampledOwnershipPeaksAndPostWarmupBaselineAlongsidePhysicalConnectionCounts() throws Exception {
        var busy = PressureSampler.observe(
                3, List.of(new SessionResources(2, 100, 1, 200), new SessionResources(3, 150, 2, 300)), 4, 2);
        assertEquals(new PressureSampler.Observation(3, 2, 5, 250, 3, 500, 4, 2), busy);
        var idle = PressureSampler.observe(0, List.of(), 0, 0);
        var current = new AtomicReference<>(idle);
        var clock = new AtomicLong(10);
        try (var writer = new ReportWriter(directory)) {
            var sampler = new PressureSampler(writer, current::get, clock::get);
            sampler.baseline();
            current.set(busy);
            clock.set(20);
            sampler.sample();
            current.set(idle);
            clock.set(30);
            sampler.sample();
            assertEquals(idle, sampler.summary().baseline());
            assertEquals(idle, sampler.summary().latest());
            assertEquals(busy, sampler.summary().sampledPeaks());
            assertTrue(sampler.summary().baselineRecorded());
            writer.finish(Map.of("pressure", sampler.summary()));
        }
        assertTrue(Files.readString(directory.resolve("pressure.csv")).contains("10,3,2,5,250,3,500,4,2\n"));
    }
}
