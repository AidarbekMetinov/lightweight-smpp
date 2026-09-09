package kg.aidarbek.smpp.endpoint;

import java.util.Objects;
import java.util.Optional;

/** Terminal connection-loop observation; no message requests are retained or replayed.
 * @param reason why future connection work stopped
 * @param attempts total initiated attempts, including pre-connect admission failures
 * @param publishedSessions fresh bound sessions offered to the application observer
 * @param lastFailure latest observed connection, callback or cleanup failure */
public record ReconnectResult(
        Reason reason, int attempts, int publishedSessions, Optional<RuntimeException> lastFailure) {
    /** Terminal policy outcomes. */
    public enum Reason {
        /** Explicit caller cancellation won. */
        CANCELLED,
        /** Endpoint shutdown stopped future attempts. */
        ENDPOINT_CLOSED,
        /** The configured lifetime attempt budget ended. */
        ATTEMPTS_EXHAUSTED,
        /** No bounded application notification reservation was available. */
        NOTIFICATION_CAPACITY,
        /** The application session observer threw. */
        CALLBACK_FAILED,
        /** Physical connection cleanup failed; replacement would conceal leaked ownership. */
        CLEANUP_FAILED
    }
    /** Validates immutable, bounded observations. */
    public ReconnectResult {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(lastFailure, "lastFailure");
        if (attempts < 0 || publishedSessions < 0 || publishedSessions > attempts)
            throw new IllegalArgumentException("Invalid reconnect observation counts");
    }
}
