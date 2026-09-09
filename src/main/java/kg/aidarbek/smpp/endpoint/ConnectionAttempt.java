package kg.aidarbek.smpp.endpoint;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Cancellation ownership before a bound session is ready. Cancelling a derived result future changes
 * only that observer; use this capability to abort the actual connection/bind workflow.
 */
public final class ConnectionAttempt {
    private final CompletionStage<BoundSession> result;
    private final CompletionStage<Void> cleanup;
    private final EndpointConnection connection;
    private final RuntimeException rejection;

    ConnectionAttempt(EndpointConnection connection, CompletionStage<Void> cleanup) {
        this.connection = connection;
        this.cleanup = cleanup;
        rejection = null;
        result = connection.bound();
    }

    ConnectionAttempt(RuntimeException rejection, CompletionStage<Void> cleanup) {
        this.rejection = rejection;
        this.cleanup = cleanup;
        connection = null;
        result = CompletableFuture.<BoundSession>failedFuture(rejection).minimalCompletionStage();
    }

    EndpointConnection connection() {
        return connection;
    }

    RuntimeException rejection() {
        return rejection;
    }

    CompletionStage<Void> cleanup() {
        return cleanup;
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
        return connection != null && connection.cancelBind();
    }
}
