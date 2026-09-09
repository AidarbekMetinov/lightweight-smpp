package kg.aidarbek.simulator;

import java.util.Optional;

/** A narrow observation port for a library request, with no sequence or wire ownership. */
interface PendingCall {
    Optional<Completion> poll();

    boolean mayHaveBeenSent();

    void cancel();

    record Completion(CohortMetrics.Outcome outcome, long status) {}
}
