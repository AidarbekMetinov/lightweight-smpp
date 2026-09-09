package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;

class DecisionQueueTest {
    @Test
    void keepsDelayedAndStalledDecisionsInsideOneBoundUntilResolvedOrClosed() {
        var queue = new DecisionQueue(2);
        try {
            var delayed = queue.defer("reply", 110, false);
            var stalled = queue.defer("withheld", 100, true);
            assertEquals(2, queue.pending());
            assertThrows(RejectedExecutionException.class, () -> queue.defer("overflow", 101, false));
            queue.advance(109);
            assertFalse(delayed.isDone());
            queue.advance(110);
            assertEquals("reply", delayed.join());
            assertFalse(stalled.isDone());
            assertEquals(1, queue.pending());
            queue.close();
            assertTrue(stalled.isCancelled());
            assertEquals(0, queue.pending());
            assertThrows(RejectedExecutionException.class, () -> queue.defer("closed", 111, false));
        } finally {
            queue.close();
        }
    }

    @Test
    void handlesClockWrapAndCancelsPhysicalRetentionOnlyWhenOwnerAdvances() {
        try (var queue = new DecisionQueue(1)) {
            var value = queue.defer(42, Long.MIN_VALUE + 1, false);
            queue.advance(Long.MAX_VALUE);
            assertFalse(value.isDone());
            queue.advance(Long.MIN_VALUE + 1);
            assertTrue(value.isDone());
            assertEquals(42, value.join());
            var cancelled = queue.defer(1, 30, true);
            cancelled.cancel(false);
            assertEquals(1, queue.pending());
            queue.advance(0);
            assertEquals(0, queue.pending());
        }
        assertThrows(IllegalArgumentException.class, () -> new DecisionQueue(0));
    }
}
