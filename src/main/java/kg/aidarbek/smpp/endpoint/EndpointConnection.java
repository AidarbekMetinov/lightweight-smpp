package kg.aidarbek.smpp.endpoint;

import java.net.SocketAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kg.aidarbek.smpp.codec.CommandDispatchException;
import kg.aidarbek.smpp.profile.MessageDirection;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.protocol.AlertNotification;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.BindResponse;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.ControlCommand;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Outbind;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.PduHeader;
import kg.aidarbek.smpp.protocol.Tlv;
import kg.aidarbek.smpp.request.BoundedNotifications;
import kg.aidarbek.smpp.request.RequestFailure;
import kg.aidarbek.smpp.request.RequestHandle;
import kg.aidarbek.smpp.request.RequestOptions;
import kg.aidarbek.smpp.request.RequestWindow;
import kg.aidarbek.smpp.request.TransmissionCertainty;
import kg.aidarbek.smpp.session.EndpointRole;
import kg.aidarbek.smpp.session.PduDirection;
import kg.aidarbek.smpp.session.ResponseContext;
import kg.aidarbek.smpp.session.SendRequirements;
import kg.aidarbek.smpp.session.SessionDecision;
import kg.aidarbek.smpp.session.SessionPermissions;
import kg.aidarbek.smpp.session.SessionState;
import kg.aidarbek.smpp.session.SessionStateMachine;
import kg.aidarbek.smpp.session.VersionNegotiation;
import kg.aidarbek.smpp.spi.FrameListener;
import kg.aidarbek.smpp.spi.FrameTransport;
import kg.aidarbek.smpp.spi.TransportFailure;
import kg.aidarbek.smpp.spi.WriteClass;
import kg.aidarbek.smpp.spi.WriteHandle;
import kg.aidarbek.smpp.spi.WriteObserver;

/** Serializes one connection's protocol lifecycle over the frame transport port. */
final class EndpointConnection implements FrameListener, AutoCloseable {
    // One reentrant state guard is shared by exchange callbacks; contended virtual callers may unmount.
    final ReentrantLock coordination = new ReentrantLock();

    boolean canSendAlert() {
        coordination.lock();
        try {
            return !closed
                    && !draining
                    && machine.requestPermission(PduDirection.OUTBOUND, 0x102, SendRequirements.COMMON)
                            == SessionDecision.ACCEPTED;
        } finally {
            coordination.unlock();
        }
    }

    NotificationSend sendAlert(
            AlertNotification command, RequestOptions requestedOptions, SendRequirements requirements) {
        long started = nanoClock.getAsLong();
        coordination.lock();
        try {
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(requirements, "requirements");
            if (!canSendAlert()) throw new IllegalStateException("Alert is unavailable in the current lifecycle");
            SendRequirements actual = new SendRequirements(
                    requirements.minimumVersion(),
                    requirements.usesOptionalParameters()
                            || !command.optionalParameters().entries().isEmpty());
            if (machine.requestPermission(PduDirection.OUTBOUND, command.commandId(), actual)
                    != SessionDecision.ACCEPTED)
                throw new IllegalArgumentException("Alert fields exceed negotiated capabilities");
            byte[] frame = pdus.encode(new Pdu<>(0, 1, command), profile());
            long deadline = started
                    + (requestedOptions == null ? options.requestTimeout() : requestedOptions.timeout()).toNanos();
            return notifications.send(command, frame, deadline);
        } finally {
            coordination.unlock();
        }
    }

    long notificationSequence(long commandId) {
        return window.allocateNotificationSequence(commandId);
    }

    byte[] encodeNotification(Command command, long sequence) {
        return pdus.encode(new Pdu<>(0, sequence, command), profile());
    }

    WriteHandle writeNotification(byte[] frame, long deadline, WriteObserver observer) {
        return transport.write(frame, WriteClass.ORDINARY, deadline, observer);
    }

    private static final Set<Long> IMPLEMENTED = Stream.concat(
                    Set.of(1L, 2L, 9L, 6L, 0x15L, 0x0bL, 0x102L).stream(),
                    OperationCatalog.all().stream().map(Operation::commandId))
            .collect(Collectors.toUnmodifiableSet());
    private final UUID id = UUID.randomUUID();
    private final FrameTransport transport;
    private final SocketAddress peer;
    private final MessageCenterBinding server;
    private final ClientConfig client;
    private final EndpointOptions options;
    private final LongSupplier nanoClock;
    private final AuthenticationDispatcher authentication;
    private final BindAuthenticator authenticator;
    private final Consumer<BoundSession> boundListener;
    private final SessionStateMachine machine;
    private final EndpointPdus pdus;
    private final RequestWindow window;
    private final MessageExchange messages;
    private final NotificationExchange notifications;
    private final Map<Long, Pdu<? extends Command>> messageRequests = new HashMap<>();
    private final BoundSession facade = new BoundSession(this);
    private final BoundedNotifications.Reservation boundNotification;
    private final BoundedNotifications.Reservation terminationNotification;
    private final CompletableFuture<Void> terminated = new CompletableFuture<>();
    private final CompletableFuture<BoundSession> bound = new CompletableFuture<>();
    private final CompletionStage<BoundSession> boundResult = bound.minimalCompletionStage();
    private final CompletionStage<Void> termination = terminated.minimalCompletionStage();
    private AuthenticationDispatcher.Ticket authenticationTicket;
    private BindMode mode;
    private boolean closed;
    private boolean notified;
    private RequestHandle<ControlCommand> unbind;
    private RequestHandle<BindResponse> binding;
    private int pendingReplies;
    private boolean closeWhenFlushed;
    private RuntimeException closeReason;
    private final long createdAt;
    private long connectDeadline;
    private boolean outgoingTcp;
    private boolean startInvoked;
    private KeepalivePolicy keepalive;
    private RequestHandle<ControlCommand> heartbeat;
    private long lastInbound;
    private long bindDeadline;
    private OutbindFlow outbindFlow;
    private boolean boundResultExpected;
    private boolean draining;
    private long drainDeadline;

