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
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kg.aidarbek.smpp.codec.CommandDispatchException;
import kg.aidarbek.smpp.profile.MessageDirection;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.BindResponse;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.ControlCommand;
import kg.aidarbek.smpp.protocol.OptionalParameters;
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
import kg.aidarbek.smpp.spi.WriteObserver;

/** Serializes one connection's protocol lifecycle over the frame transport port. */
final class EndpointConnection implements FrameListener, AutoCloseable {
    private static final Set<Long> IMPLEMENTED = Stream.concat(
                    Set.of(1L, 2L, 9L, 6L, 0x15L).stream(),
                    OperationCatalog.all().stream().map(Operation::commandId))
            .collect(Collectors.toUnmodifiableSet());
    private final UUID id = UUID.randomUUID();
    private final FrameTransport transport;
    private final SocketAddress peer;
    private final ServerConfig server;
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
    private final long connectDeadline;
    private long bindDeadline;
    private boolean draining;
    private long drainDeadline;

    private EndpointConnection(
            FrameTransport transport,
            SocketAddress peer,
            ClientConfig client,
            ServerConfig server,
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
        this.server = server;
        this.options = Objects.requireNonNull(options, "options");
        this.nanoClock = nanoClock;
        long now = nanoClock.getAsLong();
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
                server,
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
                server,
                options,
                notifications,
                authentication,
                authenticator,
                boundListener,
                System::nanoTime,
                handlers,
                exchange);
    }

    UUID id() {
        return id;
    }

    SocketAddress peer() {
        return peer;
    }

    synchronized SessionState state() {
        return machine.state();
    }

    synchronized BindMode bindMode() {
        return mode;
    }

    synchronized VersionNegotiation negotiation() {
        return machine.negotiation().orElseThrow();
    }

    synchronized boolean canSend(Operation<?, ?> operation) {
        Objects.requireNonNull(operation, "operation");
        return !closed
                && !draining
                && OperationCatalog.all().contains(operation)
                && machine.requestPermission(PduDirection.OUTBOUND, operation.commandId(), SendRequirements.COMMON)
                        == SessionDecision.ACCEPTED;
    }

    <Q extends Command, R extends Command> RequestHandle<R> send(
            Operation<Q, R> operation, Q command, RequestOptions requestedOptions, SendRequirements requirements) {
        long started = nanoClock.getAsLong();
        synchronized (this) {
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
            writeRequest(pdus.encode(request, profile()), handle);
            discardFinishedMessageContexts();
            return handle;
        }
    }

    RequestHandle<ControlCommand> control(ControlCommand.Type type, RequestOptions requestedOptions) {
        long started = nanoClock.getAsLong();
        synchronized (this) {
            if (draining) throw new IllegalStateException("Endpoint shutdown has stopped request admission");
            return sendControl(type, requestedOptions, started);
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
        writeRequest(pdus.encode(new Pdu<>(0, handle.identity().sequenceNumber(), command), profile()), handle);
        return handle;
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

    synchronized boolean cancelBind() {
        if (client == null || closed || notified) return false;
        boolean won = binding == null || binding.cancel();
        closeReason = binding == null
                ? failure(EndpointException.Reason.CANCELLED)
                : binding.terminalOutcome()
                        .orElseThrow()
                        .failure()
                        .orElseGet(() -> failure(EndpointException.Reason.CLOSED));
        close();
        return won;
    }

    synchronized void fail(RuntimeException failure) {
        if (!closed) closeReason = failure;
        close();
    }

    synchronized Optional<BoundSession> boundSession() {
        return boundState() && notified ? Optional.of(facade) : Optional.empty();
    }

    synchronized void beginShutdown(long deadline) {
        draining = true;
        drainDeadline = deadline;
        if (boundState()) advanceShutdown();
        else if (machine.state() != SessionState.UNBINDING) close();
    }

    private void advanceShutdown() {
        if (!draining || closed || !boundState() || window.pendingCount() != 0 || messages.pendingCount() != 0) return;
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

    synchronized void tick(long now) {
        if (closed) return;
        if (machine.state() == SessionState.CONNECTING && now - connectDeadline >= 0) {
            closeReason = failure(EndpointException.Reason.TRANSPORT);
            close();
            return;
        }
        window.expire();
        discardFinishedMessageContexts();
        messages.expire(now);
        for (RequestHandle<?> handle : new RequestHandle<?>[] {binding, unbind}) {
            if (handle != null
                    && handle.isDone()
                    && handle.terminalOutcome().orElseThrow().failure().isPresent()) {
                closeReason = handle.terminalOutcome().orElseThrow().failure().orElseThrow();
                close();
                return;
            }
        }
        if (machine.state() != SessionState.CONNECTING && !notified && now - bindDeadline >= 0) {
            closeReason = failure(EndpointException.Reason.BIND_TIMEOUT);
            close();
            return;
        }
        advanceShutdown();
    }

    void start() {
        transport.start(this);
    }

    @Override
    public synchronized void connected() {
        if (closed) return;
        try {
            machine.connected();
            if (client != null) {
                long started = nanoClock.getAsLong();
                bindDeadline = started + EndpointOptions.durationNanos(options.bindTimeout());
                beginClientBind();
            }
        } catch (RuntimeException failure) {
            fail(failure);
        }
    }

    /** Starts the ESME bind using the already selected total bind deadline. */
    synchronized void beginClientBind() {
        if (closed || client == null || machine.state() != SessionState.OPEN || binding != null)
            throw new IllegalStateException("Connection is not ready for an ESME bind");
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
        writeRequest(pdus.encode(new Pdu<>(0, binding.identity().sequenceNumber(), command), profile()), binding);
    }

    @Override
    public void frame(byte[] frame) {
        long arrived = nanoClock.getAsLong();
        synchronized (this) {
            receiveFrame(frame, arrived);
        }
    }

    private void receiveFrame(byte[] frame, long arrived) {
        if (closed) return;
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
        if (pending.isEmpty()) return;
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
            if (decision == SessionDecision.ACCEPTED) notifyBound();
            else {
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
            machine.unbindResponse(PduDirection.INBOUND, header, SendRequirements.COMMON);
            if (machine.state() == SessionState.CLOSED) finishAfterReplies();
        } else advanceShutdown();
    }

    private void discardFinishedMessageContexts() {
        messageRequests.keySet().removeIf(sequence -> window.pending(sequence).isEmpty());
    }

    private void writeRequest(byte[] frame, RequestHandle<?> handle) {
        try {
            transport.write(
                    frame,
                    handle == unbind ? WriteClass.CONTROL : WriteClass.ORDINARY,
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

    private synchronized void authenticate(long sequence, BindDecision decision, Throwable error) {
        if (closed || machine.state() != SessionState.BINDING) return;
        long status = error == null ? decision.commandStatus() : 8;
        OptionalParameters parameters = status == 0
                ? new OptionalParameters(List.of(new Tlv(
                        0x0210, new byte[] {(byte) server.advertisedVersion().interfaceVersion()})))
                : EndpointPdus.NO_PARAMETERS;
        Pdu<BindResponse> response = new Pdu<>(
                status,
                sequence,
                new BindResponse(mode, status == 0 ? Optional.of(server.systemId()) : Optional.empty(), parameters));
        SessionDecision result = machine.bindResponse(
                PduDirection.OUTBOUND,
                EndpointPdus.header(response),
                status == 0 ? OptionalInt.of(server.advertisedVersion().interfaceVersion()) : OptionalInt.empty());
        if (result != SessionDecision.ACCEPTED && result != SessionDecision.BIND_REJECTED) {
            close();
            return;
        }
        writeReply(pdus.encode(response, profile()), status != 0, this::notifyBound);
    }

    private void notifyBound() {
        synchronized (this) {
            if (closed || notified) return;
            notified = true;
        }
        boundNotification.dispatch(() -> {
            if (client != null) bound.complete(facade);
            else boundListener.accept(facade);
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
            close();
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

    private synchronized ProtocolProfile profile() {
        return machine.negotiation()
                .flatMap(VersionNegotiation::effectiveProfile)
                .orElseGet(() -> ProtocolProfile.forVersion(
                        client == null ? server.advertisedVersion() : client.requestedVersion()));
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
    public synchronized void closed(TransportFailure failure) {
        if (closed) return;
        window.disconnect(failure);
        closeReason = binding != null && !notified
                ? binding.terminalOutcome().orElseThrow().failure().orElse(failure)
                : failure;
        close();
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
            machine.close();
            messages.close();
            window.disconnect(closeReason);
            messageRequests.clear();
            if (authenticationTicket != null) authenticationTicket.cancel();
            if (!notified) {
                notified = true;
                if (client == null) boundNotification.release();
                else {
                    RuntimeException failure =
                            closeReason == null ? failure(EndpointException.Reason.CLOSED) : closeReason;
                    boundNotification.dispatch(() -> bound.completeExceptionally(failure));
                }
            }
        }
        transport.close();
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
            synchronized (EndpointConnection.this) {
                return !closed;
            }
        }

        @Override
        public void written() {
            synchronized (EndpointConnection.this) {
                pendingReplies--;
                if (closeAfter || closeWhenFlushed) finishAfterReplies();
                else afterWrite.run();
            }
        }

        @Override
        public void failed(TransportFailure failure) {
            close();
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
