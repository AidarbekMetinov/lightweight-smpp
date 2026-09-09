package kg.aidarbek.smpp.endpoint;

import java.util.List;

/**
 * Bounded shutdown observation. Nonzero counts identify work still retained after the shutdown bound.
 * Later application cooperation can release that work; this immutable snapshot is not rewritten.
 *
 * @param remainingConnections connecting or active transports not yet physically terminated
 * @param remainingAuthentications authentication work still executing or awaiting a supplied stage
 * @param remainingNotifications reserved, queued or executing result/lifecycle notifications
 * @param remainingHandlers message invocations or returned stages retained after logical cancellation
 * @param listenerTerminated whether the listener is closed and its accept loop stopped
 * @param timerTerminated whether the owned deadline worker stopped
 * @param applicationWorkersTerminated whether all owned authentication, message and notification workers stopped
 * @param failures bounded original cleanup failures; the list is copied, exception identities are preserved
 */
public record EndpointTermination(
        int remainingConnections,
        int remainingAuthentications,
        int remainingNotifications,
        int remainingHandlers,
        boolean listenerTerminated,
        boolean timerTerminated,
        boolean applicationWorkersTerminated,
        List<Throwable> failures) {
    /** Copies failure references and validates nonnegative observations. */
    public EndpointTermination {
        failures = List.copyOf(failures);
        if (remainingConnections < 0
                || remainingAuthentications < 0
                || remainingNotifications < 0
                || remainingHandlers < 0)
            throw new IllegalArgumentException("Remaining work counts must be nonnegative");
    }
    /** Preserves the original constructor for endpoint snapshots without message-handler work.
     * @param remainingConnections connecting or active transports not physically terminated
     * @param remainingAuthentications retained authentication invocations/stages
     * @param remainingNotifications retained result/lifecycle notifications
     * @param listenerTerminated whether listener cleanup succeeded
     * @param timerTerminated whether the deadline worker stopped
     * @param applicationWorkersTerminated whether owned application workers stopped
     * @param failures original cleanup failure observations */
    public EndpointTermination(
            int remainingConnections,
            int remainingAuthentications,
            int remainingNotifications,
            boolean listenerTerminated,
            boolean timerTerminated,
            boolean applicationWorkersTerminated,
            List<Throwable> failures) {
        this(
                remainingConnections,
                remainingAuthentications,
                remainingNotifications,
                0,
                listenerTerminated,
                timerTerminated,
                applicationWorkersTerminated,
                failures);
    }
    /** Returns whether all endpoint-owned cleanup finished within the supplied bound.
     * @return cleanup status */
    public boolean complete() {
        return remainingConnections == 0
                && remainingAuthentications == 0
                && remainingNotifications == 0
                && remainingHandlers == 0
                && listenerTerminated
                && timerTerminated
                && applicationWorkersTerminated
                && failures.isEmpty();
    }
}
