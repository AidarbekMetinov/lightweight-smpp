package kg.aidarbek.smpp.endpoint;

import java.net.SocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import kg.aidarbek.smpp.protocol.BindRequest;

/** Owns bounded application authentication execution, separate from wire and session decisions. */
final class AuthenticationDispatcher implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final Executor supplied;
    private final AtomicInteger outstanding = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();

    AuthenticationDispatcher(int concurrency, int queueCapacity, Executor supplied) {
        if (concurrency < 1 || queueCapacity < 0) throw new IllegalArgumentException("Invalid authentication bounds");
        this.supplied = supplied;
        executor = new ThreadPoolExecutor(
                concurrency,
                concurrency,
                0,
                TimeUnit.MILLISECONDS,
                queueCapacity == 0 ? new SynchronousQueue<>() : new ArrayBlockingQueue<>(queueCapacity),
                Thread.ofPlatform().daemon().name("smpp-auth-", 0).factory());
    }

    Ticket submit(
            BindAuthenticator authenticator,
            BindRequest request,
            SocketAddress peer,
            BiConsumer<BindDecision, Throwable> completion) {
        Ticket ticket = new Ticket(authenticator, request, peer, completion);
        outstanding.incrementAndGet();
        try {
            executor.execute(ticket);
        } catch (RuntimeException rejection) {
            ticket.release();
            throw rejection;
        }
        return ticket;
    }

    boolean awaitTermination(Duration timeout) throws InterruptedException {
        return executor.awaitTermination(EndpointOptions.durationNanos(timeout), TimeUnit.NANOSECONDS);
    }

    int outstanding() {
        return outstanding.get();
    }

    boolean isTerminated() {
        return executor.isTerminated();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdown();
            for (Runnable task : List.copyOf(executor.getQueue())) {
                ((Ticket) task).cancel();
            }
        }
    }

    /** Cancellation suppresses late notification; it cannot stop arbitrary application code. */
    final class Ticket implements Runnable {
        private final BindAuthenticator authenticator;
        private final BindRequest request;
        private final SocketAddress peer;
        private final BiConsumer<BindDecision, Throwable> completion;
        private final AtomicBoolean terminal = new AtomicBoolean();
        private final AtomicBoolean released = new AtomicBoolean();

        Ticket(
                BindAuthenticator authenticator,
                BindRequest request,
                SocketAddress peer,
                BiConsumer<BindDecision, Throwable> completion) {
            this.authenticator = Objects.requireNonNull(authenticator, "authenticator");
            this.request = Objects.requireNonNull(request, "request");
            this.peer = Objects.requireNonNull(peer, "peer");
            this.completion = Objects.requireNonNull(completion, "completion");
        }

        @Override
        public void run() {
            try {
                if (terminal.get() || closed.get()) return;
                BindDecision result = null;
                Throwable failure = null;
                try {
                    result = Objects.requireNonNull(invoke(), "authentication stage")
                            .toCompletableFuture()
                            .join();
                    Objects.requireNonNull(result, "authentication decision");
                } catch (Throwable error) {
                    failure =
                            error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
                }
                if (!closed.get() && terminal.compareAndSet(false, true)) completion.accept(result, failure);
            } finally {
                release();
            }
        }

        private CompletionStage<BindDecision> invoke() {
            if (supplied == null) return authenticator.authenticate(request, peer);
            CompletableFuture<CompletionStage<BindDecision>> invocation = new CompletableFuture<>();
            supplied.execute(() -> {
                try {
                    invocation.complete(authenticator.authenticate(request, peer));
                } catch (Throwable error) {
                    invocation.completeExceptionally(error);
                }
            });
            return invocation.join();
        }

        void cancel() {
            terminal.compareAndSet(false, true);
            if (executor.remove(this)) release();
        }

        private void release() {
            if (released.compareAndSet(false, true)) outstanding.decrementAndGet();
        }
    }
}
