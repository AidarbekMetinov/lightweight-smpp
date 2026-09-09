package kg.aidarbek.simulator;

import java.util.ArrayList;
import java.util.List;

/** Reconciles observed handler decisions and explicitly requested finite fault-mix acceptance. */
final class FaultCriteria {
    private FaultCriteria() {}

    static List<String> evaluate(SimulatorConfig config, ReplyController.Snapshot received) {
        var failures = new ArrayList<String>();
        long selected = received.accepted() + received.rejected() + received.delayed() + received.stalled();
        var decisions = received.decisions();
        long expectedDeferred = config.settings().consumerDelay().isZero()
                ? received.stalled() + (config.faults().delay().isZero() ? 0 : received.delayed())
                : selected;
        if (received.received()
                        != selected
                                + received.disconnected()
                                + received.invalidContent()
                                + received.streamCapacityRejected()
                                + received.cancelledBeforeDecision()
                || received.capacityRejected() != received.streamCapacityRejected() + decisions.rejected()
                || expectedDeferred != decisions.admitted() + decisions.rejected()
                || decisions.admitted() != decisions.released() + decisions.cancelled() + decisions.pending()
                || received.pendingDecisions() != decisions.pending())
            failures.add("receiver-decision-accounting-incomplete");
        if (decisions.pending() != 0) failures.add("receiver-decisions-unfinished");
        if (config.settings().verifyFaultMix()) {
            if (selected < 100) failures.add("fault-population-too-small");
            else {
                double tolerance = config.settings().faultTolerance();
                compare(
                        failures,
                        "reject",
                        received.rejected(),
                        selected,
                        config.faults().rejectPercent(),
                        tolerance);
                compare(
                        failures,
                        "delay",
                        received.delayed(),
                        selected,
                        config.faults().delayPercent(),
                        tolerance);
                compare(
                        failures,
                        "stall",
                        received.stalled(),
                        selected,
                        config.faults().stallPercent(),
                        tolerance);
                compare(
                        failures,
                        "accept",
                        received.accepted(),
                        selected,
                        100
                                - config.faults().rejectPercent()
                                - config.faults().delayPercent()
                                - config.faults().stallPercent(),
                        tolerance);
            }
            if (decisions.rejected() != 0 || received.streamCapacityRejected() != 0)
                failures.add("configured-fault-not-applied-capacity");
        }
        return List.copyOf(failures);
    }

    private static void compare(
            List<String> failures, String decision, long actual, long total, int expected, double tolerance) {
        if (Math.abs((double) actual / total - expected / 100.0) > tolerance + 1e-12)
            failures.add("fault-mix-" + decision);
    }
}
