package kg.aidarbek.smpp.endpoint;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import kg.aidarbek.smpp.protocol.Outbind;

/** Holds exactly one explicit notification or bounded authentication decision before ordinary binding. */
final class OutbindFlow {
    private final EndpointConnection connection;
    private final Outbind notification;
    private final OutbindAuthenticator authenticator;
    private final HandlerDispatcher dispatcher;
    private final HandlerDispatcher.Lane lane;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private HandlerDispatcher.Ticket<Boolean> ticket;
    private boolean received;
    boolean sending;
    long sequence;

    OutbindFlow(
            EndpointConnection connection,
            Outbind notification,
            OutbindAuthenticator authenticator,
            HandlerDispatcher dispatcher,
            HandlerDispatcher.Lane lane) {
        this.connection = connection;
        this.notification = notification;
        this.authenticator = authenticator;
        this.dispatcher = dispatcher;
        this.lane = lane;
    }

    boolean outgoing() {
        return notification != null;
    }

    void connected() {
        if (notification != null) connection.sendOutbind(notification);
    }

    boolean receive(Outbind request) {
        if (authenticator == null || received) return false;
        received = true;
        try {
            ticket = dispatcher.submit(
                    lane,
                    () -> {
                        if (cancelled.get() || !connection.bindTimeRemaining())
                            return CompletableFuture.completedFuture(false);
                        return authenticator.authenticate(request, connection.peer());
                    },
                    cancelled,
                    (accepted, failure) -> connection.outbindAuthenticated(Boolean.TRUE.equals(accepted), failure));
        } catch (RejectedExecutionException full) {
            connection.fail(full);
        }
        return true;
    }

    void close() {
        cancelled.set(true);
        if (ticket != null) ticket.cancel();
    }
}
