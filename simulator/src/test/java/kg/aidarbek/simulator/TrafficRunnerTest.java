package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TrafficRunnerTest {
    @Test
    void lifecycleActionsBracketMeasurementAfterWarmupDrainAndBeforeFinalDrain() {
        var clock = new AtomicLong();
        var calls = new AtomicInteger();
        var released = new java.util.concurrent.atomic.AtomicBoolean();
        var actions = new java.util.ArrayList<String>();
        var pending = new PendingCall() {
            @Override
            public Optional<Completion> poll() {
                return released.get()
                        ? Optional.of(new Completion(CohortMetrics.Outcome.SUCCESS, 0))
                        : Optional.empty();
            }

            @Override
            public boolean mayHaveBeenSent() {
                return true;
            }

            @Override
            public void cancel() {}
        };
        var plan = new LoadPlan(
                LoadPlan.Model.ARRIVAL_RATE,
                List.of(10),
                1,
                Duration.ofMillis(100),
                Duration.ofMillis(100),
                Duration.ofMillis(5),
                Duration.ofSeconds(1));
        var result = new TrafficRunner(
                        plan,
                        1,
                        index -> calls.incrementAndGet() == 1 ? new FakeCall(true) : pending,
                        clock::get,
                        clock::addAndGet,
                        () -> {},
                        () -> {
                            assertEquals(1, calls.get());
                            assertEquals(100_000_000, clock.get());
                            actions.add("before");
                        },
                        () -> {
                            assertEquals(200_000_000, clock.get());
                            actions.add("after");
                            released.set(true);
                        })
                .run();
        assertEquals(List.of("before", "after"), actions);
        assertEquals(1, result.measurement().outcomes().get(CohortMetrics.Outcome.SUCCESS));
        assertEquals(0, result.measurement().successesDuringMeasurement());
        assertEquals(5_000_000, result.drainNanos());
    }

    @Test
    void burstReportsSkippedAndLateOutcomesInTheirOriginalScheduledStep() {
        var clock = new AtomicLong();
        var plan = new LoadPlan(
                LoadPlan.Model.ARRIVAL_RATE,
                List.of(2, 10, 2),
                100,
                Duration.ZERO,
                Duration.ofSeconds(5),
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                List.of(Duration.ofSeconds(2), Duration.ofSeconds(1), Duration.ofSeconds(2)));
        var result = new TrafficRunner(
                        plan,
                        1,
                        index -> {
                            if (index == 0) clock.addAndGet(2_500_000_000L);
                            return new FakeCall(true);
                        },
                        clock::get,
                        clock::addAndGet,
                        () -> {})
                .run();
        assertEquals(3, result.intervals().size());
        assertEquals(
                List.of(4L, 10L, 4L),
                result.intervals().stream()
                        .map(value -> value.metrics().planned())
                        .toList());
        assertEquals(
                List.of(3L, 5L, 0L),
                result.intervals().stream()
                        .map(value -> value.metrics().skipped())
                        .toList());
        assertEquals(
                List.of(1L, 5L, 4L),
                result.intervals().stream()
                        .map(value -> value.metrics().admitted())
                        .toList());
        assertEquals(
                2_500_000_000L,
                result.intervals().getFirst().metrics().scheduledLatency().maximumNanos());
        assertEquals(0, result.intervals().getFirst().metrics().successesDuringMeasurement());
        assertEquals(1, result.intervals().getFirst().metrics().outcomes().get(CohortMetrics.Outcome.SUCCESS));
        assertEquals(8, result.measurement().skipped());
        assertEquals(10, result.measurement().admitted());
        assertTrue(result.intervals().stream().allMatch(value -> value.metrics().balanced()));
    }

    @Test
    void warmupThatCannotDrainKeepsItsCohortAndMarksMeasurementUnstarted() {
        var clock = new AtomicLong();
        var plan = new LoadPlan(
                LoadPlan.Model.ARRIVAL_RATE,
                List.of(10),
                2,
                Duration.ofMillis(100),
                Duration.ofMillis(200),
                Duration.ofMillis(5),
                Duration.ofSeconds(1));
        var result =
                new TrafficRunner(plan, 1, index -> new FakeCall(false), clock::get, clock::addAndGet, () -> {}).run();
        assertFalse(result.measurementStarted());
        assertEquals(1, result.warmup().admitted());
        assertEquals(1, result.warmup().outcomes().get(CohortMetrics.Outcome.UNFINISHED));
        assertEquals(0, result.measurement().attempted());
        assertTrue(result.warmup().balanced());
        assertFalse(result.failures().isEmpty());
    }

    @Test
    void maintenanceAndPauseFailuresKeepAdmissionsAndCancelPendingCallsExactlyOnce() {
        for (boolean inMaintenance : List.of(true, false)) {
            var clock = new AtomicLong();
            var ticks = new AtomicInteger();
            var cancelled = new AtomicInteger();
            var plan = new LoadPlan(
                    LoadPlan.Model.ARRIVAL_RATE,
                    List.of(100),
                    10,
                    Duration.ZERO,
                    Duration.ofMillis(100),
                    Duration.ofMillis(5),
                    Duration.ofSeconds(1));
            var result = new TrafficRunner(
                            plan,
                            1,
                            index -> new PendingCall() {
                                @Override
                                public Optional<Completion> poll() {
                                    return Optional.empty();
                                }

                                @Override
                                public boolean mayHaveBeenSent() {
                                    return true;
                                }

                                @Override
                                public void cancel() {
                                    cancelled.incrementAndGet();
                                }
                            },
                            clock::get,
                            nanos -> {
                                if (!inMaintenance) throw new IllegalStateException("interrupted wait");
                                clock.addAndGet(nanos);
                            },
                            () -> {
                                if (inMaintenance && ticks.incrementAndGet() == 2)
                                    throw new java.io.UncheckedIOException(new java.io.IOException("disk unavailable"));
                            })
                    .run();
            assertTrue(result.measurementStarted());
            assertEquals(1, result.measurement().admitted());
            assertEquals(1, result.measurement().outcomes().get(CohortMetrics.Outcome.UNFINISHED));
            assertEquals(9, result.measurement().skipped());
            assertEquals(1, cancelled.get());
            assertTrue(result.measurement().balanced());
            assertFalse(result.failures().isEmpty());
        }
    }

    @Test
    void openLoopKeepsOfferedCountWhenGeneratorFallsBehindAndNeverBuildsABacklog() {
        var clock = new AtomicLong(-1000);
        var calls = new AtomicInteger();
        var plan = new LoadPlan(
                LoadPlan.Model.ARRIVAL_RATE,
                List.of(10),
                10,
                Duration.ZERO,
                Duration.ofSeconds(1),
                Duration.ofMillis(10),
                Duration.ofMillis(5));
        var runner = new TrafficRunner(
                plan,
                1,
                index -> {
                    calls.incrementAndGet();
                    clock.addAndGet(250_000_000);
                    return new FakeCall(true);
                },
                clock::get,
                clock::addAndGet,
                () -> {});
        var result = runner.run().measurement();
        assertEquals(10, result.planned());
        assertEquals(4, calls.get());
        assertEquals(6, result.skipped());
        assertEquals(4, result.outcomes().get(CohortMetrics.Outcome.SUCCESS));
        assertEquals(1, result.peakPending());
        assertTrue(result.balanced());
    }

    @Test
    void concurrencyRefillsOnlyFiniteSlotsAndAccountsForUnfinishedDrain() {
        var clock = new AtomicLong();
        var calls = new AtomicInteger();
        var cancelled = new AtomicInteger();
        var plan = new LoadPlan(
                LoadPlan.Model.FIXED_CONCURRENCY,
                List.of(),
                100,
                Duration.ZERO,
                Duration.ofMillis(10),
                Duration.ofMillis(5),
                Duration.ofMillis(100));
        var result = new TrafficRunner(
                        plan,
                        2,
                        index -> {
                            calls.incrementAndGet();
                            return new PendingCall() {
                                @Override
                                public Optional<Completion> poll() {
                                    return Optional.empty();
                                }

                                @Override
                                public boolean mayHaveBeenSent() {
                                    return true;
                                }

                                @Override
                                public void cancel() {
                                    cancelled.incrementAndGet();
                                }
                            };
                        },
                        clock::get,
                        clock::addAndGet,
                        () -> {})
                .run();
        assertEquals(2, calls.get());
        assertEquals(2, cancelled.get());
        assertEquals(2, result.measurement().planned());
        assertEquals(2, result.measurement().outcomes().get(CohortMetrics.Outcome.UNFINISHED));
        assertEquals(10_000_000, result.measurementNanos());
        assertEquals(5_000_000, result.drainNanos());
        assertTrue(result.measurement().balanced());
    }

    @Test
    void keepsWarmupCohortSeparateAndClassifiesLocalRejection() {
        var clock = new AtomicLong();
        var plan = new LoadPlan(
                LoadPlan.Model.ARRIVAL_RATE,
                List.of(10),
                2,
                Duration.ofMillis(100),
                Duration.ofMillis(200),
                Duration.ofMillis(10),
                Duration.ofMillis(5));
        var result = new TrafficRunner(
                        plan,
                        1,
                        index -> {
                            if (index == 1) throw new IllegalStateException("local capability unavailable");
                            return new FakeCall(true);
                        },
                        clock::get,
                        clock::addAndGet,
                        () -> {})
                .run();
        assertEquals(1, result.warmup().admitted());
        assertEquals(2, result.measurement().attempted());
        assertEquals(1, result.measurement().rejected());
        assertEquals(1, result.measurement().admitted());
        assertTrue(result.measurement().balanced());
    }

    private record FakeCall(boolean done) implements PendingCall {
        @Override
        public Optional<Completion> poll() {
            return done ? Optional.of(new Completion(CohortMetrics.Outcome.SUCCESS, 0)) : Optional.empty();
        }

        @Override
        public boolean mayHaveBeenSent() {
            return true;
        }

        @Override
        public void cancel() {}
    }
}
