package kg.aidarbek.smpp.endpoint;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Owns globally bounded asynchronous message work and each session's invocation lane. */
final class HandlerDispatcher implements AutoCloseable {
    private final int concurrency;
    private final int maximum;
    private final int queueCapacity;
    private final ThreadPoolExecutor workers;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final ArrayDeque<Ticket<?>> queued = new ArrayDeque<>();
    private final Set<Ticket<?>> retained = new LinkedHashSet<>();
    private int active;
    private boolean closed;

    HandlerDispatcher(int concurrency, int queue) {
        if (concurrency < 1 || queue < 0 || (long) concurrency + queue > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid handler capacity");
        this.concurrency = concurrency;
        maximum = concurrency + queue;
        queueCapacity = queue;
        workers = new ThreadPoolExecutor(
                concurrency,
                concurrency,
                0,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(concurrency),
                Thread.ofPlatform().daemon().name("smpp-handler-", 0).factory());
    }

    Lane lane() {
        return new Lane();
    }

    <R> Ticket<R> submit(
            Lane lane,
            Supplier<? extends CompletionStage<R>> invocation,
            AtomicBoolean cancelled,
            BiConsumer<R, Throwable> completion) {
        Objects.requireNonNull(lane, "lane");
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(cancelled, "cancelled");
        Objects.requireNonNull(completion, "completion");
        if (lane.owner != this) throw new IllegalArgumentException("Lane belongs to a different dispatcher");
        lock.lock();
        try {
            boolean immediate = active < concurrency && !lane.invoking && lane.waiting.isEmpty();
            if (closed || retained.size() >= maximum || (!immediate && queued.size() >= queueCapacity))
                throw new RejectedExecutionException("Handler capacity unavailable");
            Ticket<R> ticket = new Ticket<>(lane, invocation, cancelled, completion);
            retained.add(ticket);
            queued.add(ticket);
            lane.waiting.add(ticket);
            drain();
            return ticket;
        } finally {
            lock.unlock();
        }
    }

    private void drain() {
        while (!closed && active < concurrency && !queued.isEmpty()) {
            Ticket<?> ready = null;
            for (Ticket<?> candidate : queued) {
                if (!candidate.lane.invoking && candidate.lane.waiting.peekFirst() == candidate) {
                    ready = candidate;
                    break;
                }
            }
            if (ready == null) return;
            queued.remove(ready);
            ready.lane.waiting.removeFirst();
            ready.lane.invoking = true;
            ready.started = true;
            active++;
            workers.execute(ready);
        }
    }

    int outstanding() {
        lock.lock();
        try {
            return retained.size();
        } finally {
            lock.unlock();
        }
    }

    boolean isTerminated() {
        lock.lock();
        try {
            return closed && retained.isEmpty() && workers.isTerminated();
        } finally {
            lock.unlock();
        }
    }

    boolean awaitTermination(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + EndpointOptions.durationNanos(timeout);
        lock.lockInterruptibly();
        try {
            while (!isTerminated()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) return false;
                changed.awaitNanos(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(10)));
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            if (closed) return;
            closed = true;
            for (Ticket<?> ticket : List.copyOf(retained)) ticket.cancel();
            workers.shutdown();
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }
    /** A connection-specific scheduling identity, owned by exactly one dispatcher. */
    final class Lane {
        private final HandlerDispatcher owner = HandlerDispatcher.this;
        private final ArrayDeque<Ticket<?>> waiting = new ArrayDeque<>();
        private boolean invoking;
    }

    /** Retains physical invocation/stage ownership after logical cancellation until both finish. */
    final class Ticket<R> implements Runnable {
        private final Lane lane;
        private final Supplier<? extends CompletionStage<R>> invocation;
        private final AtomicBoolean cancelled;
        private final BiConsumer<R, Throwable> completion;
        private final AtomicBoolean stageObserved = new AtomicBoolean();
        private final AtomicBoolean published = new AtomicBoolean();
        private boolean started;
        private boolean invocationFinished;
        private boolean stageFinished;
        private boolean released;

        Ticket(
                Lane lane,
                Supplier<? extends CompletionStage<R>> invocation,
                AtomicBoolean cancelled,
                BiConsumer<R, Throwable> completion) {
            this.lane = lane;
            this.invocation = invocation;
            this.cancelled = cancelled;
            this.completion = completion;
        }

        @Override
        public void run() {
            CompletionStage<R> stage = null;
            try {
                if (cancelled.get()) {
                    completed(null, null);
                    return;
                }
                stage = Objects.requireNonNull(invocation.get(), "handler stage");
                stage.whenComplete(this::completed);
            } catch (Throwable failure) {
                if (stage == null) completed(null, failure);
                else publish(null, failure); // Unobservable stage work remains retained conservatively.
            } finally {
                lock.lock();
                try {
                    invocationFinished = true;
                    lane.invoking = false;
                    releaseIfFinished();
                    drain();
                    changed.signalAll();
                } finally {
                    lock.unlock();
                }
            }
        }

        private void publish(R response, Throwable failure) {
            if (published.compareAndSet(false, true) && !cancelled.get()) completion.accept(response, failure);
        }

        private void completed(R response, Throwable failure) {
            if (!stageObserved.compareAndSet(false, true)) return;
            try {
                publish(response, failure);
            } finally {
                lock.lock();
                try {
                    stageFinished = true;
                    releaseIfFinished();
                    drain();
                    changed.signalAll();
                } finally {
                    lock.unlock();
                }
            }
        }

        private void releaseIfFinished() {
            if (!released && invocationFinished && stageFinished) {
                released = true;
                retained.remove(this);
                active--;
            }
        }

        void cancel() {
            cancelled.set(true);
            lock.lock();
            try {
                if (!started && !released) {
                    released = true;
                    queued.remove(this);
                    lane.waiting.remove(this);
                    retained.remove(this);
                    drain();
                    changed.signalAll();
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
