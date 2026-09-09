package kg.aidarbek.smpp.request;

import java.util.Objects;
import java.util.Optional;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.Pdu;

/**
 * Immutable terminal snapshot, visible before application notification.
 * @param <R> expected response command type
 */
public final class RequestOutcome<R extends Command> {
    private final Pdu<R> response;
    private final RuntimeException failure;

    private RequestOutcome(Pdu<R> response, RuntimeException failure) {
        this.response = response;
        this.failure = failure;
    }

    static <R extends Command> RequestOutcome<R> responded(Pdu<R> response) {
        return new RequestOutcome<>(Objects.requireNonNull(response, "response"), null);
    }

    static <R extends Command> RequestOutcome<R> failed(RuntimeException failure) {
        return new RequestOutcome<>(null, Objects.requireNonNull(failure, "failure"));
    }
    /**
     * Returns a normal operation response, including negative statuses.
     * @return response or empty
     */
    public Optional<Pdu<R>> response() {
        return Optional.ofNullable(response);
    }
    /**
     * Returns a local failure or known generic_nack.
     * @return failure or empty
     */
    public Optional<RuntimeException> failure() {
        return Optional.ofNullable(failure);
    }
}
