package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class PressureCriteriaTest {
    @Test
    void checksSampledCeilingsAndPhysicalFinalOwnershipWithoutInventingTransportQueues() {
        var config = SimulatorArguments.parse(
                "client", "--revision=0123456789012345678901234567890123456789", "--connections=2", "--window=3");
        var idle = new PressureSampler.Observation(0, 2, 0, 0, 0, 0, 0, 0);
        var withinBounds = new PressureSampler.Observation(2, 2, 6, 2_097_152, 22, 2_097_152, 6, 2);
        assertTrue(PressureCriteria.evaluate(
                        config, new PressureSampler.Summary(withinBounds, idle, withinBounds, 2, true))
                .isEmpty());
        var exceeded = new PressureSampler.Observation(3, 3, 7, 2_097_153, 23, 2_097_153, 7, 3);
        assertEquals(
                List.of(
                        "connection-bound-exceeded",
                        "observed-session-bound-exceeded",
                        "request-count-bound-exceeded",
                        "request-byte-bound-exceeded",
                        "reply-count-bound-exceeded",
                        "reply-byte-bound-exceeded",
                        "decision-bound-exceeded",
                        "stream-bound-exceeded",
                        "ownership-remains-after-cleanup"),
                PressureCriteria.evaluate(config, new PressureSampler.Summary(idle, exceeded, exceeded, 2, true)));
    }
}
