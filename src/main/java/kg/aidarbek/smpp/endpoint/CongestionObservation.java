package kg.aidarbek.smpp.endpoint;

/** Immutable latest accepted SMPP 5.0 congestion sample; it applies no rate or retry policy.
 * @param level advertised congestion in 0..100, retained as an exact value
 * @param commandId matched response's unsigned command identity
 * @param sequenceNumber matched request sequence
 * @param observedNanos monotonic endpoint clock sample; use subtraction for elapsed time */
public record CongestionObservation(int level, long commandId, long sequenceNumber, long observedNanos) {
    /** Validates value representation without interpreting load bands or wall-clock time. */
    public CongestionObservation {
        if (level < 0
                || level > 100
                || commandId < 0x80000000L
                || commandId > 0xffffffffL
                || sequenceNumber < 1
                || sequenceNumber > 0x7fffffffL)
            throw new IllegalArgumentException("Invalid matched-response congestion sample");
    }
}
