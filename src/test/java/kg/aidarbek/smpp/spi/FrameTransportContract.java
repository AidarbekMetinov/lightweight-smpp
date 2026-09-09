package kg.aidarbek.smpp.spi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Shared observable port checks for a real TCP peer and the endpoint's immediate-completion fake. */
public final class FrameTransportContract {
    private FrameTransportContract() {}

    /** Runs common ownership, lifecycle, guard, deadline and terminal-notification obligations. */
    public static void verify(FrameTransport transport, Consumer<byte[]> inject, Callable<byte[]> takeWritten)
            throws Exception {
        Observer notAdmitted = new Observer(true);
        assertEquals(
                TransportFailure.Kind.CLOSED,
                assertThrows(
                                TransportFailure.class,
                                () -> transport.write(frame(1), WriteClass.ORDINARY, deadline(), notAdmitted))
                        .kind());
        Listener listener = new Listener();
        transport.start(listener);
        listener.connected.get(3, TimeUnit.SECONDS);
        assertThrows(IllegalStateException.class, () -> transport.start(listener));
        transport.termination().toCompletableFuture().complete(null);
        assertFalse(transport.termination().toCompletableFuture().isDone());
        for (WriteClass writeClass : WriteClass.values()) {
            byte[] proposed = frame(writeClass.ordinal() + 1), expected = proposed.clone();
            Observer observer = new Observer(true);
            WriteHandle handle = transport.write(proposed, writeClass, deadline(), observer);
            Arrays.fill(proposed, (byte) 99);
            assertArrayEquals(expected, takeWritten.call());
            assertNull(observer.result.get(3, TimeUnit.SECONDS));
            assertEquals(1, observer.guards.get());
            assertEquals(1, observer.terminals.get());
            assertFalse(handle.cancel());
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> transport.write(new byte[16], WriteClass.ORDINARY, deadline(), notAdmitted));
        assertEquals(
                TransportFailure.Kind.WRITE_TIMEOUT,
                assertThrows(
                                TransportFailure.class,
                                () -> transport.write(
                                        frame(4), WriteClass.ORDINARY, System.nanoTime() - 1, notAdmitted))
                        .kind());
        Observer refused = new Observer(false);
        transport.write(frame(5), WriteClass.ORDINARY, deadline(), refused);
        assertEquals(
                TransportFailure.Kind.REJECTED,
                refused.result.get(3, TimeUnit.SECONDS).kind());
        assertFalse(refused.result.join().writeStarted());
        assertEquals(1, refused.terminals.get());
        Observer marker = new Observer(true);
        transport.write(frame(6), WriteClass.ORDINARY, deadline(), marker);
        assertArrayEquals(frame(6), takeWritten.call());
        assertNull(marker.result.get(3, TimeUnit.SECONDS));
        byte[] incoming = frame(7), expectedIncoming = incoming.clone();
        inject.accept(incoming);
        Arrays.fill(incoming, (byte) 99);
        assertArrayEquals(expectedIncoming, listener.frames.poll(3, TimeUnit.SECONDS));
        transport.close();
        transport.close();
        assertEquals(
                TransportFailure.Kind.CLOSED,
                listener.closed.get(3, TimeUnit.SECONDS).kind());
        transport.termination().toCompletableFuture().get(3, TimeUnit.SECONDS);
        assertEquals(1, listener.closures.get());
        assertEquals(
                TransportFailure.Kind.CLOSED,
                assertThrows(
                                TransportFailure.class,
                                () -> transport.write(frame(8), WriteClass.CONTROL, deadline(), notAdmitted))
                        .kind());
        assertEquals(0, notAdmitted.guards.get());
        assertEquals(0, notAdmitted.terminals.get());
    }

    private static byte[] frame(int sequence) {
        byte[] frame = HexFormat.of().parseHex("00000010000000150000000000000000");
        frame[15] = (byte) sequence;
        return frame;
    }

    private static long deadline() {
        return System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    }

    private static final class Listener implements FrameListener {
        final CompletableFuture<Void> connected = new CompletableFuture<>();
        final LinkedBlockingQueue<byte[]> frames = new LinkedBlockingQueue<>(2);
        final CompletableFuture<TransportFailure> closed = new CompletableFuture<>();
        final AtomicInteger closures = new AtomicInteger();

        @Override
        public void connected() {
            connected.complete(null);
        }

        @Override
        public void frame(byte[] frame) {
            if (!frames.offer(frame)) throw new IllegalStateException("Contract fixture capacity exceeded");
        }

        @Override
        public void closed(TransportFailure failure) {
            closures.incrementAndGet();
            closed.complete(failure);
        }
    }

    private static final class Observer implements WriteObserver {
        final boolean allow;
        final AtomicInteger guards = new AtomicInteger(), terminals = new AtomicInteger();
        final CompletableFuture<TransportFailure> result = new CompletableFuture<>();

        Observer(boolean allow) {
            this.allow = allow;
        }

        @Override
        public boolean beforeWrite() {
            guards.incrementAndGet();
            return allow;
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
        }
    }
}
