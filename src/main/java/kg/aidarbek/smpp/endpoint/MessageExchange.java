package kg.aidarbek.smpp.endpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.spi.TransportFailure;
import kg.aidarbek.smpp.spi.WriteObserver;

/** Tracks one session's application decisions and their paired protocol replies. */
final class MessageExchange {
    private final EndpointConnection connection;
    private final BoundSession session;
    private final ExchangeConfig config;
    private final HandlerDispatcher dispatcher;
    private final HandlerDispatcher.Lane lane;
    private final LongSupplier clock;
    private final List<Entry> pending = new ArrayList<>();
    private long retainedBytes;
    private boolean flushing;

    MessageExchange(
            EndpointConnection connection,
            BoundSession session,
            ExchangeConfig config,
            HandlerDispatcher dispatcher,
            LongSupplier clock) {
        this.connection = connection;
        this.session = session;
        this.config = config;
        this.dispatcher = dispatcher;
        this.clock = clock;
        lane = dispatcher == null ? null : dispatcher.lane();
    }

    void receive(Operation<?, ?> operation, Pdu<Command> request, int requestBytes, long arrived) {
        if (pending.size() >= config.options().maximumReplies()) {
            connection.close();
            return;
        }
        Entry entry = new Entry(
                operation,
                request,
                arrived + EndpointOptions.durationNanos(config.options().handlerTimeout()));
        entry.fallback = connection.encodeMessageResponse(operation, request, negative(entry, 8));
        entry.bytes = (long) requestBytes + entry.fallback.length;
        if (entry.bytes > config.options().maximumReplyBytes() - retainedBytes) {
            connection.close();
            return;
        }
        retainedBytes += entry.bytes;
        pending.add(entry);
        if (clock.getAsLong() - entry.deadline >= 0) {
            complete(entry, negative(entry, 8), null);
            return;
        }
        var handler = config.handlers().find(operation);
        if (handler.isEmpty()) {
            complete(entry, negative(entry, 8), null);
            return;
        }
        try {
            entry.ticket = dispatcher.submit(
                    lane,
                    () -> {
                        if (entry.cancelled.get() || clock.getAsLong() - entry.deadline >= 0)
                            return CompletableFuture.failedFuture(new TimeoutException("Handler invocation expired"));
                        return handler.orElseThrow().invoke(session, request, entry.deadline, entry.cancelled);
                    },
                    entry.cancelled,
                    (response, failure) -> complete(entry, response, failure));
        } catch (RejectedExecutionException full) {
            complete(entry, negative(entry, 0x58), null);
        }
    }

    private HandlerResponse<?> negative(Entry entry, long status) {
        return negative(entry.operation, entry.request, status);
    }

    private <Q extends Command, R extends Command> HandlerResponse<R> negative(
            Operation<Q, R> operation, Pdu<Command> request, long status) {
        return new HandlerResponse<>(
                status,
                operation.negative(operation.requestType().cast(request.command()), connection.messageProfile()));
    }

    private void complete(Entry entry, HandlerResponse<?> response, Throwable failure) {
        synchronized (connection) {
            if (!pending.contains(entry) || entry.frame != null) return;
            if (clock.getAsLong() - entry.deadline >= 0) {
                cancel(entry);
                response = negative(entry, 8);
            }
            try {
                HandlerResponse<?> result =
                        failure == null ? Objects.requireNonNull(response, "handler response") : negative(entry, 8);
                entry.frame = connection.encodeMessageResponse(entry.operation, entry.request, result);
            } catch (RuntimeException invalid) {
                entry.frame = entry.fallback;
            }
            long additional = (long) entry.frame.length - entry.fallback.length;
            if (additional > config.options().maximumReplyBytes() - retainedBytes) entry.frame = entry.fallback;
            else {
                entry.bytes += additional;
                retainedBytes += additional;
            }
            entry.fallback = null;
            flush();
        }
    }

    void expire(long now) {
        for (Entry entry : List.copyOf(pending)) {
            if (entry.frame == null && now - entry.deadline >= 0) {
                cancel(entry);
                complete(entry, negative(entry, 8), null);
            }
        }
        flush();
    }

    private void cancel(Entry entry) {
        entry.cancelled.set(true);
        if (entry.ticket != null) entry.ticket.cancel();
    }

    private void flush() {
        if (flushing) return;
        flushing = true;
        try {
            while (!pending.isEmpty()) {
                Entry entry = pending.getFirst();
                if (entry.frame == null || entry.writing) return;
                if (!entry.attempted) {
                    entry.attempted = true;
                    entry.writeDeadline = connection.messageWriteDeadline();
                }
                if (clock.getAsLong() - entry.writeDeadline >= 0) {
                    connection.fail(new TransportFailure(TransportFailure.Kind.WRITE_TIMEOUT, false, null));
                    return;
                }
                entry.writing = true;
                try {
                    connection.writeMessage(entry.frame, entry.writeDeadline, new ReplyWrite(entry));
                } catch (TransportFailure writeFailure) {
                    if (writeFailure.kind() == TransportFailure.Kind.FULL) entry.writing = false;
                    else connection.fail(writeFailure);
                    return;
                } catch (RuntimeException writeFailure) {
                    connection.fail(writeFailure);
                    return;
                }
            }
        } finally {
            flushing = false;
        }
    }

    int pendingCount() {
        return pending.size();
    }

    void close() {
        for (Entry entry : List.copyOf(pending)) {
            cancel(entry);
        }
        pending.clear();
        retainedBytes = 0;
    }
    /** Retains one received request and its application cancellation state until reply settlement. */
    private static final class Entry {
        final Operation<?, ?> operation;
        final Pdu<Command> request;
        final long deadline;
        final AtomicBoolean cancelled = new AtomicBoolean();
        HandlerDispatcher.Ticket<?> ticket;
        byte[] frame;
        byte[] fallback;
        long bytes;
        long writeDeadline;
        boolean attempted;
        boolean writing;

        Entry(Operation<?, ?> operation, Pdu<Command> request, long deadline) {
            this.operation = operation;
            this.request = request;
            this.deadline = deadline;
        }
    }
    /** Writes only the operation reply; request-result callbacks remain on their existing bounded workers. */
    private final class ReplyWrite implements WriteObserver {
        private final Entry entry;

        ReplyWrite(Entry entry) {
            this.entry = entry;
        }

        @Override
        public boolean beforeWrite() {
            synchronized (connection) {
                return pending.contains(entry);
            }
        }

        @Override
        public void written() {
            synchronized (connection) {
                if (pending.remove(entry)) retainedBytes -= entry.bytes;
                flush();
                connection.messageReplyFinished();
            }
        }

        @Override
        public void failed(TransportFailure failure) {
            connection.fail(failure);
        }
    }
}
