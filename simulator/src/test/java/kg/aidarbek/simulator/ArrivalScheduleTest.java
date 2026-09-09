package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArrivalScheduleTest {
    @Test
    void unequalHoldsKeepBurstAndRecoveryTimesIndependentOfPriorCompletions() {
        var plan = new LoadPlan(
                LoadPlan.Model.ARRIVAL_RATE,
                List.of(2, 10, 2),
                100,
                Duration.ZERO,
                Duration.ofSeconds(5),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                List.of(Duration.ofSeconds(2), Duration.ofSeconds(1), Duration.ofSeconds(2)));
        var schedule = new ArrivalSchedule(plan, -20);
        assertEquals(18, schedule.plannedCount());
        assertEquals(new ArrivalSchedule.Arrival(0, -20, 0), schedule.poll(-20).orElseThrow());
        assertEquals(
                new ArrivalSchedule.Arrival(3, 1_499_999_980L, 2),
                schedule.poll(1_499_999_980L).orElseThrow());
        assertEquals(
                new ArrivalSchedule.Arrival(4, 1_999_999_980L, 0),
                schedule.poll(1_999_999_980L).orElseThrow());
        assertEquals(
                new ArrivalSchedule.Arrival(14, 2_999_999_980L, 9),
                schedule.poll(2_999_999_980L).orElseThrow());
        assertEquals(3, schedule.finish());
    }

    @Test
    void skipsMissedArrivalsWithoutCatchupAndAccountsForTheEnd() {
        long start = -500_000_000L;
        ArrivalSchedule schedule = new ArrivalSchedule(List.of(10), Duration.ofSeconds(1), 100, start);
        assertTrue(schedule.poll(start - 1).isEmpty());
        assertEquals(
                new ArrivalSchedule.Arrival(0, start, 0), schedule.poll(start).orElseThrow());
        assertEquals(
                new ArrivalSchedule.Arrival(4, start + 400_000_000, 3),
                schedule.poll(start + 450_000_000).orElseThrow());
        assertTrue(schedule.poll(start + 450_000_000).isEmpty());
        assertTrue(schedule.poll(start + 1_000_000_000).isEmpty());
        assertEquals(10, schedule.plannedCount());
        assertEquals(5, schedule.finish());
        assertEquals(0, schedule.finish());
    }

    @Test
    void equalDurationRampStepsRetainOriginalTimesAndRespectCountCaps() {
        ArrivalSchedule schedule = new ArrivalSchedule(List.of(2, 4), Duration.ofSeconds(2), 5, 10);
        assertEquals(5, schedule.plannedCount());
        assertEquals(
                new ArrivalSchedule.Arrival(3, 1_250_000_010L, 3),
                schedule.poll(1_250_000_010L).orElseThrow());
        assertEquals(1_500_000_010L, schedule.nextNanos());
        assertEquals(
                new ArrivalSchedule.Arrival(4, 1_500_000_010L, 0),
                schedule.poll(1_750_000_010L).orElseThrow());
        assertTrue(schedule.poll(1_999_000_010L).isEmpty());
    }

    @Test
    void supportsMonotonicWrapAndNonIntegralIntervalsWithoutEarlyEmission() {
        long start = Long.MAX_VALUE - 100;
        ArrivalSchedule schedule = new ArrivalSchedule(List.of(3), Duration.ofSeconds(1), 3, start);
        assertEquals(0, schedule.poll(start).orElseThrow().index());
        assertTrue(schedule.poll(start + 333_333_333L).isEmpty());
        assertEquals(start + 333_333_334L, schedule.nextNanos());
        assertEquals(1, schedule.poll(start + 333_333_334L).orElseThrow().index());
    }
}
