package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class FaultPolicyTest {
    @Test
    void faultBucketsAreDisjointAndCoverTheDeclaredMix() {
        FaultPolicy policy = new FaultPolicy(1, 5, 10, 1, Duration.ofMillis(200), 0x58, 100);
        for (int bucket = 0; bucket < 100; bucket++) {
            FaultPolicy.Decision expected = bucket < 5
                    ? FaultPolicy.Decision.REJECT
                    : bucket < 15
                            ? FaultPolicy.Decision.DELAY
                            : bucket < 16 ? FaultPolicy.Decision.STALL : FaultPolicy.Decision.ACCEPT;
            assertEquals(expected, policy.bucket(bucket), "bucket=" + bucket);
        }
        var forward = new ArrayList<FaultPolicy.Decision>();
        for (int id = 0; id < 200; id++) forward.add(policy.decision(id));
        for (int id = 199; id >= 0; id--) assertEquals(forward.get(id), policy.decision(id));
    }

    @Test
    void invalidMixesAndUnboundedDelaysAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new FaultPolicy(1, 50, 50, 1, Duration.ZERO, 0x58, 0));
        assertThrows(IllegalArgumentException.class, () -> new FaultPolicy(1, -1, 0, 0, Duration.ZERO, 0x58, 0));
        assertThrows(IllegalArgumentException.class, () -> new FaultPolicy(1, 1, 0, 0, Duration.ZERO, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new FaultPolicy(1, 0, 1, 0, Duration.ofDays(1), 0x58, 0));
    }
}
