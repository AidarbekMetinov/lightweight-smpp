package kg.aidarbek.simulator;

import java.util.Objects;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/** Single-owner finite waiting with an explicit, bounded final spin interval. */
final class DeadlinePacer {
    private final long spinNanos;
    private final LongSupplier clock;
    private final LongConsumer park;
    private final Runnable spin;

    DeadlinePacer(long spinNanos, LongSupplier clock, LongConsumer park, Runnable spin) {
        if (spinNanos < 0 || spinNanos > 1_000_000) throw new IllegalArgumentException("Spin bound must be 0..1ms");
        this.spinNanos = spinNanos;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.park = Objects.requireNonNull(park, "park");
        this.spin = Objects.requireNonNull(spin, "spin");
    }

    void pause(long nanos) {
        long deadline = clock.getAsLong() + nanos;
        long remaining = nanos;
        while (remaining > 0) {
            if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Simulator interrupted");
            if (remaining > spinNanos) park.accept(remaining - spinNanos);
            else spin.run();
            remaining = deadline - clock.getAsLong();
        }
        if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Simulator interrupted");
    }
}
