package kg.aidarbek.smpp.endpoint;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import kg.aidarbek.smpp.request.BoundedNotifications;

/** Owns endpoint-wide admission, deadline ticking, notification capacity and bounded shutdown. */
final class EndpointResources implements AutoCloseable {
    final EndpointOptions options;
    final BoundedNotifications notifications;
    final HandlerDispatcher handlers;
    final ExchangeConfig exchange;
    private final AuthenticationDispatcher authentication;
    private final ScheduledThreadPoolExecutor timer;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final Map<Permit, EndpointConnection> connections = new LinkedHashMap<>();
    private final Set<ReconnectHandle> reconnects = new LinkedHashSet<>();
    private final List<Throwable> failures = new ArrayList<>();
    private final CompletableFuture<EndpointTermination> terminated = new CompletableFuture<>();
    private final CompletionStage<EndpointTermination> termination = terminated.minimalCompletionStage();
    private Runnable stopListener = () -> {};
    private CompletableFuture<?> listenerTermination = CompletableFuture.completedFuture(null);
    private volatile boolean stopping;
    private volatile long shutdownDeadline;
    private boolean listenerAttached;

    EndpointResources(EndpointOptions options, AuthenticationDispatcher authentication) {
        this(options, authentication, ExchangeConfig.defaults());
    }

    EndpointResources(EndpointOptions options, AuthenticationDispatcher authentication, ExchangeConfig exchange) {
        this.exchange = exchange;
        handlers = new HandlerDispatcher(
                exchange.options().handlerConcurrency(), exchange.options().handlerQueue());
        this.options = options;
        this.authentication = authentication;
        notifications = new BoundedNotifications(
                Math.addExact(options.callbackThreads(), options.callbackQueue()), options.callbackThreads());
        timer = new ScheduledThreadPoolExecutor(
                1, Thread.ofPlatform().daemon().name("smpp-deadlines-", 0).factory());
        timer.setRemoveOnCancelPolicy(true);
        timer.scheduleWithFixedDelay(this::tick, 5, 5, TimeUnit.MILLISECONDS);
    }

    ReconnectHandle reconnect(
            ReconnectPolicy policy, Supplier<ConnectionAttempt> factory, Consumer<BoundSession> observer) {
        lock.lock();
        try {
            if (stopping || reconnects.size() >= options.maximumConnections())
                throw new EndpointException(
                        stopping ? EndpointException.Reason.CLOSED : EndpointException.Reason.CAPACITY,
                        new UUID(0, 0),
                        -1,
                        -1);
            ReconnectHandle handle = new ReconnectHandle(this, policy, factory, observer);
            reconnects.add(handle);
            return handle;
        } finally {
            lock.unlock();
        }
    }

