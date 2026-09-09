package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoadPlanTest {
    @org.junit.jupiter.api.Test
    void rejectsAWarmupThatItsArrivalScheduleCannotExecute() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new LoadPlan(
                        LoadPlan.Model.ARRIVAL_RATE,
                        java.util.List.of(10),
                        10,
                        java.time.Duration.ofNanos(1),
                        java.time.Duration.ofSeconds(1),
                        java.time.Duration.ofSeconds(1),
                        java.time.Duration.ofSeconds(1)));
    }

    @Test
    void rejectsUnboundedOrContradictoryPlansBeforeAllocatingWork() {
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(10), 0, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(10), 1_000_000_001L, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(), 100, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(0), 100, Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(10), 100, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of(10), 100, Duration.ofDays(2)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LoadPlan(
                        LoadPlan.Model.FIXED_CONCURRENCY,
                        List.of(10),
                        100,
                        Duration.ZERO,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(1)));
    }

    @Test
    void ownsItsRateStepsAndPreservesExplicitPhases() {
        var rates = new ArrayList<>(List.of(10, 50, 100, 10));
        LoadPlan plan = plan(rates, 100, Duration.ofSeconds(4));
        rates.set(0, 999);
        assertEquals(List.of(10, 50, 100, 10), plan.rates());
        assertThrows(UnsupportedOperationException.class, () -> plan.rates().add(20));
        assertEquals(Duration.ofSeconds(4), plan.duration());
    }

    private static LoadPlan plan(List<Integer> rates, long count, Duration duration) {
        return new LoadPlan(
                LoadPlan.Model.ARRIVAL_RATE,
                rates,
                count,
                Duration.ZERO,
                duration,
                Duration.ofSeconds(2),
                Duration.ofSeconds(1));
    }
}
