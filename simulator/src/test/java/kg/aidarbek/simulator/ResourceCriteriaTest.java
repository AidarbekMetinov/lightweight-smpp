package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourceCriteriaTest {
    @TempDir
    Path directory;

    @Test
    void usesObservedPeaksAndFinalGrowthAndDoesNotTreatUnknownUsageAsZero() throws Exception {
        var config = SimulatorArguments.parse(
                "client",
                "--revision=0123456789012345678901234567890123456789",
                "--max-rss-mib=1",
                "--max-heap-mib=1",
                "--max-fd-growth=1",
                "--max-thread-growth=1");
        var current = new AtomicReference<>(new RunEnvironment.Snapshot(100, 200, -1, -1, 0, 0, -1, 2));
        try (var writer = new ReportWriter(directory)) {
            var sampler = new ResourceSampler(writer, current::get, () -> 0);
            assertTrue(ResourceCriteria.evaluate(config.settings(), sampler.summary())
                    .contains("rss-observation-unavailable"));
            current.set(new RunEnvironment.Snapshot(100, 200, 300, 300, 0, 0, 10, 2));
            sampler.baseline();
            current.set(new RunEnvironment.Snapshot(2_000_000, 3_000_000, 2_000_000, 2_000_000, 0, 0, 12, 4));
            sampler.sample();
            current.set(new RunEnvironment.Snapshot(100, 200, 300, 2_000_000, 0, 0, 12, 4));
            sampler.sample();
            assertEquals(
                    List.of(
                            "rss-budget-exceeded",
                            "heap-budget-exceeded",
                            "descriptor-growth-exceeded",
                            "platform-thread-growth-exceeded"),
                    ResourceCriteria.evaluate(config.settings(), sampler.summary()));
            assertTrue(ResourceCriteria.evaluate(LoadSettings.defaults(), sampler.summary())
                    .isEmpty());
        }
    }
}