    void reconnectFinished(ReconnectHandle handle) {
        lock.lock();
        try {
            reconnects.remove(handle);
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private List<ReconnectHandle> reconnectSnapshot() {
        lock.lock();
        try {
            return List.copyOf(reconnects);
        } finally {
            lock.unlock();
        }
    }

    Permit reserve() {
        lock.lock();
        try {
            if (stopping || connections.size() >= options.maximumConnections()) {
                throw new EndpointException(
                        stopping ? EndpointException.Reason.CLOSED : EndpointException.Reason.CAPACITY,
                        new UUID(0, 0),
                        -1,
                        -1);
            }
            Permit permit = new Permit();
            connections.put(permit, null);
            return permit;
        } finally {
            lock.unlock();
        }
    }

    int connectionCount() {
        lock.lock();
        try {
            return connections.size();
        } finally {
            lock.unlock();
        }
    }

    List<BoundSession> sessions() {
        return snapshot().stream()
                .map(EndpointConnection::boundSession)
                .flatMap(Optional::stream)
                .toList();
    }

    private List<EndpointConnection> snapshot() {
        lock.lock();
        try {
            return connections.values().stream().filter(value -> value != null).toList();
        } finally {
            lock.unlock();
        }
    }

    void listener(Runnable close, CompletionStage<Void> completion) {
        lock.lock();
        try {
            if (listenerAttached) throw new IllegalStateException("Endpoint already owns a listener");
            listenerAttached = true;
            stopListener = close;
            listenerTermination = completion.toCompletableFuture();
        } finally {
            lock.unlock();
        }
        listenerTermination.whenComplete((ignored, failure) -> {
            if (failure != null) recordFailure(failure);
        });
        if (stopping) close.run();
    }

    private void tick() {
        long now = System.nanoTime();
        for (EndpointConnection connection : snapshot()) {
            try {
                connection.tick(now);
            } catch (RuntimeException failure) {
                connection.close();
            }
        }
        for (ReconnectHandle reconnect : reconnectSnapshot()) reconnect.tick(now);
    }

    CompletionStage<EndpointTermination> termination() {
        return termination;
    }

    CompletionStage<EndpointTermination> shutdown(Duration grace, boolean abort) {
        long nanos = grace.isZero() ? 0 : EndpointOptions.durationNanos(grace);
        boolean launch;
        lock.lock();
        try {
            launch = !stopping;
            if (launch) {
                stopping = true;
                shutdownDeadline = System.nanoTime() + nanos;
            } else if (abort) {
                long shortened = System.nanoTime() + nanos;
                if (shortened - shutdownDeadline < 0) shutdownDeadline = shortened;
            }
            changed.signalAll();
        } finally {
            lock.unlock();
        }
        if (!launch && !abort) return termination;
        stopListener.run();
        for (ReconnectHandle reconnect : reconnectSnapshot()) reconnect.endpointClosing();
        for (EndpointConnection connection : snapshot()) {
            if (abort) connection.close();
            else connection.beginShutdown(shutdownDeadline);
        }
        if (authentication != null) authentication.close();
        if (launch) Thread.ofVirtual().name("smpp-endpoint-cleanup").start(this::finishShutdown);
        return termination;
    }

    @Override
    public void close() {
        shutdown(options.shutdownTimeout(), true);
    }

    private void finishShutdown() {
        boolean interrupted = false;
        try {
            while (connectionCount() > 0 && System.nanoTime() - shutdownDeadline < 0) awaitChange();
            for (EndpointConnection connection : snapshot()) connection.close();
            handlers.close();
            notifications.close();
            timer.shutdown();
            while (!cleanupComplete() && System.nanoTime() - shutdownDeadline < 0) awaitChange();
        } catch (InterruptedException interruption) {
            interrupted = true;
            for (EndpointConnection connection : snapshot()) connection.close();
            handlers.close();
            notifications.close();
            timer.shutdown();
        } finally {
            EndpointTermination result = new EndpointTermination(
                    connectionCount(),
                    authentication == null ? 0 : authentication.outstanding(),
                    notifications.outstandingCount(),
                    handlers.outstanding(),
                    listenerTermination.isDone() && !listenerTermination.isCompletedExceptionally(),
                    timer.isTerminated(),
                    notifications.isTerminated()
                            && handlers.isTerminated()
                            && (authentication == null || authentication.isTerminated()),
                    failureSnapshot());
            if (interrupted) Thread.currentThread().interrupt();
            // Network cleanup and its terminal snapshot are already settled; dependent application
            // actions may occupy only this single endpoint-owned publication thread.
            terminated.complete(result);
        }
    }

    private boolean cleanupComplete() {
        return connectionCount() == 0
                && listenerTermination.isDone()
                && timer.isTerminated()
                && notifications.isTerminated()
                && handlers.isTerminated()
                && (authentication == null || authentication.isTerminated());
    }

    private void recordFailure(Throwable failure) {
        lock.lock();
        try {
            failures.add(EndpointConnection.unwrap(failure));
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private List<Throwable> failureSnapshot() {
        lock.lock();
        try {
            return List.copyOf(failures);
        } finally {
            lock.unlock();
        }
    }

    private void awaitChange() throws InterruptedException {
        lock.lock();
        try {
            long remaining = shutdownDeadline - System.nanoTime();
            if (remaining > 0) changed.awaitNanos(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(10)));
        } finally {
            lock.unlock();
        }
    }

    /** A reserved connection slot, released once after failed construction or physical I/O cleanup. */
    final class Permit {
        private boolean released;
        private boolean attached;
        private final CompletableFuture<Void> retirement = new CompletableFuture<>();

        CompletionStage<Void> retirement() {
            return retirement.minimalCompletionStage();
        }

        void failedRetirement(Throwable failure) {
            recordFailure(failure);
            retirement.completeExceptionally(failure);
        }

        void attach(EndpointConnection connection) {
            lock.lock();
            try {
                if (released || attached) throw new IllegalStateException("Connection reservation already consumed");
                attached = true;
                connections.put(this, connection);
            } finally {
                lock.unlock();
            }
            connection.ioTermination().whenComplete((ignored, failure) -> {
                if (failure == null) release();
                else failedRetirement(failure);
            });
            if (stopping) connection.close();
        }

        void retire(CompletionStage<Void> cleanup) {
            lock.lock();
            try {
                if (released || attached) throw new IllegalStateException("Connection reservation already consumed");
                attached = true;
            } finally {
                lock.unlock();
            }
            cleanup.whenComplete((ignored, failure) -> {
                if (failure == null) release();
                else failedRetirement(failure);
            });
        }

        void release() {
            lock.lock();
            try {
                if (released) return;
                released = true;
                connections.remove(this);
                changed.signalAll();
            } finally {
                lock.unlock();
            }
            retirement.complete(null);
        }
    }
}
