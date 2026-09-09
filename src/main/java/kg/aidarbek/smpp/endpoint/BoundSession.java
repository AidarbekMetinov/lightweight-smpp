package kg.aidarbek.smpp.endpoint;

import java.net.SocketAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BroadcastSm;
import kg.aidarbek.smpp.protocol.BroadcastSmResponse;
import kg.aidarbek.smpp.protocol.CancelBroadcastSm;
import kg.aidarbek.smpp.protocol.CancelBroadcastSmResponse;
import kg.aidarbek.smpp.protocol.CancelSm;
import kg.aidarbek.smpp.protocol.CancelSmResponse;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.ControlCommand;
import kg.aidarbek.smpp.protocol.DataSm;
import kg.aidarbek.smpp.protocol.DataSmResponse;
import kg.aidarbek.smpp.protocol.DeliverSm;
import kg.aidarbek.smpp.protocol.DeliverSmResponse;
import kg.aidarbek.smpp.protocol.QueryBroadcastSm;
import kg.aidarbek.smpp.protocol.QueryBroadcastSmResponse;
import kg.aidarbek.smpp.protocol.QuerySm;
import kg.aidarbek.smpp.protocol.QuerySmResponse;
import kg.aidarbek.smpp.protocol.ReplaceSm;
import kg.aidarbek.smpp.protocol.ReplaceSmResponse;
import kg.aidarbek.smpp.protocol.SubmitMulti;
import kg.aidarbek.smpp.protocol.SubmitMultiResponse;
import kg.aidarbek.smpp.protocol.SubmitSm;
import kg.aidarbek.smpp.protocol.SubmitSmResponse;
import kg.aidarbek.smpp.request.RequestHandle;
import kg.aidarbek.smpp.request.RequestOptions;
import kg.aidarbek.smpp.session.SessionState;
import kg.aidarbek.smpp.session.VersionNegotiation;

/**
 * Thread-safe bound-session facade exposing implemented message and connection-control capabilities.
 * Each request is rechecked against the current lifecycle and uses the shared request mechanism.
 */
public final class BoundSession implements AutoCloseable {
    /** Returns MC alert capability when the current mode and lifecycle permit it.
     * @return optional one-way alert sender */
    public Optional<AlertSender> alerts() {
        return connection.canSendAlert() ? Optional.of(new AlertSender(connection)) : Optional.empty();
    }
    /** Returns query capability when currently permitted.
     * @return optional typed query sender */
    public Optional<OperationSender<QuerySm, QuerySmResponse>> query() {
        return sender(CommonOperations.QUERY_SM);
    }
    /** Returns cancellation capability when currently permitted.
     * @return optional typed cancellation sender */
    public Optional<OperationSender<CancelSm, CancelSmResponse>> cancel() {
        return sender(CommonOperations.CANCEL_SM);
    }
    /** Returns replacement capability for the exact current profile/mode.
     * @return optional typed replacement sender */
    public Optional<OperationSender<ReplaceSm, ReplaceSmResponse>> replace() {
        return sender(CommonOperations.REPLACE_SM);
    }
    /** Returns multiple-submission capability when currently permitted.
     * @return optional typed multiple-submission sender */
    public Optional<OperationSender<SubmitMulti, SubmitMultiResponse>> multipleSubmission() {
        return sender(CommonOperations.SUBMIT_MULTI);
    }

    /** Returns broadcast submission capability for an SMPP 5.0 ESME in TX or TRX mode.
     * @return optional typed broadcast sender; peer service availability is independent */
    public Optional<OperationSender<BroadcastSm, BroadcastSmResponse>> broadcast() {
        return sender(BroadcastOperations.BROADCAST_SM);
    }
    /** Returns broadcast query capability when currently permitted.
     * @return optional typed query sender */
    public Optional<OperationSender<QueryBroadcastSm, QueryBroadcastSmResponse>> queryBroadcast() {
        return sender(BroadcastOperations.QUERY_BROADCAST_SM);
    }
    /** Returns broadcast cancellation capability when currently permitted.
     * @return optional typed cancellation sender */
    public Optional<OperationSender<CancelBroadcastSm, CancelBroadcastSmResponse>> cancelBroadcast() {
        return sender(BroadcastOperations.CANCEL_BROADCAST_SM);
    }

