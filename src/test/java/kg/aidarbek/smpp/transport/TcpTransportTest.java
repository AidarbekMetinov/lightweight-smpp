package kg.aidarbek.smpp.transport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import kg.aidarbek.smpp.spi.FrameListener;
import kg.aidarbek.smpp.spi.TransportFailure;
import kg.aidarbek.smpp.spi.WriteClass;
import kg.aidarbek.smpp.spi.WriteObserver;
import org.junit.jupiter.api.Test;

class TcpTransportTest {
    @Test
    void ownsQueuedFramesAndWritesOrdinaryTrafficInAdmissionOrder() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        try (TestPeer peer = new TestPeer(TcpTransportConfig.defaults())) {
            peer.transport.start(new Events());
            CountDownLatch entered = new CountDownLatch(1);
            Writes first = new Writes(() -> {
                entered.countDown();
                await(release);
                return true;
            });
            Writes second = new Writes(() -> true);
            byte[] firstFrame = frame(16, 1), secondFrame = frame(32, 2);
            peer.transport.write(firstFrame, WriteClass.ORDINARY, deadline(), first);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            byte[] expected = secondFrame.clone();
            peer.transport.write(secondFrame, WriteClass.ORDINARY, deadline(), second);
            Arrays.fill(secondFrame, (byte) 99);
            release.countDown();
            assertArrayEquals(firstFrame, peer.peer.getInputStream().readNBytes(firstFrame.length));
            assertArrayEquals(expected, peer.peer.getInputStream().readNBytes(expected.length));
            assertNull(first.result.get(2, TimeUnit.SECONDS));
            assertNull(second.result.get(2, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
    }

    @Test
    void readsFragmentedAndCoalescedFramesWithOwnedArraysOnVirtualThread() throws Exception {
        try (TestPeer peer = new TestPeer(TcpTransportConfig.defaults())) {
            Events events = new Events();
            peer.transport.start(events);
            byte[] first = frame(32784, 1);
            byte[] second = HexFormat.of().parseHex("00000010000000150000000000000002");
            peer.peer.getOutputStream().write(first, 0, 3);
            byte[] remaining = new byte[first.length - 3 + second.length];
            System.arraycopy(first, 3, remaining, 0, first.length - 3);
            System.arraycopy(second, 0, remaining, first.length - 3, second.length);
            peer.peer.getOutputStream().write(remaining);
            byte[] received = events.frames.poll(2, TimeUnit.SECONDS);
            assertArrayEquals(first, received);
            Arrays.fill(received, (byte) 99);
            assertArrayEquals(second, events.frames.poll(2, TimeUnit.SECONDS));
            assertTrue(events.connected.get(2, TimeUnit.SECONDS));
        }
    }

    static byte[] frame(int length, int sequence) {
        byte[] frame = new byte[length];
        Arrays.fill(frame, 16, length, (byte) 0xa5);
        ByteBuffer.wrap(frame).putInt(length).putInt(0x15).putInt(0).putInt(sequence);
        return frame;
    }

    static long deadline() {
        return System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    }

    static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test coordination expired");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }

    static void awaitUninterruptibly(CountDownLatch latch) {
        boolean interrupted = false;
        long deadline = deadline();
        try {
            while (true) {
                try {
                    if (!latch.await(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS))
                        throw new IllegalStateException("Test coordination expired");
                    return;
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    static final class Writes implements WriteObserver {
        final BooleanSupplier guard;
        final Consumer<TransportFailure> onFailure;
        final CompletableFuture<TransportFailure> result = new CompletableFuture<>();
        final AtomicInteger terminals = new AtomicInteger();
        final AtomicInteger guards = new AtomicInteger();

        Writes(BooleanSupplier guard) {
            this(guard, failure -> {});
        }

        Writes(BooleanSupplier guard, Consumer<TransportFailure> onFailure) {
            this.guard = guard;
            this.onFailure = onFailure;
        }

        @Override
        public boolean beforeWrite() {
            guards.incrementAndGet();
            return guard.getAsBoolean();
        }

        @Override
        public void written() {
            terminals.incrementAndGet();
            result.complete(null);
        }

        @Override
        public void failed(TransportFailure failure) {
            terminals.incrementAndGet();
            result.complete(failure);
            onFailure.accept(failure);
        }
    }

    static final class Events implements FrameListener {
        final Runnable onConnected;
        final Consumer<byte[]> onFrame;
        final Consumer<TransportFailure> onClosed;
        final CompletableFuture<Boolean> connected = new CompletableFuture<>();
        final LinkedBlockingQueue<byte[]> frames = new LinkedBlockingQueue<>(8);
        final CompletableFuture<TransportFailure> closed = new CompletableFuture<>();
        final AtomicInteger closures = new AtomicInteger();

        Events() {
            this(() -> {}, frame -> {});
        }

        Events(Runnable onConnected, Consumer<byte[]> onFrame) {
            this(onConnected, onFrame, failure -> {});
        }

        Events(Runnable onConnected, Consumer<byte[]> onFrame, Consumer<TransportFailure> onClosed) {
            this.onConnected = onConnected;
            this.onFrame = onFrame;
            this.onClosed = onClosed;
        }

        @Override
        public void connected() {
            connected.complete(Thread.currentThread().isVirtual());
            onConnected.run();
        }

        @Override
        public void frame(byte[] frame) {
            if (!frames.offer(frame)) throw new IllegalStateException("Test frame capacity exceeded");
            onFrame.accept(frame);
        }

        @Override
        public void closed(TransportFailure failure) {
            closures.incrementAndGet();
            closed.complete(failure);
            onClosed.accept(failure);
        }
    }
}
