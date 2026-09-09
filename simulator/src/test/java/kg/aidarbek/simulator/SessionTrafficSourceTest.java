package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class SessionTrafficSourceTest {
    @Test
    void concurrencyRefillsTheSessionThatCompletedWhileItsPeerRemainsFull() {
        List<Integer> slots = new ArrayList<>();
        List<Long> indices = new ArrayList<>();
        List<ControlledCall> calls = new ArrayList<>();
        var source = new SessionTrafficSource(LoadPlan.Model.FIXED_CONCURRENCY, 2, 1, (slot, index) -> {
            if (!calls.isEmpty() && slot == 0 && calls.getFirst().terminal == null)
                throw new RejectedExecutionException("First session still owns its request window");
            slots.add(slot);
            indices.add(index);
            ControlledCall call = new ControlledCall();
            calls.add(call);
            return call;
        });
        PendingCall slow = source.apply(0);
        PendingCall fast = source.apply(1);
        calls.get(1).complete(CohortMetrics.Outcome.SUCCESS);
        assertTrue(fast.poll().isPresent());
        assertDoesNotThrow(() -> source.apply(2));
        assertEquals(List.of(0, 1, 1), slots);
        assertEquals(List.of(0L, 1L, 2L), indices);
        assertTrue(slow.poll().isEmpty());
    }

    @Test
    void cancellationRetainsCapacityUntilTerminalPollAndRepeatedPollReleasesOnlyOnce() {
        List<ControlledCall> calls = new ArrayList<>();
        var source = new SessionTrafficSource(LoadPlan.Model.FIXED_CONCURRENCY, 1, 1, (slot, index) -> {
            ControlledCall call = new ControlledCall();
            call.sent = false;
            calls.add(call);
            return call;
        });
        PendingCall first = source.apply(0);
        first.cancel();
        assertEquals(1, calls.getFirst().cancellations);
        assertThrows(RejectedExecutionException.class, () -> source.apply(1));
        calls.getFirst().complete(CohortMetrics.Outcome.CANCELLED);
        assertThrows(RejectedExecutionException.class, () -> source.apply(2));
        assertEquals(CohortMetrics.Outcome.CANCELLED, first.poll().orElseThrow().outcome());
        PendingCall second = source.apply(3);
        assertEquals(CohortMetrics.Outcome.CANCELLED, first.poll().orElseThrow().outcome());
        assertThrows(RejectedExecutionException.class, () -> source.apply(4));
        assertEquals(2, calls.size());
        assertFalse(second.mayHaveBeenSent());
        calls.get(1).sent = true;
        assertTrue(second.mayHaveBeenSent());
    }

    @Test
    void sourceRejectionIsPropagatedOnceAndDoesNotLeakItsLocalReservation() {
        List<Integer> slots = new ArrayList<>();
        RuntimeException refusal = new RejectedExecutionException("real local admission refusal");
        var source = new SessionTrafficSource(LoadPlan.Model.FIXED_CONCURRENCY, 2, 1, (slot, index) -> {
            slots.add(slot);
            if (index == 0) throw refusal;
            return new ControlledCall();
        });
        assertSame(refusal, assertThrows(RejectedExecutionException.class, () -> source.apply(0)));
        assertEquals(List.of(0), slots, "A refusal must not be replayed on a different session");
        source.apply(1);
        source.apply(2);
        assertThrows(RejectedExecutionException.class, () -> source.apply(3));
        assertEquals(List.of(0, 1, 0), slots);
    }

    @Test
    void arrivalRoutingUsesTheOriginalIndexEvenWhenAnotherSessionHasRoom() {
        List<Integer> slots = new ArrayList<>();
        RuntimeException refusal = new RejectedExecutionException("scheduled session remains full");
        var source = new SessionTrafficSource(LoadPlan.Model.ARRIVAL_RATE, 2, 1, (slot, index) -> {
            slots.add(slot);
            if (index == 2) throw refusal;
            return new ControlledCall();
        });
        source.apply(0);
        assertSame(refusal, assertThrows(RejectedExecutionException.class, () -> source.apply(2)));
        source.apply(5);
        assertEquals(List.of(0, 0, 1), slots);
    }

    @Test
    void runnerAccountsForAsymmetricCompletionAndConstructsSarContentForTheSelectedSlot() {
        var clock = new AtomicLong();
        var content = HelperContentPlans.create("sar", 161, 1);
        long[] ordinals = new long[2];
        List<Emission> emissions = new ArrayList<>();
        List<ControlledCall> calls = new ArrayList<>();
        var source = new SessionTrafficSource(LoadPlan.Model.FIXED_CONCURRENCY, 2, 1, (slot, index) -> {
            long ordinal = ordinals[slot]++;
            TrafficContent body = content.next(ordinal);
            int part = body.parameters().entries().stream()
                            .filter(tlv -> tlv.tag() == 0x020f)
                            .findFirst()
                            .orElseThrow()
                            .value()[0]
                    & 255;
            emissions.add(new Emission(slot, index, ordinal, part));
            ControlledCall call = new ControlledCall();
            if (index != 0) call.complete(CohortMetrics.Outcome.SUCCESS);
            calls.add(call);
            return call;
        });
        var plan = new LoadPlan(
                LoadPlan.Model.FIXED_CONCURRENCY,
                List.of(),
                4,
                Duration.ZERO,
                Duration.ofMillis(10),
                Duration.ofMillis(1),
                Duration.ofSeconds(1));
        var result = new TrafficRunner(
                        plan,
                        2,
                        source,
                        clock::get,
                        clock::addAndGet,
                        () -> {},
                        () -> {},
                        () -> calls.getFirst().complete(CohortMetrics.Outcome.SUCCESS))
                .run();
        assertEquals(
                List.of(
                        new Emission(0, 0, 0, 1),
                        new Emission(1, 1, 0, 1),
                        new Emission(1, 2, 1, 2),
                        new Emission(1, 3, 2, 1)),
                emissions);
        assertEquals(4, result.measurement().planned());
        assertEquals(4, result.measurement().attempted());
        assertEquals(4, result.measurement().admitted());
        assertEquals(4, result.measurement().outcomes().get(CohortMetrics.Outcome.SUCCESS));
        assertEquals(0, result.measurement().rejected());
        assertEquals(0, result.measurement().pending());
        assertEquals(2, result.measurement().peakPending());
        assertTrue(result.measurement().balanced());
        assertTrue(result.failures().isEmpty());
    }

    private record Emission(int slot, long offeredIndex, long ordinal, int sarPart) {}

    /** A terminal snapshot changes only by explicit completion; cancellation alone is not retirement. */
    private static final class ControlledCall implements PendingCall {
        Completion terminal;
        int cancellations;
        boolean sent = true;

        void complete(CohortMetrics.Outcome outcome) {
            terminal = new Completion(outcome, outcome == CohortMetrics.Outcome.SUCCESS ? 0 : -1);
        }

        @Override
        public Optional<Completion> poll() {
            return Optional.ofNullable(terminal);
        }

        @Override
        public boolean mayHaveBeenSent() {
            return sent;
        }

        @Override
        public void cancel() {
            cancellations++;
        }
    }
}
