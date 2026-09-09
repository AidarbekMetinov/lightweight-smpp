package kg.aidarbek.smpp.endpoint;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.session.EndpointRole;
import kg.aidarbek.smpp.transport.TcpTransport;
import kg.aidarbek.smpp.transport.TcpTransportConfig;

/** Thread-safe ESME connection owner with bounded resources and explicit binding requirements. */
public final class SmppClient implements AutoCloseable {
    private final EndpointOptions options;
    private final ConnectionLifecycle lifecycle;
    private final EndpointResources resources;
    private final ExchangeConfig exchange;
    /** Creates an endpoint with the documented default limits and owned workers. */
    public SmppClient() {
        this(EndpointOptions.defaults());
    }
    /** Creates an endpoint with explicit finite limits and owned workers.
     * @param options resource/deadline configuration */
    public SmppClient(EndpointOptions options) {
        this(options, ExchangeConfig.defaults());
    }
    /** Creates an endpoint with explicit message policy and optional handlers on owned bounded workers.
     * @param options connection/request/notification resource and deadline configuration
     * @param exchange message-handler and ordered-response policy */
    public SmppClient(EndpointOptions options, ExchangeConfig exchange) {
        this(options, exchange, ConnectionLifecycle.defaults());
    }

    /** Creates an endpoint with explicit connection lifecycle policy.
     * @param options finite endpoint resources and deadlines
     * @param exchange application exchange policy
     * @param lifecycle optional TLS and keepalive policy */
    public SmppClient(EndpointOptions options, ExchangeConfig exchange, ConnectionLifecycle lifecycle) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        lifecycle.validateRole(true, options);
        this.options = Objects.requireNonNull(options, "options");
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        resources = new EndpointResources(options, null, exchange);
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
                    started + EndpointOptions.durationNanos(options.connectTimeout()),
                    lifecycle.tls().orElse(null));
            connection = EndpointConnection.client(
                    transport, config, options, resources.notifications, resources.handlers, exchange);
            connection.configureStartup(lifecycle.startupNanos(options, true), true);
            connection.configureKeepalive(lifecycle.keepalive().orElse(null));
            permit.attach(connection);
            connection.start();
            return new ConnectionAttempt(connection, permit.retirement());
        } catch (RuntimeException failure) {
            if (connection != null) {
                connection.fail(failure);
                return new ConnectionAttempt(connection, permit.retirement());
            } else {
                if (transport != null) {
                    if (permit != null) permit.retire(transport.termination());
                    transport.close();
                } else if (permit != null) permit.release();
            }
            return new ConnectionAttempt(
                    failure, permit == null ? CompletableFuture.completedFuture(null) : permit.retirement());
        }
    }
    /** Starts an explicitly bounded sequence of fresh connection generations.
     * Each session observer runs on bounded endpoint notification workers. No pending request or message
     * is copied to another generation. Closing the handle cancels attempts and the current session.
     * @param config fixed target and bind credentials
     * @param policy finite lifetime attempt and retry-delay policy
     * @param observer application notification for each observed fresh binding
     * @return cancellation and terminal observation ownership */
    public ReconnectHandle reconnect(ClientConfig config, ReconnectPolicy policy, Consumer<BoundSession> observer) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(observer, "observer");
        return resources.reconnect(policy, () -> connectAttempt(config), observer);
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
