package kg.aidarbek.smpp.endpoint;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.session.EndpointRole;
import kg.aidarbek.smpp.spi.FrameTransport;
import kg.aidarbek.smpp.transport.TcpListener;
import kg.aidarbek.smpp.transport.TcpTransportConfig;

/** Explicit ESME listener for bounded authenticated outbind followed by one bind on the accepted socket. */
public final class OutbindListener implements AutoCloseable {
    private final OutbindListenerConfig config;
    private final EndpointOptions options;
    private final OutbindAuthenticator authenticator;
    private final Consumer<BoundSession> boundListener;
    private final ExchangeConfig exchange;
    private final EndpointResources resources;
    private boolean started;
    private boolean closed;
    /** Creates a listener owner without opening a socket.
     * @param config resolved listen address and fixed bind requirements
     * @param options bounded endpoint resources and deadlines
     * @param authenticator asynchronous MC authentication; required before sending ESME credentials
     * @param boundListener off-I/O bound-session notification
     * @param exchange typed handlers and shared physical bounds for alerts, messages and outbind decisions */
    public OutbindListener(
            OutbindListenerConfig config,
            EndpointOptions options,
            OutbindAuthenticator authenticator,
            Consumer<BoundSession> boundListener,
            ExchangeConfig exchange) {
        this.config = Objects.requireNonNull(config, "config");
        this.options = Objects.requireNonNull(options, "options");
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator");
        this.boundListener = Objects.requireNonNull(boundListener, "boundListener");
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        resources = new EndpointResources(options, null, exchange);
    }
    /** Opens the configured listener once.
     * @return protected resolved bound local address */
    public synchronized CompletionStage<InetSocketAddress> start() {
        if (started || closed)
            return CompletableFuture.<InetSocketAddress>failedFuture(
                            new IllegalStateException("Outbind listener can start once before closure"))
                    .minimalCompletionStage();
        started = true;
        try {
            new EndpointPdus(EndpointRole.ESME, options.pduLimits())
                    .encode(
                            new Pdu<>(0, 1, config.bind()),
                            ProtocolProfile.forVersion(
                                    config.bind().interfaceVersion() == 0x34 ? SmppVersion.V3_4 : SmppVersion.V5_0));
            TcpListener listener = TcpListener.bind(
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
        } catch (EndpointException unavailable) {
            return false;
        }
        EndpointConnection connection = null;
        try {
            ClientConfig client =
                    new ClientConfig((InetSocketAddress) peer, config.bind(), config.requireAdvertisement());
            connection = EndpointConnection.outbindListener(
                    transport,
                    client,
                    options,
                    resources.notifications,
                    resources.handlers,
                    exchange,
                    authenticator,
                    boundListener);
            permit.attach(connection);
            connection.start();
            return true;
        } catch (RuntimeException failure) {
            if (connection != null) connection.fail(failure);
            else {
                permit.retire(transport.termination());
                transport.close();
            }
            return false;
        }
    }
    /** Returns currently bound ESME sessions.
     * @return immutable snapshot */
    public List<BoundSession> sessions() {
        return resources.sessions();
    }
    /** Returns connecting, active and closing accepted socket reservations.
     * @return current reserved count */
    public int connectionCount() {
        return resources.connectionCount();
    }
    /** Stops acceptance and drains admitted work within one bound.
     * @param grace nonnegative total shutdown duration
     * @return protected physical cleanup snapshot */
    public synchronized CompletionStage<EndpointTermination> shutdown(Duration grace) {
        CompletionStage<EndpointTermination> result = resources.shutdown(Objects.requireNonNull(grace, "grace"), false);
        closed = true;
        return result;
    }
    /** Observes physical endpoint cleanup separately from any binding result.
     * @return protected cleanup stage */
    public CompletionStage<EndpointTermination> termination() {
        return resources.termination();
    }
    /** Aborts connections and requests bounded cleanup without waiting on application stages. */
    @Override
    public synchronized void close() {
        closed = true;
        resources.close();
    }
}
