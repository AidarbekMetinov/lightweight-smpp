package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class DeadlinePacerTest {
    @Test
    void guardedWaitAbsorbsKnownParkOversleepWithoutDriftingTheArrivalDeadline() {
        var clock = new AtomicLong(Long.MAX_VALUE - 500_000);
        long start = clock.get();
        var parked = new AtomicLong();
        var pacer = new DeadlinePacer(
                100_000,
                clock::get,
                nanos -> {
                    parked.addAndGet(nanos);
                    clock.addAndGet(nanos + 50_000);
                },
                () -> clock.addAndGet(1_000));
        for (int index = 0; index < 10; index++) pacer.pause(1_000_000);
        assertEquals(10_000_000, clock.get() - start);
        assertEquals(9_000_000, parked.get());
    }
}
