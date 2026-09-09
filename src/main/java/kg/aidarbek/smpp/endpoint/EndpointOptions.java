package kg.aidarbek.smpp.endpoint;

import java.time.Duration;
import java.util.Objects;
import kg.aidarbek.smpp.codec.PduLimits;

/**
 * Immutable endpoint resource and deadline configuration. Durations are library policy.
 *
 * @param maximumConnections combined connecting, active and closing transport reservations
 * @param requestWindow maximum outstanding locally originated requests per session
 * @param maximumPendingBytes maximum retained outgoing request bytes per session
 * @param callbackThreads maximum concurrently executing application notifications
 * @param callbackQueue additional notification reservations beyond the worker count
 * @param connectTimeout TCP connect deadline, excluding caller-owned DNS resolution
 * @param bindTimeout deadline from connected/accepted arrival through authentication and bind reply
 * @param requestTimeout default enquiry and unbind deadline
 * @param shutdownTimeout bound for abort cleanup and executor reporting
 * @param pduLimits codec and frame allocation limits
 */
public record EndpointOptions(
        int maximumConnections,
        int requestWindow,
        long maximumPendingBytes,
        int callbackThreads,
        int callbackQueue,
        Duration connectTimeout,
        Duration bindTimeout,
        Duration requestTimeout,
        Duration shutdownTimeout,
        PduLimits pduLimits) {
    /**
     * Validates finite positive deadlines and finite positive capacities; a callback queue may be zero.
     *
     * @throws IllegalArgumentException for invalid capacities or durations
     * @throws NullPointerException for missing durations or codec limits
     */
    public EndpointOptions {
        if (maximumConnections < 1
                || requestWindow < 1
                || maximumPendingBytes < 16
                || callbackThreads < 1
                || callbackQueue < 0) {
            throw new IllegalArgumentException("Endpoint capacities must be positive; callback queue may be zero");
        }
        durationNanos(connectTimeout);
        durationNanos(bindTimeout);
        durationNanos(requestTimeout);
        durationNanos(shutdownTimeout);
        Objects.requireNonNull(pduLimits, "pduLimits");
    }

    static long durationNanos(Duration duration) {
        Objects.requireNonNull(duration, "duration");
        try {
            long nanos = duration.toNanos();
            if (nanos <= 0 || nanos > Long.MAX_VALUE / 4) {
                throw new IllegalArgumentException("Deadline duration must be positive and at most 73 years");
            }
            return nanos;
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Deadline duration exceeds supported monotonic range", overflow);
        }
    }

    /**
     * Returns modest configurable starting bounds; no capacity guarantee is implied.
     *
     * @return a fresh immutable default configuration
     */
    public static EndpointOptions defaults() {
        return new EndpointOptions(
                100,
                32,
                1_048_576,
                4,
                128,
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                Duration.ofSeconds(10),
                Duration.ofSeconds(10),
                new PduLimits(1_048_576, 65_536, 1024));
    }
}
