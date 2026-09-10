package kg.aidarbek.smpp.endpoint;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import kg.aidarbek.smpp.spi.FrameListener;
import kg.aidarbek.smpp.spi.FrameTransport;
import kg.aidarbek.smpp.spi.TransportFailure;
import kg.aidarbek.smpp.spi.WriteClass;
import kg.aidarbek.smpp.spi.WriteHandle;
import kg.aidarbek.smpp.spi.WriteObserver;

/** Bounded frame-port fake with explicit deferred output; shared contracts also run on the TCP adapter. */
final class FakeFrameTransport implements FrameTransport {
    final BlockingQueue<byte[]> writes = new LinkedBlockingQueue<>(16);
    final AtomicInteger writtenFrames = new AtomicInteger();
    boolean discardWrittenFrames;
    Runnable beforeWritten = () -> {};
    private final CompletableFuture<Void> termination = new CompletableFuture<>();
    private final Throwable terminationFailure;
    private Throwable callbackCleanupFailure;
    private volatile FrameListener listener;
    private volatile boolean closed;
    private TransportFailure closure;
    private int activeWrites;
    private int listenerInvocations;
    private int ordinaryWrites;
    private int controlWrites;
    private final Set<PendingWrite> owned = new LinkedHashSet<>();
    private boolean publishingTermination;
    private boolean closeCallbackFinished;
    TransportFailure writeFailure;
    boolean failAfterGuard;
    boolean deferWrites;
    final BlockingQueue<PendingWrite> deferred = new LinkedBlockingQueue<>(16);

    FakeFrameTransport() {
        this(null);
    }

    FakeFrameTransport(Throwable terminationFailure) {
        this.terminationFailure = terminationFailure;
    }

    @Override
    public void start(FrameListener installed) {
        synchronized (this) {
            if (listener != null || closed) throw new IllegalStateException("Cannot start transport");
            listener = Objects.requireNonNull(installed, "listener");
            listenerInvocations++;
        }
        invokeListener(installed::connected);
    }

    void receive(byte[] frame) {
        FrameListener receiver;
        synchronized (this) {
            if (listener == null || closed) return;
            receiver = listener;
            listenerInvocations++;
        }
        invokeListener(() -> receiver.frame(frame.clone()));
    }

