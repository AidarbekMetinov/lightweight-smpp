package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class FaultCriteriaTest {
    @Test
    void validatesSelectedMixAndAppliedDecisionsWithoutTreatingCapacityFallbackAsAnInjectedDelay() {
        var config = SimulatorArguments.parse(
                "server",
                "--revision=0123456789012345678901234567890123456789",
                "--reject=25",
                "--delay=25",
                "--stall=25",
                "--verify-fault-mix=true",
                "--fault-tolerance=0");
        var good = snapshot(25, 25, 25, 25, 0);
        assertEquals(List.of(), FaultCriteria.evaluate(config, good));
        assertTrue(FaultCriteria.evaluate(config, snapshot(26, 24, 25, 25, 0)).contains("fault-mix-reject"));
        assertTrue(FaultCriteria.evaluate(config, snapshot(25, 25, 25, 25, 1))
                .contains("configured-fault-not-applied-capacity"));
        assertTrue(FaultCriteria.evaluate(config, snapshot(0, 0, 0, 0, 0)).contains("fault-population-too-small"));
    }

    private static ReplyController.Snapshot snapshot(long accept, long reject, long delay, long stall, long full) {
        return new ReplyController.Snapshot(
                accept + reject + delay + stall,
                accept,
                reject,
                delay,
                stall,
                0,
                0,
                full,
                0,
                0,
                0,
                1,
                1,
                0,
                0,
                new DecisionQueue.Snapshot(
                        delay + stall - full, delay - full, stall, full, 0, (int) (delay + stall - full)));
    }
}
