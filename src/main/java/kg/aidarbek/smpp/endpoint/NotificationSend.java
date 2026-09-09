package kg.aidarbek.smpp.endpoint;

import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

/** Observation and queued cancellation of a one-way write; completion never means peer acceptance. */
public final class NotificationSend {
    private final long sequence;
    private final CompletionStage<Void> result;
    private final BooleanSupplier cancellation;

    NotificationSend(long sequence, CompletionStage<Void> result, BooleanSupplier cancellation) {
        this.sequence = sequence;
        this.result = result;
        this.cancellation = cancellation;
    }
    /** Returns the unique local sequence, without a pending response-window entry.
     * @return sequence in 1..0x7fffffff */
    public long sequenceNumber() {
        return sequence;
    }
    /** Observes local physical write settlement on bounded off-I/O notification workers.
     * @return protected local completion stage; derived-future cancellation cannot cancel the write */
    public CompletionStage<Void> result() {
        return result;
    }
    /** Prevents physical write if queued cancellation wins; a started write returns false.
     * @return true only when this cancellation prevents the notification from being written */
    public boolean cancel() {
        return cancellation.getAsBoolean();
    }
}