    private void invokeListener(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException | Error failure) {
            abort(new TransportFailure(TransportFailure.Kind.OBSERVER_FAILED, false, failure));
        } finally {
            synchronized (this) {
                listenerInvocations--;
            }
            publishTermination();
        }
    }

    @Override
    public WriteHandle write(byte[] frame, WriteClass writeClass, long deadline, WriteObserver observer) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(writeClass, "writeClass");
        Objects.requireNonNull(observer, "observer");
        PendingWrite accepted;
        boolean queued;
        synchronized (this) {
            if (closed || listener == null) throw new TransportFailure(TransportFailure.Kind.CLOSED, false, null);
            if (frame.length < 16
                    || frame.length > 1048576
                    || EndpointPdus.header(frame).commandLength() != frame.length)
                throw new IllegalArgumentException("Invalid complete frame");
            if (System.nanoTime() - deadline >= 0)
                throw new TransportFailure(TransportFailure.Kind.WRITE_TIMEOUT, false, null);
            if (writeFailure != null && !failAfterGuard) throw writeFailure;
            if (writes.remainingCapacity() == 0
                    || (writeClass == WriteClass.ORDINARY ? ordinaryWrites : controlWrites) >= 8)
                throw new TransportFailure(TransportFailure.Kind.FULL, false, null);
            accepted = new PendingWrite(frame.clone(), writeClass, deadline, observer);
            owned.add(accepted);
            activeWrites++;
            if (writeClass == WriteClass.ORDINARY) ordinaryWrites++;
            else controlWrites++;
            queued = deferWrites;
            if (queued) deferred.add(accepted);
        }
        if (!queued) accepted.send();
        return accepted;
    }

    final class PendingWrite implements WriteHandle {
        private final byte[] frame;
        private final WriteClass writeClass;
        private final long deadline;
        private final WriteObserver observer;
        private boolean settled;

        PendingWrite(byte[] frame, WriteClass writeClass, long deadline, WriteObserver observer) {
            this.frame = frame;
            this.writeClass = writeClass;
            this.deadline = deadline;
            this.observer = observer;
        }

        void send() {
            settle(null);
        }

        @Override
        public boolean cancel() {
            return settle(new TransportFailure(TransportFailure.Kind.CANCELLED, false, null));
        }

        private boolean settle(TransportFailure cancelled) {
            synchronized (this) {
                if (settled) return false;
                settled = true;
            }
            deferred.remove(this);
            boolean terminalStarted = false;
            boolean writeStarted = false;
            try {
                TransportFailure rejected = cancelled;
                if (rejected == null && System.nanoTime() - deadline >= 0)
                    rejected = new TransportFailure(TransportFailure.Kind.WRITE_TIMEOUT, false, null);
                if (rejected == null && !observer.beforeWrite())
                    rejected = new TransportFailure(TransportFailure.Kind.REJECTED, false, null);
                if (rejected == null) {
                    writeStarted = true;
                    synchronized (FakeFrameTransport.this) {
                        if (closed) rejected = new TransportFailure(closure.kind(), true, closure.getCause());
                        else if (System.nanoTime() - deadline >= 0)
                            rejected = new TransportFailure(TransportFailure.Kind.WRITE_TIMEOUT, true, null);
                        else rejected = writeFailure;
                        if (rejected == null) {
                            if (!discardWrittenFrames) writes.add(frame);
                            writtenFrames.incrementAndGet();
                        }
                    }
                }
                if (rejected != null) {
                    terminalStarted = true;
                    observer.failed(rejected);
                    if (writeStarted && rejected.kind() == TransportFailure.Kind.WRITE_TIMEOUT) abort(rejected);
                } else {
                    beforeWritten.run();
                    terminalStarted = true;
                    observer.written();
                }
            } catch (Throwable failure) {
                TransportFailure reason =
                        new TransportFailure(TransportFailure.Kind.OBSERVER_FAILED, writeStarted, failure);
                if (terminalStarted) recordCallbackFailure(failure);
                else {
                    try {
                        observer.failed(reason);
                    } catch (Throwable terminalFailure) {
                        recordCallbackFailure(terminalFailure);
                    }
                }
                abort(reason);
            } finally {
                synchronized (FakeFrameTransport.this) {
                    activeWrites--;
                    owned.remove(this);
                    if (writeClass == WriteClass.ORDINARY) ordinaryWrites--;
                    else controlWrites--;
                }
                publishTermination();
            }
            return true;
        }
    }

    @Override
    public CompletionStage<Void> termination() {
        return termination.minimalCompletionStage();
    }

    @Override
    public void close() {
        abort(new TransportFailure(TransportFailure.Kind.CLOSED, false, null));
    }

    private void abort(TransportFailure reason) {
        List<PendingWrite> abandoned;
        synchronized (this) {
            if (closed) return;
            closed = true;
            closure = reason;
            abandoned = List.copyOf(owned);
        }
        try {
            if (listener != null) listener.closed(reason);
        } catch (Throwable failure) {
            recordCallbackFailure(failure);
        } finally {
            for (PendingWrite pending : abandoned)
                pending.settle(new TransportFailure(reason.kind(), false, reason.getCause()));
            synchronized (this) {
                closeCallbackFinished = true;
            }
            publishTermination();
        }
    }

    private synchronized void recordCallbackFailure(Throwable failure) {
        if (callbackCleanupFailure == null)
            callbackCleanupFailure = new TransportFailure(TransportFailure.Kind.CLEANUP_FAILED, false, failure);
    }

    private void publishTermination() {
        Throwable failure;
        synchronized (this) {
            if (!closed
                    || !closeCallbackFinished
                    || activeWrites != 0
                    || listenerInvocations != 0
                    || publishingTermination) return;
            publishingTermination = true;
            failure = terminationFailure == null ? callbackCleanupFailure : terminationFailure;
        }
        Thread.ofVirtual().name("fake-transport-cleanup").start(() -> {
            if (failure == null) termination.complete(null);
            else termination.completeExceptionally(failure);
        });
    }
}
