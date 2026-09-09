package kg.aidarbek.smpp.request;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe completion dispatch with a fixed number of owned virtual workers and bounded reservations.
 *
 * <p>Capacity covers reserved, queued and executing notifications. A notification retains its permit
 * until its Runnable returns, including any synchronous future dependents it invokes. Reservation
 * and dispatch never execute application work on their caller or while holding the dispatcher lock.
 * One worker begins notifications in dispatch order; multiple workers may begin or finish out of order.
 *
 * <p>The creating endpoint owns this resource. Close is idempotent and nonblocking: it rejects new
 * reservations while allowing existing permits to dispatch or release. Every issued permit must be
 * consumed, even after close. A blocked callback or unused permit delays termination but cannot grow
 * this dispatcher's work beyond capacity. There is no forced interruption or silent task discard.
 * Unchecked callback failures are retained by {@link #lastFailure()} and later notifications continue.
 */
public final class BoundedNotifications implements AutoCloseable {
    private final int capacity;
    private final ArrayDeque<Runnable> queued = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private int outstanding;
    private int remainingWorkers;
    private boolean closed;
    private Throwable lastFailure;

    /**
     * Creates the notification capacity and fixed worker count.
     * @param capacity reserved plus running limit
     * @param workers concurrent invocation limit in 1..capacity
     * @throws IllegalArgumentException unless {@code 1 <= workers <= capacity}
     */
    public BoundedNotifications(int capacity, int workers) {
        if (capacity <= 0 || workers <= 0 || workers > capacity) {
            throw new IllegalArgumentException("Require 1 <= workers <= capacity");
        }
        this.capacity = capacity;
        remainingWorkers = workers;
        for (int index = 0; index < workers; index++) {
            Thread.ofVirtual().name("smpp-notification-" + index).start(this::runWorker);
        }
    }

    /**
     * Reserves notification capacity.
     * @return a permit, or empty on overload or close
     */
    public Optional<Reservation> tryReserve() {
        lock.lock();
        try {
            if (closed || outstanding == capacity) {
                return Optional.empty();
            }
            outstanding++;
            return Optional.of(new Reservation());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Reserves and dispatches without waiting for capacity.
     * @param notification work to invoke
     * @return false if unavailable; a false result retains no task or permit
     * @throws NullPointerException if notification is null
     */
    public boolean tryDispatch(Runnable notification) {
        Objects.requireNonNull(notification, "notification");
        Optional<Reservation> reserved = tryReserve();
        reserved.ifPresent(value -> value.dispatch(notification));
        return reserved.isPresent();
    }

    /**
     * Returns reservations still retained.
     * @return outstanding count
     */
    public int outstandingCount() {
        lock.lock();
        try {
            return outstanding;
        } finally {
            lock.unlock();
        }
    }

    /** Closes admission while draining all previously reserved notifications. */
    @Override
    public void close() {
        lock.lock();
        try {
            closed = true;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns whether all workers have stopped.
     * @return true after complete shutdown
     */
    public boolean isTerminated() {
        lock.lock();
        try {
            return remainingWorkers == 0;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Waits for shutdown within a caller-supplied bound.
     * @param timeout wait bound
     * @return true if terminated
     * @throws InterruptedException if interrupted
     * @throws NullPointerException if timeout is null
     * @throws IllegalArgumentException if timeout is negative
     * @throws ArithmeticException if timeout cannot be represented as signed nanoseconds
     */
    public boolean awaitTermination(Duration timeout) throws InterruptedException {
        long remaining = Objects.requireNonNull(timeout, "timeout").toNanos();
        if (remaining < 0) {
            throw new IllegalArgumentException("Wait timeout must not be negative");
        }
        long started = System.nanoTime();
        long bound = remaining;
        lock.lockInterruptibly();
        try {
            remaining = bound - (System.nanoTime() - started);
            while (remainingWorkers != 0 && remaining > 0) {
                changed.awaitNanos(remaining);
                remaining = bound - (System.nanoTime() - started);
            }
            return remainingWorkers == 0;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Reports the most recent unchecked notification failure without stopping other delivery. Endpoint
     * owners should inspect this diagnostic during shutdown; only the latest failure is retained, so
     * arbitrary failing callbacks cannot create an unbounded exception history.
     * @return failure if any
     */
    public Optional<Throwable> lastFailure() {
        lock.lock();
        try {
            return Optional.ofNullable(lastFailure);
        } finally {
            lock.unlock();
        }
    }

    private void runWorker() {
        boolean interrupted = false;
        try {
            while (true) {
                Runnable notification;
                lock.lock();
                try {
                    while (queued.isEmpty()) {
                        if (closed && outstanding == 0) {
                            return;
                        }
                        try {
                            changed.await();
                        } catch (InterruptedException exception) {
                            interrupted = true;
                        }
                    }
                    notification = queued.removeFirst();
                } finally {
                    lock.unlock();
                }
                try {
                    notification.run();
                } catch (RuntimeException | Error failure) {
                    lock.lock();
                    try {
                        lastFailure = failure;
                    } finally {
                        lock.unlock();
                    }
                } finally {
                    lock.lock();
                    try {
                        outstanding--;
                        changed.signalAll();
                    } finally {
                        lock.unlock();
                    }
                }
            }
        } finally {
            lock.lock();
            try {
                remainingWorkers--;
                changed.signalAll();
            } finally {
                lock.unlock();
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** A thread-safe single-use permit owned by its dispatcher; dispatch or release it exactly once. */
    public final class Reservation {
        private boolean consumed;

        private Reservation() {}

        /**
         * Releases an unused permit; subsequent releases are harmless.
         * @return true on the first release
         */
        public boolean release() {
            lock.lock();
            try {
                if (consumed) {
                    return false;
                }
                consumed = true;
                outstanding--;
                changed.signalAll();
                return true;
            } finally {
                lock.unlock();
            }
        }

        /**
         * Queues the reserved notification for owned workers, including after dispatcher close.
         * Successful dispatch consumes the permit; release then has no effect on the executing task.
         * @param notification callback to invoke
         * @throws NullPointerException if notification is null, without consuming the permit
         * @throws IllegalStateException if this permit was already dispatched or released
         */
        public void dispatch(Runnable notification) {
            Objects.requireNonNull(notification, "notification");
            lock.lock();
            try {
                if (consumed) {
                    throw new IllegalStateException("Notification reservation already consumed");
                }
                consumed = true;
                queued.addLast(notification);
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }
}
