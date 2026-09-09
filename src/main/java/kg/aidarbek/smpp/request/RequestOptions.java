package kg.aidarbek.smpp.request;

import java.time.Duration;
import java.util.Objects;

/**
 * A request's total timeout, including validation, admission, writing and response waiting.
 * Instances retain immutable Duration value equality and are safe to share between threads.
 * @param timeout positive duration representable in signed nanoseconds
 */
public record RequestOptions(Duration timeout) {
    /**
     * Validates a positive duration representable as a signed nanosecond difference.
     *
     * @throws NullPointerException if timeout is null
     *
     * @throws IllegalArgumentException if timeout is nonpositive or exceeds Long.MAX_VALUE nanoseconds
     */
    public RequestOptions {
        Objects.requireNonNull(timeout, "timeout");
        try {
            if (timeout.toNanos() <= 0) {
                throw new IllegalArgumentException("Request timeout must be positive");
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Request timeout exceeds the monotonic clock range", exception);
        }
    }
    /**
     * Creates options with the supplied total timeout.
     * @param timeout positive nanosecond-representable duration
     * @return options
     */
    public static RequestOptions timeout(Duration timeout) {
        return new RequestOptions(timeout);
    }
}
