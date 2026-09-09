package kg.aidarbek.smpp.endpoint;

import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.request.RequestHandle;
import kg.aidarbek.smpp.request.RequestOptions;
import kg.aidarbek.smpp.session.SendRequirements;

/**
 * One typed operation capability; each send rechecks lifecycle and negotiated field support.
 * All sends use the session's single request window and total invocation deadline. Results retain typed
 * peer statuses; local outcomes retain request identity and transmission certainty. No retry or delivery inference occurs.
 * @param <Q> immutable request representation
 * @param <R> corresponding immutable response representation
 */
public final class OperationSender<Q extends Command, R extends Command> {
    private final EndpointConnection connection;
    private final Operation<Q, R> operation;

    OperationSender(EndpointConnection connection, Operation<Q, R> operation) {
        this.connection = connection;
        this.operation = operation;
    }
    /** Sends with the endpoint request deadline and requirements derived from actual fields.
     * @param command non-null immutable operation request
     * @return existing request-window handle for response observation and explicit cancellation */
    public RequestHandle<R> send(Q command) {
        return send(command, null, SendRequirements.COMMON);
    }
    /** Sends with one total invocation deadline and actual-field requirements.
     * @param command non-null immutable operation request
     * @param options invocation budget, or null for the endpoint default
     * @return request handle preserving peer response or structured local failure */
    public RequestHandle<R> send(Q command, RequestOptions options) {
        return send(command, options, SendRequirements.COMMON);
    }
    /** Applies optional extra requirements; these cannot weaken validation of actual fields or TLVs.
     * Validation/admission may throw before a handle exists and consume the same total invocation budget.
     * @param command non-null immutable operation request
     * @param options invocation budget, or null for the endpoint default
     * @param requirements non-null optional stricter version/TLV requirements
     * @return admitted request handle using the session's existing request window
     * @throws IllegalStateException if this stored capability is no longer available
     * @throws IllegalArgumentException for incompatible fields, requirements or configured codec bounds
     * @throws kg.aidarbek.smpp.request.RequestFailure if request admission cannot reserve its finite resources */
    public RequestHandle<R> send(Q command, RequestOptions options, SendRequirements requirements) {
        return connection.send(operation, command, options, requirements);
    }
}
