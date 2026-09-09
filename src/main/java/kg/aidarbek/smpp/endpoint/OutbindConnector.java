package kg.aidarbek.smpp.endpoint;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.protocol.Outbind;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.session.EndpointRole;
import kg.aidarbek.smpp.transport.TcpTransport;
import kg.aidarbek.smpp.transport.TcpTransportConfig;

/** Explicit MC connection owner: each invocation sends one outbind and accepts one follow-up bind. */
public final class OutbindConnector implements AutoCloseable {
    private final OutbindConnectorConfig config;
    private final EndpointOptions options;
    private final ConnectionLifecycle lifecycle;
    private final BindAuthenticator authenticator;
    private final ExchangeConfig exchange;
    private final AuthenticationDispatcher authentication;
    private final EndpointResources resources;
    /** Creates an owner without connection or retry work.
     * @param config MC version and bind authentication policy
     * @param options bounded endpoint resources and deadlines
     * @param authenticator ESME bind authentication
     * @param exchange typed application handler configuration */
    public OutbindConnector(
            OutbindConnectorConfig config,
            EndpointOptions options,
            BindAuthenticator authenticator,
            ExchangeConfig exchange) {
        this(config, options, authenticator, exchange, ConnectionLifecycle.defaults());
    }
    /** Creates a reversed connector with explicit transport lifecycle policy.
     * @param config message-center bind policy
     * @param options finite endpoint resources
     * @param authenticator asynchronous follow-up bind decision
     * @param exchange exchange policy
     * @param lifecycle optional TLS client and keepalive policy */
    public OutbindConnector(
            OutbindConnectorConfig config,
            EndpointOptions options,
            BindAuthenticator authenticator,
            ExchangeConfig exchange,
            ConnectionLifecycle lifecycle) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        lifecycle.validateRole(true, options);
        this.config = Objects.requireNonNull(config, "config");
        this.options = Objects.requireNonNull(options, "options");
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator");
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        authentication =
                new AuthenticationDispatcher(config.authenticationConcurrency(), config.authenticationQueue(), null);
        resources = new EndpointResources(options, authentication, exchange);
    }

    /** Starts one resolved TCP attempt with one notification on that connection.
     * @param remoteAddress resolved ESME listener address; DNS resolution belongs to the caller
     * @param notification immutable outbind credentials, preflighted before connection admission
     * @return cancellation and successful-binding observation; never automatic reconnection */
    public ConnectionAttempt connectAttempt(InetSocketAddress remoteAddress, Outbind notification) {
        Objects.requireNonNull(remoteAddress, "remoteAddress");
        Objects.requireNonNull(notification, "notification");
        long started = System.nanoTime();
        EndpointResources.Permit permit = null;
        TcpTransport transport = null;
        EndpointConnection connection = null;
        try {
            if (remoteAddress.isUnresolved() || remoteAddress.getPort() == 0)
                throw new IllegalArgumentException("Outbind connector requires resolved addressing and a nonzero port");
            new EndpointPdus(EndpointRole.MESSAGE_CENTER, options.pduLimits())
                    .encode(new Pdu<>(0, 1, notification), ProtocolProfile.forVersion(config.advertisedVersion()));
            permit = resources.reserve();
            transport = TcpTransport.connect(
                    remoteAddress,
                    new TcpTransportConfig(
                            options.pduLimits().maximumPduLength(),
                            options.requestWindow(),
                            options.maximumPendingBytes(),
                            8,
                            65536,
                            0),
                    started + EndpointOptions.durationNanos(options.connectTimeout()),
                    lifecycle.tls().orElse(null));
            connection = EndpointConnection.outbindConnector(
                    transport,
                    remoteAddress,
                    config,
                    options,
                    resources.notifications,
                    authentication,
                    authenticator,
                    resources.handlers,
                    exchange,
                    notification);
            connection.configureStartup(lifecycle.startupNanos(options, true), true);
            connection.configureKeepalive(lifecycle.keepalive().orElse(null));
            permit.attach(connection);
            connection.start();
            return new ConnectionAttempt(connection, permit.retirement());
        } catch (RuntimeException failure) {
            if (connection != null) {
                connection.fail(failure);
                return new ConnectionAttempt(connection, permit.retirement());
            }
            if (transport != null) {
                if (permit != null) permit.retire(transport.termination());
                transport.close();
            } else if (permit != null) permit.release();
            return new ConnectionAttempt(
                    failure, permit == null ? CompletableFuture.completedFuture(null) : permit.retirement());
        }
    }
    /** Explicitly repeats connection establishment, outbind and follow-up bind within a finite policy.
     * Only connection authentication is repeated; no message request is copied across generations.
     * @param remoteAddress resolved ESME listener
     * @param notification fixed outbind credentials
     * @param policy finite connection attempt and delay bounds
     * @param observer bounded off-I/O notification for each fresh binding
     * @return cancellation and terminal observation ownership */
    public ReconnectHandle reconnect(
            InetSocketAddress remoteAddress,
            Outbind notification,
            ReconnectPolicy policy,
            Consumer<BoundSession> observer) {
        Objects.requireNonNull(remoteAddress, "remoteAddress");
        Objects.requireNonNull(notification, "notification");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(observer, "observer");
        return resources.reconnect(policy, () -> connectAttempt(remoteAddress, notification), observer);
    }

    /** Returns bound MC sessions created by explicit successful attempts.
     * @return immutable snapshot */
    public List<BoundSession> sessions() {
        return resources.sessions();
    }
    /** Returns connecting, active and closing socket reservations.
     * @return current count */
    public int connectionCount() {
        return resources.connectionCount();
    }
    /** Stops attempts and drains admitted work within one bound.
     * @param grace nonnegative total shutdown duration
     * @return protected physical cleanup snapshot */
    public CompletionStage<EndpointTermination> shutdown(Duration grace) {
        return resources.shutdown(Objects.requireNonNull(grace, "grace"), false);
    }
    /** Observes physical endpoint cleanup separately from binding results.
     * @return protected cleanup stage */
    public CompletionStage<EndpointTermination> termination() {
        return resources.termination();
    }
    /** Aborts owned connections and requests bounded physical cleanup. */
    @Override
    public void close() {
        resources.close();
    }
}
