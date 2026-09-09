package kg.aidarbek.smpp.endpoint;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import kg.aidarbek.smpp.protocol.AlertNotification;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.Pdu;

/** Immutable optional typed handler registrations, shared across an endpoint's sessions.
 * Handlers must support concurrent sessions and overlapping asynchronous decisions. Missing services reject requests.
 */
public final class EndpointHandlers {
    private final Map<Operation<?, ?>, Registration<?, ?>> registrations;
    private final NotificationHandler<AlertNotification> alert;

    private EndpointHandlers(
            Map<Operation<?, ?>, Registration<?, ?>> registrations, NotificationHandler<AlertNotification> alert) {
        this.alert = alert;
        this.registrations = Map.copyOf(registrations);
    }
    /** Starts a local registration builder.
     * @return unshared mutable builder */
    public static Builder builder() {
        return new Builder();
    }
    /** Returns a registry with no application services.
     * @return immutable empty registry */
    public static EndpointHandlers empty() {
        return builder().build();
    }

    Optional<NotificationHandler<AlertNotification>> alert() {
        return Optional.ofNullable(alert);
    }

    Optional<Registration<?, ?>> find(Operation<?, ?> operation) {
        return Optional.ofNullable(registrations.get(operation));
    }
    /** Collects distinct typed registrations; each build owns an immutable snapshot. Builders are not thread-safe. */
    public static final class Builder {
        /** Registers the optional typed one-way alert hook.
         * @param handler local application completion, without a response PDU
         * @return this builder */
        public Builder onAlert(NotificationHandler<AlertNotification> handler) {
            Objects.requireNonNull(handler, "handler");
            if (alert != null) throw new IllegalArgumentException("Duplicate alert handler");
            alert = handler;
            return this;
        }

        private NotificationHandler<AlertNotification> alert;
        private final Map<Operation<?, ?>, Registration<?, ?>> registrations = new LinkedHashMap<>();

        private Builder() {}
        /** Registers one handler for one implemented operation.
         * @param <Q> request representation
         * @param <R> corresponding response representation
         * @param operation non-null library catalogue key
         * @param handler non-null asynchronous application service
         * @return this builder
         * @throws IllegalArgumentException for unsupported or duplicate registrations */
        public <Q extends Command, R extends Command> Builder on(
                Operation<Q, R> operation, RequestHandler<Q, R> handler) {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(handler, "handler");
            if (!OperationCatalog.all().contains(operation))
                throw new IllegalArgumentException("Operation is not implemented");
            if (registrations.putIfAbsent(operation, new Registration<>(operation, handler)) != null)
                throw new IllegalArgumentException("Duplicate operation handler");
            return this;
        }
        /** Copies the currently registered handlers.
         * @return immutable registry independent of subsequent builder changes */
        public EndpointHandlers build() {
            return new EndpointHandlers(registrations, alert);
        }
    }
    /** Keeps type-safe invocation inside the registration that established the request/response relation. */
    static final class Registration<Q extends Command, R extends Command> {
        private final Operation<Q, R> operation;
        private final RequestHandler<Q, R> handler;

        Registration(Operation<Q, R> operation, RequestHandler<Q, R> handler) {
            this.operation = operation;
            this.handler = handler;
        }

        CompletionStage<HandlerResponse<R>> invoke(
                BoundSession session, Pdu<Command> pdu, long deadline, AtomicBoolean cancelled) {
            return handler.handle(new IncomingRequest<>(
                    session,
                    new Pdu<>(
                            pdu.commandStatus(),
                            pdu.sequenceNumber(),
                            operation.requestType().cast(pdu.command())),
                    deadline,
                    cancelled));
        }
    }
}
