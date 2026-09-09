package kg.aidarbek.smpp.endpoint;

import java.net.SocketAddress;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.ControlCommand;
import kg.aidarbek.smpp.request.RequestHandle;
import kg.aidarbek.smpp.request.RequestOptions;
import kg.aidarbek.smpp.session.SessionState;
import kg.aidarbek.smpp.session.VersionNegotiation;

/**
 * Thread-safe bound-session capability: connection enquiry, unbinding and closure only.
 * Each request is rechecked against the current lifecycle and uses the shared request mechanism.
 */
public final class BoundSession implements AutoCloseable {
    private final EndpointConnection connection;

    BoundSession(EndpointConnection connection) {
        this.connection = connection;
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
