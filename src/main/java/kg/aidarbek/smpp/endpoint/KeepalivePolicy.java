package kg.aidarbek.smpp.endpoint;

import java.time.Duration;

/** Optional idle-link enquiry bounds; one unanswered automatic enquiry is allowed per connection.
 * @param idleInterval duration without received complete PDUs before an enquiry is due
 * @param responseTimeout total enquiry request deadline, including local admission and writing */
public record KeepalivePolicy(Duration idleInterval, Duration responseTimeout) {
    /** Validates positive monotonic durations. */
    public KeepalivePolicy {
        EndpointOptions.durationNanos(idleInterval);
        EndpointOptions.durationNanos(responseTimeout);
    }
}