    /** Returns the latest valid congestion sample from an accepted matched SMPP 5.0 response.
     * Missing, reserved, stale and unsupported-profile parameters do not replace the snapshot.
     * This observation does not alter admission limits, rate or retry policy.
     * @return immutable latest sample, or empty until one is accepted */
    public Optional<CongestionObservation> congestion() {
        return connection.congestion();
    }

    private final EndpointConnection connection;

    /** Samples bounded request and paired application-reply ownership for this generation.
     * Counters exclude one-way notifications, control replies and transport buffers. Concurrent
     * request settlement may occur between independent counter reads; this is diagnostic data.
     * @return immutable current observation */
    public SessionResources resources() {
        return connection.resources();
    }

    BoundSession(EndpointConnection connection) {
        this.connection = connection;
    }

    /** Returns a currently permitted, locally implemented typed sending capability.
     * This describes local sending support; the peer may reject a request or have no application handler.
     * @param <Q> request representation
     * @param <R> paired response representation
     * @param operation non-null library catalogue operation
     * @return current capability, or empty when role, version, mode or lifecycle forbids it */
    public <Q extends Command, R extends Command> Optional<OperationSender<Q, R>> sender(Operation<Q, R> operation) {
        return connection.canSend(operation)
                ? Optional.of(new OperationSender<>(connection, operation))
                : Optional.empty();
    }
    /** Returns ESME submission capability when the current mode/version permit it.
     * @return optional submission sender */
    public Optional<OperationSender<SubmitSm, SubmitSmResponse>> submission() {
        return sender(MessageOperations.SUBMIT_SM);
    }
    /** Returns message-center delivery capability when currently permitted.
     * @return optional delivery sender */
    public Optional<OperationSender<DeliverSm, DeliverSmResponse>> delivery() {
        return sender(MessageOperations.DELIVER_SM);
    }
    /** Returns data_sm capability for this endpoint's exact negotiated request direction.
     * SMPP 3.4 allows both origins in all bound modes; 5.0 applies its direction-specific mode table.
     * @return optional data-message sender */
    public Optional<OperationSender<DataSm, DataSmResponse>> dataMessages() {
        return sender(MessageOperations.DATA_SM);
    }

    /** Returns the immutable connection generation.
     * @return session identity */
    public UUID id() {
        return connection.id();
    }
    /** Returns the remote socket identity.
     * @return peer address */
    public SocketAddress peer() {
        return connection.peer();
    }
    /** Returns a current lifecycle snapshot.
     * @return current state */
    public SessionState state() {
        return connection.state();
    }
    /** Returns the established bind mode.
     * @return RX, TX or TRX */
    public BindMode bindMode() {
        return connection.bindMode();
    }
    /** Returns the immutable requested/advertised/effective version result.
     * @return negotiation result */
    public VersionNegotiation negotiation() {
        return connection.negotiation();
    }
    /** Sends a connection enquiry using the endpoint default deadline.
     * @return request handle */
    public RequestHandle<ControlCommand> enquireLink() {
        return connection.control(ControlCommand.Type.ENQUIRE_LINK, null);
    }
    /** Sends an enquiry with a total invocation deadline.
     * @param options request deadline
     * @return request handle */
    public RequestHandle<ControlCommand> enquireLink(RequestOptions options) {
        return connection.control(ControlCommand.Type.ENQUIRE_LINK, options);
    }
    /** Begins graceful unbinding using the default request deadline.
     * @return request handle */
    public RequestHandle<ControlCommand> unbind() {
        return connection.control(ControlCommand.Type.UNBIND, null);
    }
    /** Begins graceful unbinding with a total invocation deadline.
     * @param options request deadline
     * @return request handle */
    public RequestHandle<ControlCommand> unbind(RequestOptions options) {
        return connection.control(ControlCommand.Type.UNBIND, options);
    }
    /** Returns an observed local or peer lifecycle failure after closure.
     * Explicit close and successful peer unbind normally have no failure. Request failures retain their
     * original certainty and cause; this observation does not wait for notification dispatch.
     * @return original failure when available, otherwise empty */
    public Optional<RuntimeException> closeReason() {
        return connection.closeReason();
    }
    /** Observes physical transport cleanup on the endpoint's bounded notification workers.
     * @return protected completion */
    public CompletionStage<Void> termination() {
        return connection.termination();
    }
    /** Aborts this connection idempotently; never closes a shared endpoint or supplied executor. */
    @Override
    public void close() {
        connection.close();
    }
}
