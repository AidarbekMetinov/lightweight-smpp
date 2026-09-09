package kg.aidarbek.simulator;

import java.util.Objects;
import java.util.Optional;
import kg.aidarbek.smpp.request.PeerNackException;
import kg.aidarbek.smpp.request.RequestFailure;
import kg.aidarbek.smpp.request.RequestHandle;
import kg.aidarbek.smpp.request.TransmissionCertainty;

/** Observes the library's atomic terminal snapshot without creating a second pending-request engine. */
final class RequestObservation implements PendingCall {
    private final RequestHandle<?> handle;

    public RequestObservation(RequestHandle<?> handle) {
        this.handle = Objects.requireNonNull(handle, "handle");
    }

    @Override
    public Optional<Completion> poll() {
        return handle.terminalOutcome().map(terminal -> {
            if (terminal.response().isPresent()) {
                long status = terminal.response().orElseThrow().commandStatus();
                return new Completion(
                        status == 0 ? CohortMetrics.Outcome.SUCCESS : CohortMetrics.Outcome.PEER_NEGATIVE, status);
            }
            RuntimeException failure = terminal.failure().orElseThrow();
            if (failure instanceof PeerNackException nack)
                return new Completion(
                        CohortMetrics.Outcome.GENERIC_NACK, nack.nack().commandStatus());
            if (failure instanceof RequestFailure request)
                return new Completion(
                        switch (request.reason()) {
                            case DEADLINE_EXPIRED -> CohortMetrics.Outcome.TIMEOUT;
                            case CANCELLED -> CohortMetrics.Outcome.CANCELLED;
                            default -> CohortMetrics.Outcome.LOCAL_FAILURE;
                        },
                        -1);
            return new Completion(CohortMetrics.Outcome.LOCAL_FAILURE, -1);
        });
    }

    @Override
    public boolean mayHaveBeenSent() {
        return handle.transmission() == TransmissionCertainty.MAY_HAVE_BEEN_SENT;
    }

    @Override
    public void cancel() {
        handle.cancel();
    }
}
