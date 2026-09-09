package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConnectionChurnTest {
    @Test
    void countsSkippedAndBusyReplacementSlotsWithoutReplayAcrossClockWrap() {
        var slots = new ArrayList<Integer>();
        var churn = new ConnectionChurn(2, 2, 5, Duration.ofSeconds(3), slot -> {
            slots.add(slot);
            return slots.size() > 1;
        });
        long start = Long.MAX_VALUE - 500_000_000;
        churn.start(start);
        churn.advance(start);
        churn.advance(start + 1_500_000_000);
        churn.stop();
        churn.advance(start + 2_000_000_000);
        assertEquals(List.of(0, 1), slots);
        assertEquals(new ConnectionChurn.Snapshot(5, 3, 2, 1, 1, 0, true, true), churn.snapshot());
        assertThrows(IllegalStateException.class, () -> churn.start(start));
    }
}
