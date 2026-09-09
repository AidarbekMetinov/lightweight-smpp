package kg.aidarbek.smpp.endpoint;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.session.EndpointRole;
import kg.aidarbek.smpp.transport.TcpTransport;
import kg.aidarbek.smpp.transport.TcpTransportConfig;

/** Thread-safe ESME connection owner with bounded resources and explicit binding requirements. */
public final class SmppClient implements AutoCloseable {
    private final EndpointOptions options;
    private final EndpointResources resources;
    /** Creates an endpoint with the documented default limits and owned workers. */
    public SmppClient() {
        this(EndpointOptions.defaults());
    }
    /** Creates an endpoint with explicit finite limits and owned workers.
     * @param options resource/deadline configuration */
    public SmppClient(EndpointOptions options) {
        this.options = Objects.requireNonNull(options, "options");
        resources = new EndpointResources(options, null);
    }
    /** Connects and completes only after successful binding.
     * @param config immutable target and bind request
     * @return protected bound-session result */
    public CompletionStage<BoundSession> connect(ClientConfig config) {
        return connectAttempt(config).result();
    }

    /** Starts the same connect/bind workflow with explicit early cancellation.
     * @param config immutable target and bind
     * @return cancellation and observation capability */
    public ConnectionAttempt connectAttempt(ClientConfig config) {
        Objects.requireNonNull(config, "config");
        long started = System.nanoTime();
        EndpointResources.Permit permit = null;
        TcpTransport transport = null;
        EndpointConnection connection = null;
        try {
            new EndpointPdus(EndpointRole.ESME, options.pduLimits())
                    .encode(new Pdu<>(0, 1, config.bind()), ProtocolProfile.forVersion(config.requestedVersion()));
            permit = resources.reserve();
            transport = TcpTransport.connect(
                    config.remoteAddress(),
                    new TcpTransportConfig(
                            options.pduLimits().maximumPduLength(),
                            options.requestWindow(),
                            options.maximumPendingBytes(),
                            8,
                            65536,
                            0),
                    started + EndpointOptions.durationNanos(options.connectTimeout()));
            connection = EndpointConnection.client(transport, config, options, resources.notifications);
            permit.attach(connection);
            connection.start();
            return new ConnectionAttempt(connection.bound(), connection::cancelBind);
        } catch (RuntimeException failure) {
            if (connection != null) {
                connection.fail(failure);
                return new ConnectionAttempt(connection.bound(), connection::cancelBind);
            } else {
                if (transport != null) {
                    if (permit != null) permit.retire(transport.termination());
                    transport.close();
                } else if (permit != null) permit.release();
            }
            return new ConnectionAttempt(
                    CompletableFuture.<BoundSession>failedFuture(failure).minimalCompletionStage(), () -> false);
        }
    }
    /** Returns currently bound session snapshots.
     * @return immutable list */
    public List<BoundSession> sessions() {
        return resources.sessions();
    }
    /** Returns reserved connecting, active and closing transport slots.
     * @return current count */
    public int connectionCount() {
        return resources.connectionCount();
    }
    /** Attempts graceful unbinding within one total shutdown bound.
     * @param grace nonnegative total bound
     * @return cleanup observation */
    public CompletionStage<EndpointTermination> shutdown(Duration grace) {
        return resources.shutdown(Objects.requireNonNull(grace, "grace"), false);
    }
    /** Observes bounded endpoint cleanup separately from session requests.
     * @return protected result */
    public CompletionStage<EndpointTermination> termination() {
        return resources.termination();
    }
    /** Aborts all owned connections and requests bounded owned-worker cleanup without waiting. */
    @Override
    public void close() {
        resources.close();
    }
}
