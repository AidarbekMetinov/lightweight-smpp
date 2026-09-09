package kg.aidarbek.simulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;

/** A finite delay/stall queue advanced by the simulator owner, never a scheduler backlog. */
final class DecisionQueue implements AutoCloseable {
    private final int capacity;
    private final List<Deferred<?>> waiting = new ArrayList<>();
    private int retained;
    private int peak;
    private long admitted, released, cancelledCount, rejected;
    private boolean closed;

    public DecisionQueue(int capacity) {
        if (capacity < 1 || capacity > 65536) throw new IllegalArgumentException("Decision capacity must be 1..65536");
        this.capacity = capacity;
    }

    public <T> CompletableFuture<T> defer(T value, long deadlineNanos, boolean stalled) {
        return defer(value, deadlineNanos, stalled, () -> false);
    }

    public synchronized <T> CompletableFuture<T> defer(
            T value, long deadlineNanos, boolean stalled, BooleanSupplier cancelled) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(cancelled, "cancelled");
        if (closed || retained == capacity) {
            rejected++;
            throw new RejectedExecutionException("Simulator decision capacity unavailable");
        }
        var future = new CompletableFuture<T>();
        waiting.add(new Deferred<>(value, deadlineNanos, stalled, future, cancelled));
        retained++;
        admitted++;
        peak = Math.max(peak, retained);
        return future;
    }

    public void advance(long now) {
        List<Completion> ready = new ArrayList<>();
        synchronized (this) {
            waiting.removeIf(value -> {
                boolean cancelled = value.cancelled().getAsBoolean();
                if (value.future().isDone() || cancelled || !value.stalled() && now - value.deadline() >= 0) {
                    ready.add(new Completion(value, cancelled));
                    return true;
                }
                return false;
            });
        }
        for (var value : ready) {
            try {
                value.complete();
            } finally {
                synchronized (this) {
                    retained--;
                    if (value.deferred().future().isCancelled()) cancelledCount++;
                    else released++;
                }
            }
        }
    }

    public synchronized int pending() {
        return retained;
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(admitted, released, cancelledCount, rejected, retained, peak);
    }

    record Snapshot(long admitted, long released, long cancelled, long rejected, int pending, int peakPending) {}

    @Override
    public void close() {
        List<Deferred<?>> cancelled;
        synchronized (this) {
            closed = true;
            cancelled = List.copyOf(waiting);
            waiting.clear();
        }
        for (var value : cancelled) {
            value.future().cancel(false);
            synchronized (this) {
                retained--;
                if (value.future().isCancelled()) cancelledCount++;
                else released++;
            }
        }
    }

    private record Completion(Deferred<?> deferred, boolean cancelled) {
        void complete() {
            if (cancelled) deferred.future().cancel(false);
            else deferred.complete();
        }
    }

    private record Deferred<T>(
            T value, long deadline, boolean stalled, CompletableFuture<T> future, BooleanSupplier cancelled) {
        void complete() {
            future.complete(value);
        }
    }
}
