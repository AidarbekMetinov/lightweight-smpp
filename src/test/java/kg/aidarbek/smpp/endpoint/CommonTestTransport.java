package kg.aidarbek.smpp.endpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import kg.aidarbek.smpp.spi.FrameListener;
import kg.aidarbek.smpp.spi.FrameTransport;
import kg.aidarbek.smpp.spi.TransportFailure;
import kg.aidarbek.smpp.spi.WriteClass;
import kg.aidarbek.smpp.spi.WriteHandle;
import kg.aidarbek.smpp.spi.WriteObserver;

/** Controlled bounded port: tests choose when a queued write claims its guard and settles. */
final class CommonTestTransport implements FrameTransport {
    final ArrayBlockingQueue<byte[]> written = new ArrayBlockingQueue<>(16);
    private final List<Pending> pending = new ArrayList<>();
    private final CompletableFuture<Void> terminated = new CompletableFuture<>();
    private FrameListener listener;
    boolean deferred;
    Runnable cancellationReady = () -> {};
    private boolean closed;
    private boolean cleaned;
    private int invoking;
    private Throwable cleanupFailure;

    @Override
    public void start(FrameListener receiver) {
        synchronized (this) {
            if (closed || listener != null) throw new IllegalStateException("Transport already started or closed");
            listener = java.util.Objects.requireNonNull(receiver);
            invoking++;
        }
        try {
            receiver.connected();
        } catch (RuntimeException | Error failure) {
            recordFailure(failure);
            close();
        } finally {
            finishedInvocation();
        }
    }

    void receive(byte[] frame) {
        FrameListener receiver;
        synchronized (this) {
            if (closed || listener == null) return;
            receiver = listener;
            invoking++;
        }
        try {
            receiver.frame(frame.clone());
        } catch (RuntimeException | Error failure) {
            recordFailure(failure);
            close();
        } finally {
            finishedInvocation();
        }
    }

    @Override
    public WriteHandle write(byte[] frame, WriteClass writeClass, long deadline, WriteObserver observer) {
        java.util.Objects.requireNonNull(writeClass);
        java.util.Objects.requireNonNull(observer);
        Pending entry;
        synchronized (this) {
            if (closed || listener == null) throw failure(TransportFailure.Kind.CLOSED, false);
            if (frame.length < 16
                    || frame.length > 65536
                    || java.nio.ByteBuffer.wrap(frame).getInt() != frame.length)
                throw new IllegalArgumentException("Invalid complete frame");
            if (System.nanoTime() - deadline >= 0) throw failure(TransportFailure.Kind.WRITE_TIMEOUT, false);
            if (written.size() + pending.size() >= 16
                    || pending.stream()
                                    .filter(value -> value.writeClass == writeClass)
                                    .count()
                            == 4
                    || frame.length
                            > 65536
                                    - pending.stream()
                                            .filter(value -> value.writeClass == writeClass)
                                            .mapToLong(value -> value.frame.length)
                                            .sum()) throw failure(TransportFailure.Kind.FULL, false);
            entry = new Pending(frame.clone(), observer, writeClass);
            pending.add(entry);
        }
        if (!deferred) {
            claim(entry);
            settle(entry, null);
        }
        return () -> cancel(entry);
    }

    boolean claimNext() {
        Pending entry;
        synchronized (this) {
            entry = pending.stream().filter(value -> !value.claimed).findFirst().orElseThrow();
        }
        return claim(entry);
    }

    private boolean claim(Pending entry) {
        synchronized (this) {
            if (!pending.contains(entry)) return false;
            entry.claimed = true;
            invoking++;
        }
        boolean allowed;
        try {
            allowed = entry.observer.beforeWrite();
        } catch (RuntimeException | Error failure) {
            recordFailure(failure);
            settle(entry, new TransportFailure(TransportFailure.Kind.OBSERVER_FAILED, false, failure));
            close();
            return false;
        } finally {
            synchronized (this) {
                invoking--;
                notifyAll();
            }
        }
        if (!allowed) settle(entry, failure(TransportFailure.Kind.REJECTED, false));
        return allowed;
    }

    void completeClaimed() {
        Pending entry;
        synchronized (this) {
            entry = pending.stream().filter(value -> value.claimed).findFirst().orElseThrow();
        }
        settle(entry, null);
    }

    private boolean cancel(Pending entry) {
        synchronized (this) {
            if (!pending.contains(entry) || entry.claimed) return false;
        }
        cancellationReady.run();
        return settle(entry, failure(TransportFailure.Kind.CANCELLED, false));
    }

    private boolean settle(Pending entry, TransportFailure failure) {
        synchronized (this) {
            if (failure != null && failure.kind() == TransportFailure.Kind.CANCELLED && entry.claimed) return false;
            if (!pending.remove(entry)) return false;
            invoking++;
            if (failure == null) written.add(entry.frame.clone());
        }
        try {
            if (failure == null) entry.observer.written();
            else entry.observer.failed(failure);
        } catch (RuntimeException | Error callbackFailure) {
            recordFailure(callbackFailure);
            close();
        } finally {
            synchronized (this) {
                invoking--;
                notifyAll();
            }
        }
        return true;
    }

    @Override
    public CompletionStage<Void> termination() {
        return terminated.minimalCompletionStage();
    }

    @Override
    public void close() {
        List<Pending> entries;
        FrameListener receiver;
        synchronized (this) {
            if (closed) return;
            closed = true;
            entries = List.copyOf(pending);
            receiver = listener;
        }
        try {
            for (Pending entry : entries) settle(entry, failure(TransportFailure.Kind.CLOSED, entry.claimed));
            if (receiver != null) receiver.closed(failure(TransportFailure.Kind.CLOSED, false));
        } catch (RuntimeException | Error callbackFailure) {
            recordFailure(callbackFailure);
        } finally {
            synchronized (this) {
                cleaned = true;
                notifyAll();
            }
            Thread.ofVirtual().name("smpp-common-fixture-cleanup").start(this::finish);
        }
    }

    private synchronized void recordFailure(Throwable failure) {
        if (cleanupFailure == null) cleanupFailure = failure;
    }

    private synchronized void finishedInvocation() {
        invoking--;
        notifyAll();
    }

    private void finish() {
        Throwable failure;
        boolean interrupted = false;
        synchronized (this) {
            while (!cleaned || invoking != 0 || !pending.isEmpty()) {
                try {
                    wait();
                } catch (InterruptedException stopped) {
                    interrupted = true;
                    recordFailure(stopped);
                }
            }
            failure = cleanupFailure;
        }
        if (interrupted) Thread.currentThread().interrupt();
        if (failure == null) terminated.complete(null);
        else
            terminated.completeExceptionally(
                    new TransportFailure(TransportFailure.Kind.CLEANUP_FAILED, false, failure));
    }

    private static TransportFailure failure(TransportFailure.Kind kind, boolean started) {
        return new TransportFailure(kind, started, null);
    }
    /** Owns an accepted frame until cancellation or an explicitly driven terminal callback. */
    private static final class Pending {
        final byte[] frame;
        final WriteObserver observer;
        final WriteClass writeClass;
        boolean claimed;

        Pending(byte[] frame, WriteObserver observer, WriteClass writeClass) {
            this.frame = frame;
            this.observer = observer;
            this.writeClass = writeClass;
        }
    }
}
