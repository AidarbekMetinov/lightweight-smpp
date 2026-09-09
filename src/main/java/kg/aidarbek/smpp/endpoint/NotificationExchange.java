package kg.aidarbek.smpp.endpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import kg.aidarbek.smpp.protocol.AlertNotification;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.request.BoundedNotifications;
import kg.aidarbek.smpp.spi.TransportFailure;
import kg.aidarbek.smpp.spi.WriteHandle;
import kg.aidarbek.smpp.spi.WriteObserver;

/** Owns local one-way work without any response-window reservation, under the connection state lock. */
final class NotificationExchange {
    private final EndpointConnection connection;
    private final BoundSession session;
    private final ExchangeConfig config;
    private final HandlerDispatcher dispatcher;
    private final HandlerDispatcher.Lane lane;
    private final BoundedNotifications callbacks;
    private final LongSupplier clock;
    private final List<Outgoing> outgoing = new ArrayList<>();
    private final List<Incoming> incoming = new ArrayList<>();

    NotificationExchange(
            EndpointConnection connection,
            BoundSession session,
            ExchangeConfig config,
            HandlerDispatcher dispatcher,
            HandlerDispatcher.Lane lane,
            BoundedNotifications callbacks,
            LongSupplier clock) {
        this.connection = connection;
        this.session = session;
        this.config = config;
        this.dispatcher = dispatcher;
        this.lane = lane;
        this.callbacks = callbacks;
        this.clock = clock;
    }

    NotificationSend send(Command command, byte[] checked, long deadline) {
        if (clock.getAsLong() - deadline >= 0)
            throw new TransportFailure(TransportFailure.Kind.WRITE_TIMEOUT, false, null);
        BoundedNotifications.Reservation callback = callbacks
                .tryReserve()
                .orElseThrow(() -> new RejectedExecutionException("Notification callback capacity unavailable"));
        Outgoing entry;
        try {
            long sequence = connection.notificationSequence(command.commandId());
            byte[] frame = connection.encodeNotification(command, sequence);
            if (frame.length != checked.length) throw new IllegalStateException("Unstable notification encoding");
            entry = new Outgoing(sequence, deadline, callback);
            outgoing.add(entry);
            try {
                entry.handle = connection.writeNotification(frame, deadline, entry);
            } catch (RuntimeException failure) {
                finish(entry, failure);
            }
        } catch (RuntimeException failure) {
            callback.release();
            throw failure;
        }
        return new NotificationSend(entry.sequence, entry.result.minimalCompletionStage(), () -> cancel(entry));
    }

    private boolean cancel(Outgoing entry) {
        connection.coordination.lock();
        try {
            if (!outgoing.contains(entry) || entry.started) return false;
            finish(entry, new TransportFailure(TransportFailure.Kind.CANCELLED, false, null));
            if (entry.handle != null) entry.handle.cancel();
            return true;
        } finally {
            connection.coordination.unlock();
        }
    }

    private void finish(Outgoing entry, Throwable failure) {
        if (!outgoing.remove(entry)) return;
        entry.callback.dispatch(() -> {
            if (failure == null) entry.result.complete(null);
            else entry.result.completeExceptionally(failure);
        });
        connection.messageReplyFinished();
    }

    void receive(Pdu<AlertNotification> pdu, long arrived) {
        var handler = config.handlers().alert();
        if (handler.isEmpty()) return;
        Incoming entry = new Incoming(
                arrived + EndpointOptions.durationNanos(config.options().handlerTimeout()));
        incoming.add(entry);
        try {
            entry.ticket = dispatcher.submit(
                    lane,
                    () -> {
                        if (entry.cancelled.get() || clock.getAsLong() - entry.deadline >= 0)
                            return CompletableFuture.failedFuture(new TimeoutException("Alert invocation expired"));
                        return handler.orElseThrow()
                                .handle(new IncomingNotification<>(session, pdu, entry.deadline, entry.cancelled));
                    },
                    entry.cancelled,
                    (ignored, failure) -> complete(entry, failure));
        } catch (RejectedExecutionException unavailable) {
            incoming.remove(entry);
            connection.fail(unavailable);
        }
    }

    private void complete(Incoming entry, Throwable failure) {
        connection.coordination.lock();
        try {
            if (!incoming.remove(entry)) return;
            if (clock.getAsLong() - entry.deadline >= 0) {
                cancel(entry);
                connection.close();
            } else if (failure != null) connection.close();
            else connection.messageReplyFinished();
        } finally {
            connection.coordination.unlock();
        }
    }

    void expire(long now) {
        for (Outgoing entry : List.copyOf(outgoing)) {
            if (now - entry.deadline >= 0) {
                TransportFailure failure =
                        new TransportFailure(TransportFailure.Kind.WRITE_TIMEOUT, entry.started, null);
                if (entry.started) connection.fail(failure);
                else {
                    finish(entry, failure);
                    if (entry.handle != null) entry.handle.cancel();
                }
            }
        }
        for (Incoming entry : List.copyOf(incoming)) {
            if (now - entry.deadline >= 0) {
                cancel(entry);
                incoming.remove(entry);
                connection.close();
            }
        }
    }

    private void cancel(Incoming entry) {
        entry.cancelled.set(true);
        if (entry.ticket != null) entry.ticket.cancel();
    }

    int pendingCount() {
        return incoming.size() + outgoing.size();
    }

    void close() {
        for (Incoming entry : incoming) cancel(entry);
        incoming.clear();
        for (Outgoing entry : List.copyOf(outgoing)) {
            finish(entry, new TransportFailure(TransportFailure.Kind.CLOSED, entry.started, null));
            if (entry.handle != null) entry.handle.cancel();
        }
    }

    /** Retains one logical incoming decision while dispatcher ownership covers its physical stage. */
    private static final class Incoming {
        final long deadline;
        final AtomicBoolean cancelled = new AtomicBoolean();
        HandlerDispatcher.Ticket<Void> ticket;

        Incoming(long deadline) {
            this.deadline = deadline;
        }
    }

    /** Guards cancellation atomically against physical write and dispatches exactly one local outcome. */
    private final class Outgoing implements WriteObserver {
        final long sequence;
        final long deadline;
        final BoundedNotifications.Reservation callback;
        final CompletableFuture<Void> result = new CompletableFuture<>();
        WriteHandle handle;
        boolean started;

        Outgoing(long sequence, long deadline, BoundedNotifications.Reservation callback) {
            this.sequence = sequence;
            this.deadline = deadline;
            this.callback = callback;
        }

        @Override
        public boolean beforeWrite() {
            connection.coordination.lock();
            try {
                if (!outgoing.contains(this)) return false;
                if (clock.getAsLong() - deadline >= 0) {
                    finish(this, new TransportFailure(TransportFailure.Kind.WRITE_TIMEOUT, false, null));
                    return false;
                }
                started = true;
                return true;
            } finally {
                connection.coordination.unlock();
            }
        }

        @Override
        public void written() {
            connection.coordination.lock();
            try {
                finish(this, null);
            } finally {
                connection.coordination.unlock();
            }
        }

        @Override
        public void failed(TransportFailure failure) {
            connection.coordination.lock();
            try {
                finish(this, failure);
                if (failure.writeStarted() && started) connection.fail(failure);
            } finally {
                connection.coordination.unlock();
            }
        }
    }
}