    private EndpointConnection(
            FrameTransport transport,
            SocketAddress peer,
            ClientConfig client,
            MessageCenterBinding server,
            EndpointOptions options,
            BoundedNotifications notifications,
            AuthenticationDispatcher authentication,
            BindAuthenticator authenticator,
            Consumer<BoundSession> boundListener,
            LongSupplier nanoClock,
            HandlerDispatcher handlers,
            ExchangeConfig exchange) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.peer = Objects.requireNonNull(peer, "peer");
        this.client = client;
        boundResultExpected = client != null;
        this.server = server;
        this.options = Objects.requireNonNull(options, "options");
        this.nanoClock = nanoClock;
        long now = nanoClock.getAsLong();
        createdAt = now;
        lastInbound = now;
        outgoingTcp = client != null;
        connectDeadline = now + EndpointOptions.durationNanos(options.connectTimeout());
        bindDeadline = now + EndpointOptions.durationNanos(options.bindTimeout());
        this.authentication = authentication;
        this.authenticator = authenticator;
        this.boundListener = Objects.requireNonNull(boundListener, "boundListener");
        machine = client == null
                ? SessionStateMachine.messageCenter(server.acceptedVersions(), server.advertisedVersion(), IMPLEMENTED)
                : SessionStateMachine.esme(client.requestedVersion(), client.requireAdvertisement(), IMPLEMENTED);
        pdus = new EndpointPdus(client == null ? EndpointRole.MESSAGE_CENTER : EndpointRole.ESME, options.pduLimits());
        window =
                new RequestWindow(id, options.requestWindow(), options.maximumPendingBytes(), notifications, nanoClock);
        messages = new MessageExchange(this, facade, exchange, handlers, nanoClock);
        this.notifications =
                new NotificationExchange(this, facade, exchange, handlers, messages.lane(), notifications, nanoClock);
        boundNotification = notifications.tryReserve().orElseThrow(() -> failure(EndpointException.Reason.CAPACITY));
        terminationNotification = notifications.tryReserve().orElseGet(() -> {
            boundNotification.release();
            throw failure(EndpointException.Reason.CAPACITY);
        });
        transport
                .termination()
                .whenComplete((ignored, error) -> terminationNotification.dispatch(() -> {
                    if (error == null) terminated.complete(null);
                    else terminated.completeExceptionally(unwrap(error));
                }));
    }

    static EndpointConnection server(
            FrameTransport transport,
            SocketAddress peer,
            ServerConfig server,
            EndpointOptions options,
            BoundedNotifications notifications,
            AuthenticationDispatcher authentication,
            BindAuthenticator authenticator,
            Consumer<BoundSession> boundListener) {
        return new EndpointConnection(
                transport,
                peer,
                null,
                MessageCenterBinding.from(server),
                options,
                notifications,
                authentication,
                authenticator,
                boundListener,
                System::nanoTime,
                null,
                ExchangeConfig.defaults());
    }

    static EndpointConnection client(
            FrameTransport transport,
            ClientConfig client,
            EndpointOptions options,
            BoundedNotifications notifications) {
        return client(transport, client, options, notifications, System::nanoTime);
    }

    static EndpointConnection client(
            FrameTransport transport,
            ClientConfig client,
            EndpointOptions options,
            BoundedNotifications notifications,
            LongSupplier nanoClock) {
        return new EndpointConnection(
                transport,
                client.remoteAddress(),
                client,
                null,
                options,
                notifications,
                null,
                null,
                ignored -> {},
                nanoClock,
                null,
                ExchangeConfig.defaults());
    }

    static EndpointConnection client(
            FrameTransport transport,
            ClientConfig client,
            EndpointOptions options,
            BoundedNotifications notifications,
            HandlerDispatcher handlers,
            ExchangeConfig exchange) {
        return client(transport, client, options, notifications, handlers, exchange, System::nanoTime);
    }

    static EndpointConnection client(
            FrameTransport transport,
            ClientConfig client,
            EndpointOptions options,
            BoundedNotifications notifications,
            HandlerDispatcher handlers,
            ExchangeConfig exchange,
            LongSupplier clock) {
        return new EndpointConnection(
                transport,
                client.remoteAddress(),
                client,
                null,
                options,
                notifications,
                null,
                null,
                ignored -> {},
                clock,
                handlers,
                exchange);
    }

    static EndpointConnection server(
            FrameTransport transport,
            SocketAddress peer,
            ServerConfig server,
            EndpointOptions options,
            BoundedNotifications notifications,
            AuthenticationDispatcher authentication,
            BindAuthenticator authenticator,
            Consumer<BoundSession> boundListener,
            HandlerDispatcher handlers,
            ExchangeConfig exchange) {
        return new EndpointConnection(
                transport,
                peer,
                null,
                MessageCenterBinding.from(server),
                options,
                notifications,
                authentication,
                authenticator,
                boundListener,
                System::nanoTime,
                handlers,
                exchange);
    }

    static EndpointConnection outbindListener(
            FrameTransport transport,
            ClientConfig config,
            EndpointOptions options,
            BoundedNotifications notifications,
            HandlerDispatcher handlers,
            ExchangeConfig exchange,
            OutbindAuthenticator authenticator,
            Consumer<BoundSession> boundListener) {
        EndpointConnection connection = new EndpointConnection(
                transport,
                config.remoteAddress(),
                config,
                null,
                options,
                notifications,
                null,
                null,
                boundListener,
                System::nanoTime,
                handlers,
                exchange);
        connection.outbindFlow = new OutbindFlow(connection, null, authenticator, handlers, connection.messages.lane());
        return connection;
    }

    static EndpointConnection outbindConnector(
            FrameTransport transport,
            SocketAddress peer,
            OutbindConnectorConfig config,
            EndpointOptions options,
            BoundedNotifications notifications,
            AuthenticationDispatcher authentication,
            BindAuthenticator authenticator,
            HandlerDispatcher handlers,
            ExchangeConfig exchange,
            Outbind notification) {
        EndpointConnection connection = new EndpointConnection(
                transport,
                peer,
                null,
                MessageCenterBinding.from(config),
                options,
                notifications,
                authentication,
                authenticator,
                ignored -> {},
                System::nanoTime,
                handlers,
                exchange);
        connection.boundResultExpected = true;
        connection.outbindFlow = new OutbindFlow(connection, notification, null, handlers, connection.messages.lane());
        return connection;
    }

    boolean bindTimeRemaining() {
        coordination.lock();
        try {
            return !closed && nanoClock.getAsLong() - bindDeadline < 0;
        } finally {
            coordination.unlock();
        }
    }

    void outbindAuthenticated(boolean accepted, Throwable failure) {
        coordination.lock();
        try {
            if (closed) return;
            if (!accepted || failure != null || !bindTimeRemaining()) {
                fail(failure(EndpointException.Reason.BIND_REJECTED));
                return;
            }
            try {
                beginClientBind();
            } catch (RuntimeException admissionFailure) {
                fail(admissionFailure);
            }
        } finally {
            coordination.unlock();
        }
    }

    void sendOutbind(Outbind command) {
        long sequence = notificationSequence(command.commandId());
        byte[] frame = encodeNotification(command, sequence);
        if (machine.request(PduDirection.OUTBOUND, EndpointPdus.header(frame), SendRequirements.COMMON)
                != SessionDecision.ACCEPTED)
            throw new IllegalStateException("Outbind is unavailable in the current lifecycle");
        outbindFlow.sequence = sequence;
        transport.write(frame, WriteClass.ORDINARY, bindDeadline, new OutbindWrite());
    }

    private final CongestionMonitor congestion = new CongestionMonitor();

    Optional<CongestionObservation> congestion() {
        coordination.lock();
        try {
            return congestion.snapshot();
        } finally {
            coordination.unlock();
        }
    }

    UUID id() {
        return id;
    }

    SocketAddress peer() {
        return peer;
    }

    SessionState state() {
        coordination.lock();
        try {
            return machine.state();
        } finally {
            coordination.unlock();
        }
    }

    BindMode bindMode() {
        coordination.lock();
        try {
            return mode;
        } finally {
            coordination.unlock();
        }
    }

    VersionNegotiation negotiation() {
        coordination.lock();
        try {
            return machine.negotiation().orElseThrow();
        } finally {
            coordination.unlock();
        }
    }

    boolean canSend(Operation<?, ?> operation) {
        coordination.lock();
        try {
            Objects.requireNonNull(operation, "operation");
            return !closed
                    && !draining
                    && OperationCatalog.all().contains(operation)
                    && machine.requestPermission(PduDirection.OUTBOUND, operation.commandId(), SendRequirements.COMMON)
                            == SessionDecision.ACCEPTED;
        } finally {
            coordination.unlock();
        }
    }

    <Q extends Command, R extends Command> RequestHandle<R> send(
            Operation<Q, R> operation, Q command, RequestOptions requestedOptions, SendRequirements requirements) {
        long started = nanoClock.getAsLong();
        coordination.lock();
        try {
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(requirements, "requirements");
            discardFinishedMessageContexts();
            if (!canSend(operation))
                throw new IllegalStateException("Operation is unavailable in the current lifecycle");
            SendRequirements actual = operation.requestRequirements(command, requirements);
            if (command.commandId() != operation.commandId()
                    || machine.requestPermission(PduDirection.OUTBOUND, command.commandId(), actual)
                            != SessionDecision.ACCEPTED)
                throw new IllegalArgumentException("Operation fields exceed negotiated capabilities");
            byte[] checked = pdus.encode(new Pdu<>(0, 1, command), profile());
            RequestOptions requestOptions =
                    requestedOptions == null ? RequestOptions.timeout(options.requestTimeout()) : requestedOptions;
            RequestHandle<R> handle = window.admit(
                    command.commandId(),
                    operation.responseCommandId(),
                    operation.responseType(),
                    checked.length,
                    requestOptions,
                    started);
            if (machine.request(PduDirection.OUTBOUND, handle.requestHeader(), actual) != SessionDecision.ACCEPTED) {
                handle.cancel();
                throw new IllegalStateException("Operation admission lost its lifecycle permission");
            }
            Pdu<Q> request = new Pdu<>(0, handle.identity().sequenceNumber(), command);
            messageRequests.put(handle.identity().sequenceNumber(), request);
            writeRequest(EndpointPdus.assignSequence(checked, handle.identity().sequenceNumber()), handle);
            discardFinishedMessageContexts();
            return handle;
        } finally {
            coordination.unlock();
        }
    }

    RequestHandle<ControlCommand> control(ControlCommand.Type type, RequestOptions requestedOptions) {
        long started = nanoClock.getAsLong();
        coordination.lock();
        try {
            if (draining) throw new IllegalStateException("Endpoint shutdown has stopped request admission");
            return sendControl(type, requestedOptions, started);
        } finally {
            coordination.unlock();
        }
    }

    private RequestHandle<ControlCommand> sendControl(
            ControlCommand.Type type, RequestOptions requestedOptions, long started) {
        ControlCommand command = EndpointPdus.control(type);
        if (closed
                || machine.requestPermission(PduDirection.OUTBOUND, command.commandId(), SendRequirements.COMMON)
                        != SessionDecision.ACCEPTED)
            throw new IllegalStateException("Control request is unavailable in this state");
        RequestOptions requestOptions =
                requestedOptions == null ? RequestOptions.timeout(options.requestTimeout()) : requestedOptions;
        byte[] checked = pdus.encode(new Pdu<>(0, 1, command), profile());
        RequestHandle<ControlCommand> handle = window.admit(
                command.commandId(),
                command.commandId() | 0x8000_0000L,
                ControlCommand.class,
                checked.length,
                requestOptions,
                started);
        if (machine.request(PduDirection.OUTBOUND, handle.requestHeader(), SendRequirements.COMMON)
                != SessionDecision.ACCEPTED) {
            handle.cancel();
            throw new IllegalStateException("Control admission lost its lifecycle permission");
        }
        if (type == ControlCommand.Type.UNBIND) unbind = handle;
        writeRequest(EndpointPdus.assignSequence(checked, handle.identity().sequenceNumber()), handle);
        return handle;
    }

    Optional<RuntimeException> closeReason() {
        coordination.lock();
        try {
            return closed ? Optional.ofNullable(closeReason) : Optional.empty();
        } finally {
            coordination.unlock();
        }
    }

    SessionResources resources() {
        coordination.lock();
        try {
            return new SessionResources(
                    window.pendingCount(), window.pendingBytes(), messages.pendingCount(), messages.retainedBytes());
        } finally {
            coordination.unlock();
        }
    }

    CompletionStage<Void> termination() {
        return termination;
    }

    CompletionStage<Void> ioTermination() {
        return transport.termination();
    }

    CompletionStage<BoundSession> bound() {
        return boundResult;
    }

    boolean cancelBind() {
        coordination.lock();
        try {
            if (!boundResultExpected || closed || notified) return false;
            boolean won = binding == null || binding.cancel();
            closeReason = binding == null
                    ? failure(EndpointException.Reason.CANCELLED)
                    : binding.terminalOutcome()
                            .orElseThrow()
                            .failure()
                            .orElseGet(() -> failure(EndpointException.Reason.CLOSED));
            close();
            return won;
        } finally {
            coordination.unlock();
        }
    }

    void fail(RuntimeException failure) {
        coordination.lock();
        try {
            if (!closed) closeReason = failure;
            close();
        } finally {
            coordination.unlock();
        }
    }

    Optional<BoundSession> boundSession() {
        coordination.lock();
        try {
            return boundState() && notified ? Optional.of(facade) : Optional.empty();
        } finally {
            coordination.unlock();
        }
    }

    void beginShutdown(long deadline) {
        coordination.lock();
        try {
            draining = true;
            drainDeadline = deadline;
            if (boundState()) advanceShutdown();
            else if (machine.state() != SessionState.UNBINDING) close();
        } finally {
            coordination.unlock();
        }
    }

    private void advanceShutdown() {
        if (!draining
                || closed
                || !boundState()
                || window.pendingCount() != 0
                || messages.pendingCount() != 0
                || notifications.pendingCount() != 0) return;
        long started = nanoClock.getAsLong();
        long remaining = drainDeadline - started;
        if (remaining <= 0) {
            close();
            return;
        }
        try {
            sendControl(
                    ControlCommand.Type.UNBIND,
                    RequestOptions.timeout(Duration.ofNanos(
                            Math.min(remaining, EndpointOptions.durationNanos(options.requestTimeout())))),
                    started);
        } catch (RequestFailure unavailable) {
            if (unavailable.reason() != RequestFailure.Reason.NOTIFICATION_BACKLOG
                    && unavailable.reason() != RequestFailure.Reason.WINDOW_FULL) fail(unavailable);
        } catch (RuntimeException failure) {
            fail(failure);
        }
    }

    void tick(long now) {
        coordination.lock();
        try {
            if (closed) return;
            if (machine.state() == SessionState.CONNECTING && now - connectDeadline >= 0) {
                closeReason = failure(
                        outgoingTcp ? EndpointException.Reason.TRANSPORT : EndpointException.Reason.BIND_TIMEOUT);
                close();
                return;
            }
            window.expire();
            discardFinishedMessageContexts();
            messages.expire(now);
            notifications.expire(now);
            for (RequestHandle<?> handle : new RequestHandle<?>[] {binding, unbind}) {
                if (handle != null
                        && handle.isDone()
                        && handle.terminalOutcome().orElseThrow().failure().isPresent()) {
                    closeReason =
                            handle.terminalOutcome().orElseThrow().failure().orElseThrow();
                    close();
                    return;
                }
            }
            if (machine.state() != SessionState.CONNECTING && !notified && now - bindDeadline >= 0) {
                closeReason = failure(EndpointException.Reason.BIND_TIMEOUT);
                close();
                return;
            }
            maintainKeepalive(now);
            advanceShutdown();
        } finally {
            coordination.unlock();
        }
    }

    private void maintainKeepalive(long now) {
        if (keepalive == null || draining || !boundState()) return;
        if (heartbeat != null) {
            if (!heartbeat.isDone()) return;
            var outcome = heartbeat.terminalOutcome().orElseThrow();
            if (outcome.failure().isPresent()) {
                fail(outcome.failure().orElseThrow());
                return;
            }
            long status = outcome.response().orElseThrow().commandStatus();
            if (status != 0) {
                fail(new EndpointException(EndpointException.Reason.KEEPALIVE_REJECTED, id, status, -1));
                return;
            }
            heartbeat = null;
        }
        long due = lastInbound + keepalive.idleInterval().toNanos();
        if (now - due >= 0) {
            try {
                heartbeat = sendControl(
                        ControlCommand.Type.ENQUIRE_LINK, RequestOptions.timeout(keepalive.responseTimeout()), due);
            } catch (RequestFailure unavailable) {
                if (unavailable.reason() != RequestFailure.Reason.WINDOW_FULL
                        && unavailable.reason() != RequestFailure.Reason.BYTE_LIMIT
                        && unavailable.reason() != RequestFailure.Reason.NOTIFICATION_BACKLOG) fail(unavailable);
            }
        }
    }

    void configureKeepalive(KeepalivePolicy policy) {
        coordination.lock();
        try {
            if (startInvoked) throw new IllegalStateException("Keepalive policy must be fixed before starting");
            keepalive = policy;
        } finally {
            coordination.unlock();
        }
    }

    void configureStartup(long timeoutNanos, boolean outgoing) {
        coordination.lock();
        try {
            if (startInvoked || timeoutNanos <= 0)
                throw new IllegalStateException("Startup policy must be fixed before starting");
            outgoingTcp = outgoing;
            connectDeadline = createdAt + timeoutNanos;
        } finally {
            coordination.unlock();
        }
    }

    void start() {
        coordination.lock();
        try {
            if (startInvoked) throw new IllegalStateException("Connection already started");
            startInvoked = true;
        } finally {
            coordination.unlock();
        }
        transport.start(this);
    }

    @Override
    public void connected() {
        coordination.lock();
        try {
            if (closed) return;
            try {
                machine.connected();
                if (outgoingTcp)
                    bindDeadline = nanoClock.getAsLong() + EndpointOptions.durationNanos(options.bindTimeout());
                if (client != null || outbindFlow != null) {
                    if (outbindFlow == null) beginClientBind();
                    else outbindFlow.connected();
                }
            } catch (RuntimeException failure) {
                fail(failure);
            }
        } finally {
            coordination.unlock();
        }
    }

    /** Starts the ESME bind using the already selected total bind deadline. */
    void beginClientBind() {
        coordination.lock();
        try {
            if (closed
                    || client == null
                    || (machine.state() != SessionState.OPEN && machine.state() != SessionState.OUTBOUND)
                    || binding != null) throw new IllegalStateException("Connection is not ready for an ESME bind");
            long started = nanoClock.getAsLong();
            long remaining = bindDeadline - started;
            if (remaining <= 0) {
                fail(failure(EndpointException.Reason.BIND_TIMEOUT));
                return;
            }
            BindRequest command = client.bind();
            mode = command.mode();
            byte[] checked = pdus.encode(new Pdu<>(0, 1, command), profile());
            binding = window.admit(
                    command.commandId(),
                    mode.responseCommandId(),
                    BindResponse.class,
                    checked.length,
                    RequestOptions.timeout(Duration.ofNanos(remaining)),
                    started);
            machine.bindRequest(
                    PduDirection.OUTBOUND,
                    mode,
                    command.interfaceVersion(),
                    binding.identity().sequenceNumber());
            writeRequest(EndpointPdus.assignSequence(checked, binding.identity().sequenceNumber()), binding);
        } finally {
            coordination.unlock();
        }
    }

    @Override
    public void frame(byte[] frame) {
        long arrived = nanoClock.getAsLong();
        coordination.lock();
        try {
            receiveFrame(frame, arrived);
        } finally {
            coordination.unlock();
        }
    }

    private void receiveFrame(byte[] frame, long arrived) {
        if (closed) return;
        if (arrived - lastInbound > 0) lastInbound = arrived;
        PduHeader raw;
        try {
            raw = EndpointPdus.header(frame);
        } catch (RuntimeException malformedHeader) {
            protocolFailure();
            return;
        }
        Pdu<Command> incoming;
        try {
            incoming = pdus.decode(frame, profile());
        } catch (RuntimeException malformed) {
            if ((raw.commandId() & 0x8000_0000L) != 0) protocolFailure();
            else
                reject(
                        raw,
                        malformed instanceof CommandDispatchException ? 3 : 2,
                        !(malformed instanceof CommandDispatchException));
            return;
        }
        if ((incoming.command().commandId() & 0x8000_0000L) != 0) {
            response(incoming);
        } else if (incoming.command() instanceof BindRequest bind) {
            if (outbindFlow != null
                    && (!outbindFlow.outgoing()
                            || !outbindFlow.sending
                            || (bind.interfaceVersion() == 0x34 && bind.mode() != BindMode.RECEIVER))) {
                reject(raw, 4, true);
                return;
            }
            if (machine.bindRequest(
                            PduDirection.INBOUND, bind.mode(), bind.interfaceVersion(), incoming.sequenceNumber())
                    != SessionDecision.ACCEPTED) {
                reject(raw, boundState() ? 5 : 4, false);
                return;
            }
            mode = bind.mode();
            if (VersionNegotiation.forMessageCenter(
                            bind.interfaceVersion(), server.acceptedVersions(), server.advertisedVersion())
                    .effectiveProfile()
                    .isEmpty()) {
                authenticate(incoming.sequenceNumber(), new BindDecision(0x0d), null);
                return;
            }
            try {
                authenticationTicket = authentication.submit(
                        authenticator,
                        bind,
                        peer,
                        (decision, error) -> authenticate(incoming.sequenceNumber(), decision, error));
            } catch (RejectedExecutionException overloaded) {
                authenticate(incoming.sequenceNumber(), new BindDecision(8), null);
            }
        } else if (incoming.command() instanceof ControlCommand control) {
            PduHeader request = EndpointPdus.header(incoming);
            if (machine.request(PduDirection.INBOUND, request, SendRequirements.COMMON) != SessionDecision.ACCEPTED) {
                reject(raw, 4, false);
                return;
            }
            ControlCommand.Type responseType = control.type() == ControlCommand.Type.UNBIND
                    ? ControlCommand.Type.UNBIND_RESPONSE
                    : ControlCommand.Type.ENQUIRE_LINK_RESPONSE;
            Pdu<ControlCommand> response = new Pdu<>(0, incoming.sequenceNumber(), EndpointPdus.control(responseType));
            SessionDecision decision = control.type() == ControlCommand.Type.UNBIND
                    ? machine.unbindResponse(
                            PduDirection.OUTBOUND, EndpointPdus.header(response), SendRequirements.COMMON)
                    : machine.responsePermission(
                            PduDirection.OUTBOUND,
                            EndpointPdus.header(response),
                            Optional.of(new ResponseContext(PduDirection.INBOUND, request)),
                            SendRequirements.COMMON);
            if (decision == SessionDecision.ACCEPTED) {
                writeReply(pdus.encode(response, profile()), machine.state() == SessionState.CLOSED, () -> {});
            }
        } else if (incoming.command() instanceof Outbind notification) {
            if (outbindFlow == null
                    || machine.request(PduDirection.INBOUND, raw, SendRequirements.COMMON) != SessionDecision.ACCEPTED
                    || !outbindFlow.receive(notification)) protocolFailure();
        } else if (incoming.command() instanceof AlertNotification alert) {
            if (!draining
                    && machine.request(PduDirection.INBOUND, raw, SendRequirements.COMMON) == SessionDecision.ACCEPTED)
                notifications.receive(new Pdu<>(incoming.commandStatus(), incoming.sequenceNumber(), alert), arrived);
            else protocolFailure();
        } else {
            boolean permitted = SessionPermissions.permitsRequest(
                    profile(),
                    client == null ? EndpointRole.ESME : EndpointRole.MESSAGE_CENTER,
                    machine.state(),
                    raw.commandId());
            var operation = OperationCatalog.find(raw.commandId());
            if (permitted && operation.isPresent() && !draining)
                messages.receive(operation.orElseThrow(), incoming, frame.length, arrived);
            else reject(raw, permitted ? 8 : 4, false);
        }
    }

    private void reject(PduHeader offending, long status, boolean closeAfter) {
        byte[] response = pdus.negative(offending, status, profile());
        if (machine.protocolErrorPermission(
                        PduDirection.OUTBOUND,
                        EndpointPdus.header(response),
                        new ResponseContext(PduDirection.INBOUND, offending),
                        SendRequirements.COMMON)
                == SessionDecision.ACCEPTED) {
            writeReply(response, closeAfter, () -> {});
        } else if (closeAfter) protocolFailure();
    }

    private boolean boundState() {
        return machine.state() == SessionState.BOUND_RX
                || machine.state() == SessionState.BOUND_TX
                || machine.state() == SessionState.BOUND_TRX;
    }

    private void protocolFailure() {
        closeReason = failure(EndpointException.Reason.PROTOCOL);
        close();
    }

    private void response(Pdu<Command> response) {
        window.expire();
        discardFinishedMessageContexts();
        Optional<RequestHandle<?>> pending = window.pending(response.sequenceNumber());
        if (pending.isEmpty()) {
            if (outbindFlow != null
                    && !notified
                    && outbindFlow.outgoing()
                    && response.sequenceNumber() == outbindFlow.sequence
                    && response.command().commandId() == 0x80000000L) protocolFailure();
            return;
        }
        RequestHandle<?> handle = pending.orElseThrow();
        if (handle.transmission() == TransmissionCertainty.NOT_SENT) return;
        PduHeader header = EndpointPdus.header(response);
        boolean bindResponse = handle == binding;
        boolean unbinding = handle.requestHeader().commandId() == 6;
        if (!bindResponse
                && !unbinding
                && machine.responsePermission(
                                PduDirection.INBOUND,
                                header,
                                Optional.of(new ResponseContext(PduDirection.OUTBOUND, handle.requestHeader())),
                                SendRequirements.COMMON)
                        != SessionDecision.ACCEPTED) return;
        var operation = OperationCatalog.find(handle.requestHeader().commandId());
        if (operation.isPresent() && response.command().commandId() != 0x80000000L) {
            try {
                operation
                        .orElseThrow()
                        .validateResponse(
                                messageRequests.get(response.sequenceNumber()),
                                response,
                                profile(),
                                client == null ? MessageDirection.DELIVERY : MessageDirection.SUBMISSION);
            } catch (RuntimeException invalidContext) {
                protocolFailure();
                return;
            }
        }
        boolean accepted = window.accept(id, response);
        discardFinishedMessageContexts();
        if (!accepted) return;
        if (bindResponse) {
            OptionalInt advertisement =
                    response.command() instanceof BindResponse bind ? advertisement(bind) : OptionalInt.empty();
            SessionDecision decision = machine.bindResponse(PduDirection.INBOUND, header, advertisement);
            if (decision == SessionDecision.ACCEPTED) {
                congestion.accepted(response, profile(), nanoClock.getAsLong());
                notifyBound();
            } else {
                closeReason = new EndpointException(
                        decision == SessionDecision.VERSION_REJECTED
                                ? EndpointException.Reason.VERSION_REJECTED
                                : EndpointException.Reason.BIND_REJECTED,
                        id,
                        header.commandStatus() == 0 ? -1 : header.commandStatus(),
                        advertisement.orElse(-1));
                close();
            }
        } else if (unbinding) {
            congestion.accepted(response, profile(), nanoClock.getAsLong());
            machine.unbindResponse(PduDirection.INBOUND, header, SendRequirements.COMMON);
            if (machine.state() == SessionState.CLOSED) finishAfterReplies();
        } else {
            congestion.accepted(response, profile(), nanoClock.getAsLong());
            advanceShutdown();
        }
    }

    private void discardFinishedMessageContexts() {
        messageRequests.keySet().removeIf(sequence -> window.pending(sequence).isEmpty());
    }

    private void writeRequest(byte[] frame, RequestHandle<?> handle) {
        try {
            transport.write(
                    frame,
                    (handle == unbind || handle.requestHeader().commandId() == 0x15)
                            ? WriteClass.CONTROL
                            : WriteClass.ORDINARY,
                    handle.deadlineNanos(),
                    new RequestWrite(handle));
        } catch (RuntimeException failure) {
            window.failWrite(handle, failure);
            if (handle == unbind || handle == binding) failLifecycleRequest(handle);
        }
    }

    private void failLifecycleRequest(RequestHandle<?> handle) {
        fail(handle.terminalOutcome()
                .orElseThrow()
                .failure()
                .orElseGet(() -> failure(EndpointException.Reason.CLOSED)));
    }

    private void authenticate(long sequence, BindDecision decision, Throwable error) {
        coordination.lock();
        try {
            if (closed || machine.state() != SessionState.BINDING) return;
            long status = error == null ? decision.commandStatus() : 8;
            OptionalParameters parameters = status == 0
                    ? new OptionalParameters(List.of(new Tlv(
                            0x0210,
                            new byte[] {(byte) server.advertisedVersion().interfaceVersion()})))
                    : EndpointPdus.NO_PARAMETERS;
            Pdu<BindResponse> response = new Pdu<>(
                    status,
                    sequence,
                    new BindResponse(
                            mode, status == 0 ? Optional.of(server.systemId()) : Optional.empty(), parameters));
            SessionDecision result = machine.bindResponse(
                    PduDirection.OUTBOUND,
                    EndpointPdus.header(response),
                    status == 0 ? OptionalInt.of(server.advertisedVersion().interfaceVersion()) : OptionalInt.empty());
            if (result != SessionDecision.ACCEPTED && result != SessionDecision.BIND_REJECTED) {
                close();
                return;
            }
            if (status != 0 && boundResultExpected)
                closeReason = new EndpointException(EndpointException.Reason.BIND_REJECTED, id, status, -1);
            writeReply(pdus.encode(response, profile()), status != 0, this::notifyBound);
        } finally {
            coordination.unlock();
        }
    }

    private void notifyBound() {
        coordination.lock();
        try {
            if (closed || notified) return;
            notified = true;
            lastInbound = nanoClock.getAsLong();
        } finally {
            coordination.unlock();
        }
        boundNotification.dispatch(() -> {
            if (boundResultExpected) bound.complete(facade);
            boundListener.accept(facade);
        });
    }

    private void writeReply(byte[] frame, boolean closeAfter, Runnable afterWrite) {
        pendingReplies++;
        closeWhenFlushed |= closeAfter;
        try {
            transport.write(
                    frame,
                    WriteClass.CONTROL,
                    notified
                            ? nanoClock.getAsLong() + EndpointOptions.durationNanos(options.requestTimeout())
                            : bindDeadline,
                    new ReplyWrite(closeAfter, afterWrite));
        } catch (RuntimeException failure) {
            fail(failure);
        }
    }

    private void finishAfterReplies() {
        closeWhenFlushed = true;
        if (pendingReplies == 0) close();
    }

    ProtocolProfile messageProfile() {
        return profile();
    }

    <Q extends Command, R extends Command> byte[] encodeMessageResponse(
            Operation<Q, R> operation, Pdu<Command> request, HandlerResponse<?> response) {
        R command = operation.responseType().cast(response.command());
        if (command.commandId() != operation.responseCommandId())
            throw new IllegalArgumentException("Incorrect response command");
        SendRequirements requirements = operation.responseRequirements(command, response.requirements());
        Pdu<R> pdu = new Pdu<>(response.commandStatus(), request.sequenceNumber(), command);
        if (machine.responsePermission(
                        PduDirection.OUTBOUND,
                        EndpointPdus.header(pdu),
                        Optional.of(new ResponseContext(PduDirection.INBOUND, EndpointPdus.header(request))),
                        requirements)
                != SessionDecision.ACCEPTED)
            throw new IllegalArgumentException("Response exceeds negotiated capabilities or lifecycle");
        byte[] frame = pdus.encode(pdu, profile());
        operation.validateResponse(
                request, pdu, profile(), client == null ? MessageDirection.SUBMISSION : MessageDirection.DELIVERY);
        return frame;
    }

    long messageWriteDeadline() {
        return nanoClock.getAsLong() + EndpointOptions.durationNanos(options.requestTimeout());
    }

    void writeMessage(byte[] frame, long deadline, WriteObserver observer) {
        transport.write(frame, WriteClass.ORDINARY, deadline, observer);
    }

    void messageReplyFinished() {
        advanceShutdown();
    }

    private ProtocolProfile profile() {
        coordination.lock();
        try {
            return machine.negotiation()
                    .flatMap(VersionNegotiation::effectiveProfile)
                    .orElseGet(() -> ProtocolProfile.forVersion(
                            client == null ? server.advertisedVersion() : client.requestedVersion()));
        } finally {
            coordination.unlock();
        }
    }

    private static OptionalInt advertisement(BindResponse response) {
        return response.optionalParameters().entries().stream()
                .filter(tlv -> tlv.tag() == 0x0210)
                .mapToInt(tlv -> tlv.value()[0] & 255)
                .findFirst();
    }

    private EndpointException failure(EndpointException.Reason reason) {
        return new EndpointException(reason, id, -1, -1);
    }

    static Throwable unwrap(Throwable failure) {
        return failure instanceof java.util.concurrent.CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
    }

    @Override
    public void closed(TransportFailure failure) {
        coordination.lock();
        try {
            if (closed) return;
            window.disconnect(failure);
            closeReason = binding != null && !notified
                    ? binding.terminalOutcome().orElseThrow().failure().orElse(failure)
                    : failure;
            close();
        } finally {
            coordination.unlock();
        }
    }

    @Override
    public void close() {
        coordination.lock();
        try {
            if (closed) return;
            closed = true;
            machine.close();
            messages.close();
            notifications.close();
            window.disconnect(closeReason);
            messageRequests.clear();
            if (authenticationTicket != null) authenticationTicket.cancel();
            if (outbindFlow != null) outbindFlow.close();
            if (!notified) {
                notified = true;
                if (!boundResultExpected) boundNotification.release();
                else {
                    RuntimeException failure =
                            closeReason == null ? failure(EndpointException.Reason.CLOSED) : closeReason;
                    boundNotification.dispatch(() -> bound.completeExceptionally(failure));
                }
            }
        } finally {
            coordination.unlock();
        }
        transport.close();
    }

    /** Writes the single MC outbind without creating a response window entry or application callback. */
    private final class OutbindWrite implements WriteObserver {
        @Override
        public boolean beforeWrite() {
            coordination.lock();
            try {
                if (!bindTimeRemaining()) return false;
                outbindFlow.sending = true;
                return true;
            } finally {
                coordination.unlock();
            }
        }

        @Override
        public void written() {}

        @Override
        public void failed(TransportFailure failure) {
            fail(failure);
        }
    }

    /** Bridges only internal reply completion; application work goes through bounded notifications. */
    private final class ReplyWrite implements WriteObserver {
        private final boolean closeAfter;
        private final Runnable afterWrite;

        ReplyWrite(boolean closeAfter, Runnable afterWrite) {
            this.closeAfter = closeAfter;
            this.afterWrite = afterWrite;
        }

        @Override
        public boolean beforeWrite() {
            coordination.lock();
            try {
                return !closed;
            } finally {
                coordination.unlock();
            }
        }

        @Override
        public void written() {
            coordination.lock();
            try {
                pendingReplies--;
                if (closeAfter || closeWhenFlushed) finishAfterReplies();
                else afterWrite.run();
            } finally {
                coordination.unlock();
            }
        }

        @Override
        public void failed(TransportFailure failure) {
            fail(failure);
        }
    }

    /** Marks request transmission and terminal write failure without application notification on I/O. */
    private final class RequestWrite implements WriteObserver {
        private final RequestHandle<?> handle;

        RequestWrite(RequestHandle<?> handle) {
            this.handle = handle;
        }

        @Override
        public boolean beforeWrite() {
            return window.beginWrite(handle);
        }

        @Override
        public void written() {}

        @Override
        public void failed(TransportFailure failure) {
            window.failWrite(handle, failure);
            if (handle == unbind || handle == binding) failLifecycleRequest(handle);
        }
    }
}
