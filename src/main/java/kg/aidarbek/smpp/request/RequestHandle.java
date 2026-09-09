package kg.aidarbek.smpp.request;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.PduHeader;

/**
 * A thread-safe request observation and cancellation capability.
 *
 * <p>Only the owning window assigns the identity and settles this handle. Pending metadata is not
 * a response authorization token; the endpoint still applies session policy and atomically accepts
 * the response through the window. The result contains a complete immutable PDU with raw numeric
 * peer status. Operation-specific negative responses complete normally. Local failure and a known
 * generic_nack complete exceptionally with different exception types.
 *
 * <p>Internal settlement is immediately observable through isDone and terminalOutcome, even if
 * notification workers are blocked. CompletionStage dependents without an explicit executor can
 * delay that worker and its notification permit. Applications should use an appropriate executor
 * for expensive dependent actions. Waiting or interruption on a derived future does not cancel
 * this protocol request; only cancel competes for local cancellation.
 * @param <R> expected response command
 */
public final class RequestHandle<R extends Command> {
    final RequestWindow owner;
    final long expectedResponseCommandId;
    final Class<R> responseType;
    final long frameBytes;
    final BoundedNotifications.Reservation notification;
    volatile TransmissionCertainty transmission = TransmissionCertainty.NOT_SENT;
    volatile RequestOutcome<R> outcome;
    private final RequestIdentity identity;
    private final PduHeader requestHeader;
    private final long deadlineNanos;
    private final CompletableFuture<Pdu<R>> completion = new CompletableFuture<>();
    private final CompletionStage<Pdu<R>> result = completion.minimalCompletionStage();

    RequestHandle(
            RequestWindow owner,
            RequestIdentity identity,
            long requestCommandId,
            long expectedResponseCommandId,
            Class<R> responseType,
            long frameBytes,
            long deadlineNanos,
            BoundedNotifications.Reservation notification) {
        this.owner = owner;
        this.identity = identity;
        this.expectedResponseCommandId = expectedResponseCommandId;
        this.responseType = responseType;
        this.frameBytes = frameBytes;
        this.deadlineNanos = deadlineNanos;
        this.notification = notification;
        requestHeader = new PduHeader(frameBytes, requestCommandId, 0, identity.sequenceNumber());
    }
    /**
     * Returns the immutable local correlation identity.
     * @return identity
     */
    public RequestIdentity identity() {
        return identity;
    }
    /**
     * Returns the original request header, suitable for session response context.
     * @return header
     */
    public PduHeader requestHeader() {
        return requestHeader;
    }
    /**
     * Returns the absolute monotonic deadline, with nanoTime wrap semantics.
     * @return clock tick
     */
    public long deadlineNanos() {
        return deadlineNanos;
    }
    /**
     * Returns a protected notification stage; mutating a derived future cannot settle this request.
     * @return result stage
     */
    public CompletionStage<Pdu<R>> result() {
        return result;
    }
    /**
     * Competes for local cancellation; it never emits cancel_sm or initiates replay.
     * @return true only if cancellation won; false if another terminal outcome or a due deadline won
     */
    public boolean cancel() {
        return owner.cancel(this);
    }
    /**
     * Returns whether internal settlement is complete, regardless of notification delay.
     * @return terminal status
     */
    public boolean isDone() {
        return outcome != null;
    }
    /**
     * Returns the immutable winning terminal snapshot.
     * @return outcome, or empty while pending
     */
    public Optional<RequestOutcome<R>> terminalOutcome() {
        return Optional.ofNullable(outcome);
    }
    /**
     * Returns current transmission knowledge.
     * @return certainty
     */
    public TransmissionCertainty transmission() {
        return transmission;
    }

    void dispatchCompletion() {
        notification.dispatch(() -> {
            RequestOutcome<R> terminal = outcome;
            if (terminal.failure().isPresent()) {
                completion.completeExceptionally(terminal.failure().orElseThrow());
            } else {
                completion.complete(terminal.response().orElseThrow());
            }
        });
    }
}
