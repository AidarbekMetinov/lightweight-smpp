package kg.aidarbek.smpp.endpoint;

import java.time.Duration;
import java.util.Objects;

/** Explicit finite connection-attempt policy, independent of all message replay.
 * @param maximumAttempts total lifetime connection attempts, including the initial attempt
 * @param retryDelay nonnegative delay after physical retirement before another attempt */
public record ReconnectPolicy(int maximumAttempts, Duration retryDelay) {
    /** Validates a finite positive attempt bound and a monotonic retry duration. */
    public ReconnectPolicy {
        if (maximumAttempts < 1)
            throw new IllegalArgumentException("Reconnect requires a positive total attempt bound");
        Objects.requireNonNull(retryDelay, "retryDelay");
        if (!retryDelay.isZero()) EndpointOptions.durationNanos(retryDelay);
    }
}
