package kg.aidarbek.smpp.endpoint;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import kg.aidarbek.smpp.spi.FrameTransport;
import kg.aidarbek.smpp.transport.TcpListener;
import kg.aidarbek.smpp.transport.TcpTransportConfig;

/** A message-center TCP listener composing binding, control and optional typed application message services. */
public final class SmppServer implements AutoCloseable {
    private final ServerConfig config;
    private final EndpointOptions options;
    private final BindAuthenticator authenticator;
    private final Consumer<BoundSession> boundListener;
    private final AuthenticationDispatcher authentication;
    private final EndpointResources resources;
    private final ExchangeConfig exchange;
    private TcpListener listener;
    private boolean closed;
    private boolean started;

    /**
     * Creates an endpoint with owned bounded authentication and notification workers.
     *
     * @param config listener/version/authentication configuration
     * @param options resource/deadline limits
     * @param authenticator focused application authentication
     * @param boundListener optional application work on each successful bound session; provide a no-op if unused
     */
    public SmppServer(
            ServerConfig config,
            EndpointOptions options,
            BindAuthenticator authenticator,
            Consumer<BoundSession> boundListener) {
        this(config, options, authenticator, boundListener, null);
    }

    /**
     * Uses a caller-owned executor only for authentication invocation. The endpoint never closes it;
     * an owned bounded worker invokes its execute method, including inline/rejecting executors.
     *
     * @param config listener/version/authentication configuration
     * @param options resource/deadline limits
     * @param authenticator focused application authentication
     * @param boundListener bounded off-I/O session notification
     * @param authenticationExecutor caller-owned authentication executor, or null for owned workers
     */
    public SmppServer(
            ServerConfig config,
            EndpointOptions options,
            BindAuthenticator authenticator,
            Consumer<BoundSession> boundListener,
            Executor authenticationExecutor) {
        this(config, options, authenticator, boundListener, authenticationExecutor, ExchangeConfig.defaults());
    }
    /** Creates a server with explicit message policy and optional typed application handlers on owned workers.
     * @param config listener/version/authentication configuration
     * @param options connection/request/notification limits and deadlines
     * @param authenticator non-null focused asynchronous authentication service
     * @param boundListener non-null session notification, dispatched outside I/O and coordinator locks
     * @param authenticationExecutor caller-owned authentication executor, or null for owned invocation
     * @param exchange message-handler and ordered-response policy */
    public SmppServer(
            ServerConfig config,
            EndpointOptions options,
            BindAuthenticator authenticator,
            Consumer<BoundSession> boundListener,
            Executor authenticationExecutor,
            ExchangeConfig exchange) {
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.config = Objects.requireNonNull(config, "config");
        this.options = Objects.requireNonNull(options, "options");
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator");
        this.boundListener = Objects.requireNonNull(boundListener, "boundListener");
        authentication = new AuthenticationDispatcher(
                config.authenticationConcurrency(), config.authenticationQueue(), authenticationExecutor);
        resources = new EndpointResources(options, authentication, exchange);
    }

    /** Binds once to the configured address.
     * @return protected bound-address result */
    public synchronized CompletionStage<InetSocketAddress> start() {
        if (closed || started)
            return CompletableFuture.<InetSocketAddress>failedFuture(
                            new IllegalStateException("Server can be started once before closure"))
                    .minimalCompletionStage();
        started = true;
        try {
            listener = TcpListener.bind(
                    config.listenAddress(),
                    options.maximumConnections(),
                    new TcpTransportConfig(
                            options.pduLimits().maximumPduLength(),
                            options.requestWindow(),
                            options.maximumPendingBytes(),
                            8,
                            65536,
                            0),
                    this::accept);
            resources.listener(listener::close, listener.termination());
            return CompletableFuture.completedFuture(listener.localAddress()).minimalCompletionStage();
        } catch (IOException | RuntimeException failure) {
            close();
            return CompletableFuture.<InetSocketAddress>failedFuture(failure).minimalCompletionStage();
        }
    }

    private boolean accept(FrameTransport transport, SocketAddress peer) {
        EndpointResources.Permit permit;
        try {
            permit = resources.reserve();
        } catch (EndpointException full) {
            return false;
        }
        EndpointConnection connection = null;
        try {
            connection = EndpointConnection.server(
                    transport,
                    peer,
                    config,
                    options,
                    resources.notifications,
                    authentication,
                    authenticator,
                    boundListener,
                    resources.handlers,
                    exchange);
            permit.attach(connection);
            connection.start();
            return true;
        } catch (RuntimeException failure) {
            if (connection != null) connection.close();
            else {
                permit.retire(transport.termination());
                transport.close();
            }
            return false;
        }
    }
    /** Returns a snapshot of currently bound sessions.
     * @return immutable session list */
    public List<BoundSession> sessions() {
        return resources.sessions();
    }
    /** Returns connection reservations including active, connecting and closing transports.
     * @return current count */
    public int connectionCount() {
        return resources.connectionCount();
    }
    /** Stops acceptance and attempts unbinding within one total shutdown bound.
     * @param grace nonnegative total bound
     * @return termination snapshot */
    public synchronized CompletionStage<EndpointTermination> shutdown(Duration grace) {
        CompletionStage<EndpointTermination> result = resources.shutdown(Objects.requireNonNull(grace, "grace"), false);
        closed = true;
        return result;
    }
    /** Observes the separate bounded shutdown result.
     * @return protected termination result */
    public CompletionStage<EndpointTermination> termination() {
        return resources.termination();
    }
    /** Aborts listener/sessions idempotently and requests owned worker shutdown without blocking. */
    @Override
    public synchronized void close() {
        closed = true;
        resources.close();
    }
}
