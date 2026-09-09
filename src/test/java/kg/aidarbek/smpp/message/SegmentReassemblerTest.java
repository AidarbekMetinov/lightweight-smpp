package kg.aidarbek.smpp.message;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import kg.aidarbek.smpp.protocol.OctetString;
import org.junit.jupiter.api.Test;

class SegmentReassemblerTest {
    @Test
    void incompleteExpiryCannotResurrectEarlierPartsAndConcurrentFinalDuplicatesEmitOnce() throws Exception {
        AtomicLong clock = new AtomicLong(0);
        ReassemblyKey key = new ReassemblyKey("scope", 5);
        try (SegmentReassembler assembler = new SegmentReassembler(2, 4, 10, 10, Duration.ofNanos(10), clock::get);
                var executor = Executors.newFixedThreadPool(2)) {
            assembler.accept(key, part(5, 2, 1, 1));
            clock.set(10);
            assertTrue(assembler.accept(key, part(5, 2, 2, 2)).isEmpty());
            assertEquals(1, assembler.retainedSegments());
            clock.set(20);
            assertEquals(1, assembler.expire());
            assembler.accept(key, part(5, 2, 1, 3));
            CountDownLatch start = new CountDownLatch(1);
            var first = executor.submit(() -> {
                start.await();
                return assembler.accept(key, part(5, 2, 2, 4));
            });
            var second = executor.submit(() -> {
                start.await();
                return assembler.accept(key, part(5, 2, 2, 4));
            });
            start.countDown();
            var one = first.get(2, TimeUnit.SECONDS);
            var two = second.get(2, TimeUnit.SECONDS);
            assertTrue(one.isPresent() != two.isPresent());
            assertArrayEquals(new byte[] {3, 4}, one.or(() -> two).orElseThrow().value());
            assertEquals(2, assembler.retainedSegments());
            assertEquals(2, assembler.retainedBytes());
        }
    }

    @Test
    void capacityConflictsAndFixedExpiryAreAtomicEvenAcrossSignedClockWrap() {
        AtomicLong clock = new AtomicLong(Long.MAX_VALUE - 5);
        SegmentReassembler assembler = new SegmentReassembler(1, 2, 3, 3, Duration.ofNanos(10), clock::get);
        ReassemblyKey key = new ReassemblyKey("scope", 1);
        MessageSegment first = part(1, 2, 1, 65, 66);
        assembler.accept(key, first);
        assertThrows(IllegalStateException.class, () -> assembler.accept(new ReassemblyKey("other", 1), first));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(key, part(1, 2, 1, 67)));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(key, part(1, 3, 2, 67)));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(key, part(2, 2, 2, 67)));
        assertThrows(IllegalArgumentException.class, () -> assembler.accept(key, part(1, 2, 2, 67, 68)));
        assertEquals(1, assembler.retainedSegments());
        assertEquals(2, assembler.retainedBytes());
        clock.addAndGet(9);
        assertTrue(assembler.accept(key, first).isEmpty());
        assertEquals(0, assembler.expire());
        assertArrayEquals(
                new byte[] {65, 66, 67},
                assembler.accept(key, part(1, 2, 2, 67)).orElseThrow().value());
        clock.incrementAndGet();
        assertEquals(1, assembler.expire());
        assertEquals(0, assembler.retainedGroups());
        assertEquals(0, assembler.retainedSegments());
        assertEquals(0, assembler.retainedBytes());
        assertTrue(assembler.accept(key, first).isEmpty());
        assembler.close();
        assembler.close();
        assertEquals(0, assembler.retainedBytes());
        assertThrows(IllegalStateException.class, () -> assembler.accept(key, first));
    }

    @Test
    void globalSegmentAndByteLimitsAndReferenceNamespacesAreExplicit() {
        ReassemblyKey key = new ReassemblyKey("scope", 0);
        try (SegmentReassembler count = new SegmentReassembler(3, 1, 20, 20, Duration.ofSeconds(1), () -> 0L)) {
            count.accept(key, part(0, 2, 1, 1));
            assertThrows(IllegalStateException.class, () -> count.accept(key, part(0, 2, 2, 2)));
            assertEquals(1, count.retainedBytes());
        }
        try (SegmentReassembler bytes = new SegmentReassembler(3, 4, 1, 20, Duration.ofSeconds(1), () -> 0L)) {
            bytes.accept(key, part(0, 2, 1, 1));
            assertThrows(IllegalStateException.class, () -> bytes.accept(key, part(0, 2, 2, 2)));
            assertEquals(1, bytes.retainedSegments());
        }
        assertThrows(IllegalArgumentException.class, () -> new ReassemblyKey("x".repeat(257), 0));
        assertThrows(IllegalArgumentException.class, () -> new ReassemblyKey("scope", -1));
        assertThrows(IllegalArgumentException.class, () -> new ReassemblyKey("scope", 65536));
        assertThrows(NullPointerException.class, () -> new ReassemblyKey(null, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SegmentReassembler(0, 1, 1, 1, Duration.ofSeconds(1), () -> 0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SegmentReassembler(1, 0, 1, 1, Duration.ofSeconds(1), () -> 0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SegmentReassembler(1, 1, 0, 1, Duration.ofSeconds(1), () -> 0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SegmentReassembler(1, 1, 1, 0, Duration.ofSeconds(1), () -> 0L));
        assertThrows(IllegalArgumentException.class, () -> new SegmentReassembler(1, 1, 1, 1, Duration.ZERO, () -> 0L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SegmentReassembler(1, 1, 1, 1, Duration.ofSeconds(Long.MAX_VALUE), () -> 0L));
        assertThrows(NullPointerException.class, () -> new SegmentReassembler(1, 1, 1, 1, Duration.ofSeconds(1), null));
    }

    @Test
    void outOfOrderPartsCompleteOnceAndRetainBoundedDuplicateHistory() {
        AtomicLong clock = new AtomicLong(-10);
        try (SegmentReassembler assembler = new SegmentReassembler(2, 4, 20, 10, Duration.ofNanos(100), clock::get)) {
            ReassemblyKey key = new ReassemblyKey("source/destination/encoding", 17);
            MessageSegment first = part(17, 2, 1, 65, 66);
            MessageSegment second = part(17, 2, 2, 67);
            assertTrue(assembler.accept(key, second).isEmpty());
            assertTrue(assembler.accept(key, second).isEmpty());
            assertArrayEquals(
                    new byte[] {65, 66, 67},
                    assembler.accept(key, first).orElseThrow().value());
            assertTrue(assembler.accept(key, first).isEmpty());
            assertTrue(assembler.accept(key, second).isEmpty());
            assertEquals(1, assembler.retainedGroups());
            assertEquals(2, assembler.retainedSegments());
            assertEquals(3, assembler.retainedBytes());
            assertTrue(assembler
                    .accept(new ReassemblyKey("other/source", 17), first)
                    .isEmpty());
            assertEquals(2, assembler.retainedGroups());
        }
    }

    private static MessageSegment part(int reference, int total, int number, int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) bytes[i] = (byte) values[i];
        return new MessageSegment(reference, total, number, new OctetString(bytes));
    }
}
