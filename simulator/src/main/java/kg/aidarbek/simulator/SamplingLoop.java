package kg.aidarbek.simulator;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.LockSupport;

/** One owned platform worker isolates blocking observations from virtual carriers without a queued backlog. */
final class SamplingLoop implements AutoCloseable {
    private final Runnable sample;
    private final Duration interval;
    private final Thread worker;
    private volatile boolean stopping;
    private volatile Throwable failure;
    private volatile long skipped;
    private boolean started;

    SamplingLoop(Runnable sample, Duration interval) {
        this.sample = Objects.requireNonNull(sample, "sample");
        LoadPlan.bounded(interval, false, Duration.ofMinutes(1));
        if (interval.compareTo(Duration.ofMillis(10)) < 0)
            throw new IllegalArgumentException("Sampling interval must be at least 10ms");
        this.interval = interval;
        worker =
                Thread.ofPlatform().daemon().name("smpp-simulator-observations").unstarted(this::run);
    }

    synchronized void start() {
        if (started || stopping) throw new IllegalStateException("Sampling loop starts once before closure");
        started = true;
        worker.start();
    }

    private void run() {
        long period = interval.toNanos();
        long due = System.nanoTime();
        try {
            while (!stopping) {
                sample.run();
                long elapsed = Math.max(0, System.nanoTime() - due);
                long missed = elapsed / period;
                skipped += missed;
                due += (missed + 1) * period;
                long remaining;
                while (!stopping && (remaining = due - System.nanoTime()) > 0) LockSupport.parkNanos(remaining);
            }
        } catch (Throwable failed) {
            failure = failed;
        }
    }

    boolean stop(Duration timeout) throws InterruptedException {
        LoadPlan.bounded(timeout, true, Duration.ofMinutes(1));
        close();
        if (!timeout.isZero() && Thread.currentThread() != worker) worker.join(timeout);
        return !worker.isAlive();
    }

    Throwable failure() {
        return failure;
    }

    long skippedSamples() {
        return skipped;
    }

    @Override
    public synchronized void close() {
        stopping = true;
        LockSupport.unpark(worker);
    }
}
