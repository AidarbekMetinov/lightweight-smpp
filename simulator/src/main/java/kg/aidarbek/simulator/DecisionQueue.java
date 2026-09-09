package kg.aidarbek.simulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

/** A finite delay/stall queue advanced by the simulator owner, never a scheduler backlog. */
final class DecisionQueue implements AutoCloseable {
    private final int capacity;
    private final List<Deferred<?>> waiting = new ArrayList<>();
    private int retained;
    private boolean closed;

    public DecisionQueue(int capacity) {
        if (capacity < 1 || capacity > 65536) throw new IllegalArgumentException("Decision capacity must be 1..65536");
        this.capacity = capacity;
    }

    public synchronized <T> CompletableFuture<T> defer(T value, long deadlineNanos, boolean stalled) {
        Objects.requireNonNull(value, "value");
        if (closed || retained == capacity)
            throw new RejectedExecutionException("Simulator decision capacity unavailable");
        var future = new CompletableFuture<T>();
        waiting.add(new Deferred<>(value, deadlineNanos, stalled, future));
        retained++;
        return future;
    }

    public void advance(long now) {
        List<Deferred<?>> ready = new ArrayList<>();
        synchronized (this) {
            waiting.removeIf(value -> {
                if (value.future().isDone() || !value.stalled() && now - value.deadline() >= 0) {
                    ready.add(value);
                    return true;
                }
                return false;
            });
        }
        for (var value : ready) {
            value.complete();
            synchronized (this) {
                retained--;
            }
        }
    }

    public synchronized int pending() {
        return retained;
    }

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
            }
        }
    }

    private record Deferred<T>(T value, long deadline, boolean stalled, CompletableFuture<T> future) {
        void complete() {
            future.complete(value);
        }
    }
}
