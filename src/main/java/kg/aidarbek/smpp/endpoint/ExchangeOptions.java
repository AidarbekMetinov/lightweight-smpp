package kg.aidarbek.smpp.endpoint;

import java.time.Duration;

/**
 * Finite application execution and ordered reply buffering policy. Bounds count wire sizes, not exact JVM heap usage.
 * Request objects held by logically cancelled handlers remain bounded by the endpoint-wide handler capacity.
 * @param handlerConcurrency endpoint-wide maximum active invocations or unfinished returned stages
 * @param handlerQueue additional waiting invocations; zero rejects work that cannot start immediately
 * @param maximumReplies per-session received requests awaiting ordered response write settlement
 * @param maximumReplyBytes per-session sum of retained request wire sizes and reserved/encoded reply sizes
 * @param handlerTimeout total decision budget from complete-frame arrival, including queue and owner waits
 */
public record ExchangeOptions(
        int handlerConcurrency, int handlerQueue, int maximumReplies, long maximumReplyBytes, Duration handlerTimeout) {
    /** Validates finite positive bounds and a nonnegative waiting-handler queue. */
    public ExchangeOptions {
        if (handlerConcurrency < 1
                || handlerQueue < 0
                || maximumReplies < 1
                || maximumReplyBytes < 16
                || (long) handlerConcurrency + handlerQueue > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid exchange capacity");
        EndpointOptions.durationNanos(handlerTimeout);
    }
    /** Creates four application slots, 128 waiting slots, 32 replies, 1 MiB reply accounting and ten-second decisions.
     * These configurable limits are library policy. Frame limits and reply accounting are independent bounds.
     * @return fresh immutable policy */
    public static ExchangeOptions defaults() {
        return new ExchangeOptions(4, 128, 32, 1_048_576, Duration.ofSeconds(10));
    }
}
