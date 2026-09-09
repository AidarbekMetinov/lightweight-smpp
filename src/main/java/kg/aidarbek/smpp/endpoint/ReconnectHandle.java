package kg.aidarbek.smpp.endpoint;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import kg.aidarbek.smpp.request.BoundedNotifications;

/** Explicit ownership of a bounded sequence of fresh connections, with no request or message replay. */
public final class ReconnectHandle implements AutoCloseable {
    private final EndpointResources resources;
    private final ReconnectPolicy policy;
    private final Supplier<ConnectionAttempt> factory;
    private final Consumer<BoundSession> observer;
    private final LongSupplier clock;
    private final BoundedNotifications.Reservation terminalNotification;
    private final CompletableFuture<ReconnectResult> terminated = new CompletableFuture<>();
    private final CompletionStage<ReconnectResult> termination = terminated.minimalCompletionStage();
    private ConnectionAttempt current;
    private int attempts;
    private int published;
    private long nextAttempt;
    private boolean offered;
    private boolean callbackInFlight;
    private boolean finished;
    private ReconnectResult.Reason reason;
    private RuntimeException lastFailure;

    ReconnectHandle(
            EndpointResources resources,
            ReconnectPolicy policy,
            Supplier<ConnectionAttempt> factory,
            Consumer<BoundSession> observer) {
        this(resources, policy, factory, observer, System::nanoTime);
    }

    ReconnectHandle(
            EndpointResources resources,
            ReconnectPolicy policy,
            Supplier<ConnectionAttempt> factory,
            Consumer<BoundSession> observer,
            LongSupplier clock) {
        this.resources = resources;
        this.policy = policy;
        this.factory = factory;
        this.observer = observer;
        this.clock = clock;
        nextAttempt = clock.getAsLong();
        terminalNotification = resources
                .notifications
                .tryReserve()
                .orElseThrow(() -> new EndpointException(EndpointException.Reason.CAPACITY, new UUID(0, 0), -1, -1));
    }

    void tick(long now) {
        ConnectionAttempt candidate;
        ConnectionAttempt created = null;
        synchronized (this) {
            if (reason != null) {
                finishIfStopped();
                return;
            }
            if (current == null) {
                if (callbackInFlight || now - nextAttempt < 0) return;
                if (attempts >= policy.maximumAttempts()) {
                    reason = ReconnectResult.Reason.ATTEMPTS_EXHAUSTED;
                    finishIfStopped();
                    return;
                }
                attempts++;
                offered = false;
                try {
                    created = Objects.requireNonNull(factory.get(), "Connection factory result");
                } catch (RuntimeException failure) {
                    created = new ConnectionAttempt(failure, CompletableFuture.completedFuture(null));
                }
                current = created;
            }
            candidate = current;
        }
        if (created != null) {
            ConnectionAttempt tracked = created;
            tracked.cleanup().whenComplete((ignored, failure) -> retired(tracked, failure));
        }
        if (candidate.connection() == null) return;
        Optional<BoundSession> ready = candidate.connection().boundSession();
        if (ready.isEmpty()) return;
        boolean full;
        synchronized (this) {
            if (current != candidate || reason != null || offered) return;
            Optional<BoundedNotifications.Reservation> callback = resources.notifications.tryReserve();
            full = callback.isEmpty();
            if (!full) {
                offered = true;
                published++;
                callbackInFlight = true;
                BoundSession session = ready.orElseThrow();
                callback.orElseThrow().dispatch(() -> notifySession(session));
            }
        }
        if (full)
            stop(
                    ReconnectResult.Reason.NOTIFICATION_CAPACITY,
                    new EndpointException(
                            EndpointException.Reason.CAPACITY,
                            ready.orElseThrow().id(),
                            -1,
                            -1),
                    true);
    }

    private void notifySession(BoundSession session) {
        try {
            observer.accept(session);
        } catch (RuntimeException | Error failure) {
            stop(ReconnectResult.Reason.CALLBACK_FAILED, asRuntime(failure), true);
        } finally {
            synchronized (this) {
                callbackInFlight = false;
                finishIfStopped();
            }
        }
    }

    private void retired(ConnectionAttempt expected, Throwable cleanupFailure) {
        RuntimeException failure = expected.connection() == null
                ? expected.rejection()
                : expected.connection().closeReason().orElse(null);
        synchronized (this) {
            if (current != expected) return;
            if (failure != null) lastFailure = failure;
            current = null;
            nextAttempt = clock.getAsLong() + policy.retryDelay().toNanos();
            if (cleanupFailure != null) {
                reason = ReconnectResult.Reason.CLEANUP_FAILED;
                lastFailure = asRuntime(EndpointConnection.unwrap(cleanupFailure));
            } else if (reason == null && attempts >= policy.maximumAttempts())
                reason = ReconnectResult.Reason.ATTEMPTS_EXHAUSTED;
            finishIfStopped();
        }
    }

    private boolean stop(ReconnectResult.Reason selected, RuntimeException failure, boolean closeConnection) {
        EndpointConnection connection;
        boolean won;
        synchronized (this) {
            won = reason == null;
            if (won) {
                reason = selected;
                if (failure != null) lastFailure = failure;
            }
            connection = current == null ? null : current.connection();
            finishIfStopped();
        }
        if (won && closeConnection && connection != null) connection.close();
        return won;
    }

    private void finishIfStopped() {
        if (reason == null || current != null || callbackInFlight || finished) return;
        finished = true;
        ReconnectResult result = new ReconnectResult(reason, attempts, published, Optional.ofNullable(lastFailure));
        resources.reconnectFinished(this);
        terminalNotification.dispatch(() -> terminated.complete(result));
    }

    private static RuntimeException asRuntime(Throwable failure) {
        return failure instanceof RuntimeException runtime
                ? runtime
                : new IllegalStateException("Reconnect lifecycle callback failed", failure);
    }

    void endpointClosing() {
        stop(ReconnectResult.Reason.ENDPOINT_CLOSED, null, false);
    }

    /** Returns the currently bound generation, which may close immediately after observation.
     * @return current ready session or empty */
    public Optional<BoundSession> currentSession() {
        EndpointConnection connection;
        synchronized (this) {
            connection = current == null ? null : current.connection();
        }
        return connection == null ? Optional.empty() : connection.boundSession();
    }
    /** Returns the number of lifetime attempts already started.
     * @return initiated attempts */
    public synchronized int attempts() {
        return attempts;
    }
    /** Stops future attempts and aborts current connecting or bound ownership.
     * @return true only if caller cancellation selected the terminal policy */
    public boolean cancel() {
        return stop(ReconnectResult.Reason.CANCELLED, null, true);
    }
    /** Observes the final physical retirement outcome and prior session-observer completion.
     * @return protected terminal observation */
    public CompletionStage<ReconnectResult> termination() {
        return termination;
    }
    /** Cancels this connection sequence without closing its shared endpoint. */
    @Override
    public void close() {
        cancel();
    }
}
