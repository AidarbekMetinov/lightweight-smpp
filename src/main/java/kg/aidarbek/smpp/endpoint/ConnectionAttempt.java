package kg.aidarbek.smpp.endpoint;

import java.util.concurrent.CompletionStage;
import java.util.function.BooleanSupplier;

/**
 * Cancellation ownership before a bound session is ready. Cancelling a derived result future changes
 * only that observer; use this capability to abort the actual connection/bind workflow.
 */
public final class ConnectionAttempt {
    private final CompletionStage<BoundSession> result;
    private final BooleanSupplier cancellation;

    ConnectionAttempt(CompletionStage<BoundSession> result, BooleanSupplier cancellation) {
        this.result = result;
        this.cancellation = cancellation;
    }

    /** Returns the protected bind result, published outside transport progress.
     * @return bound session or failure */
    public CompletionStage<BoundSession> result() {
        return result;
    }

    /**
     * Cancels this connecting/binding workflow if cancellation wins. Readiness already established
     * returns false; close the resulting BoundSession to abort an established session.
     *
     * @return true only if this cancellation won
     */
    public boolean cancel() {
        return cancellation.getAsBoolean();
    }
}
