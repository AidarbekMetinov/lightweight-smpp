package kg.aidarbek.simulator;

import java.time.Duration;

/** Disjoint seeded application fault decisions; a stall withholds the handler result until cleanup. */
record FaultPolicy(
        long seed,
        int rejectPercent,
        int delayPercent,
        int stallPercent,
        Duration delay,
        long rejectionStatus,
        long disconnectAfter) {
    public FaultPolicy {
        if (rejectPercent < 0
                || delayPercent < 0
                || stallPercent < 0
                || rejectPercent > 100
                || delayPercent > 100
                || stallPercent > 100
                || rejectPercent + delayPercent + stallPercent > 100)
            throw new IllegalArgumentException("Fault percentages must be disjoint within 0..100");
        LoadPlan.bounded(delay, true, Duration.ofHours(1));
        if (rejectionStatus < 1 || rejectionStatus > 0xffff_ffffL || disconnectAfter < 0)
            throw new IllegalArgumentException(
                    "Rejection status must be a nonzero uint32; disconnect count is nonnegative");
    }

    public Decision decision(long identity) {
        long mixed = Long.rotateLeft(identity ^ seed, 17) * 0x9e3779b97f4a7c15L;
        return bucket((int) Math.floorMod(mixed ^ (mixed >>> 32), 100L));
    }

    Decision bucket(int value) {
        if (value < 0 || value >= 100) throw new IllegalArgumentException("Bucket must be 0..99");
        if (value < rejectPercent) return Decision.REJECT;
        if (value < rejectPercent + delayPercent) return Decision.DELAY;
        if (value < rejectPercent + delayPercent + stallPercent) return Decision.STALL;
        return Decision.ACCEPT;
    }

    public enum Decision {
        ACCEPT,
        REJECT,
        DELAY,
        STALL
    }
}
