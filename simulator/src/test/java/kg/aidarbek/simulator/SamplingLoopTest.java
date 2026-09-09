package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class SamplingLoopTest {
    @Test
    void blockedObservationDoesNotBlockTheGeneratorOrAccumulateQueuedSamples() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var returned = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var loop = new SamplingLoop(
                () -> {
                    calls.incrementAndGet();
                    entered.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException failure) {
                        throw new AssertionError(failure);
                    }
                },
                Duration.ofMillis(10));
        Thread owner = Thread.startVirtualThread(() -> {
            loop.start();
            returned.countDown();
        });
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertTrue(returned.await(100, TimeUnit.MILLISECONDS), "Sampling must not block the traffic owner");
            assertFalse(loop.stop(Duration.ofMillis(20)), "A physically blocked sample remains owned");
            assertEquals(1, calls.get(), "No queued samples may accumulate behind the blocked observation");
            release.countDown();
            assertTrue(loop.stop(Duration.ofSeconds(1)));
            assertNull(loop.failure());
        } finally {
            release.countDown();
            loop.close();
            owner.join(Duration.ofSeconds(1));
        }
    }
}
