package kg.aidarbek.smpp.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import javax.net.ssl.SSLSocket;
import kg.aidarbek.smpp.codec.PduFramer;
import kg.aidarbek.smpp.spi.FrameListener;
import kg.aidarbek.smpp.spi.FrameTransport;
import kg.aidarbek.smpp.spi.TransportFailure;
import kg.aidarbek.smpp.spi.WriteClass;
import kg.aidarbek.smpp.spi.WriteHandle;
import kg.aidarbek.smpp.spi.WriteObserver;

/** JDK-socket implementation of the session frame port. */
public final class TcpTransport implements FrameTransport {
    private final Socket socket;
    private final TcpTransportConfig config;
    private final TlsConfig tls;
    private volatile Socket ioSocket;
    private final InetSocketAddress remote;
    private final long connectDeadline;
    private final CompletableFuture<Void> termination = new CompletableFuture<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final ArrayDeque<PendingWrite> ordinary = new ArrayDeque<>();
    private final ArrayDeque<PendingWrite> control = new ArrayDeque<>();
    private final int[] occupiedCount = new int[2];
    private final long[] occupiedBytes = new long[2];
    private Thread reader;
    private Thread writer;
    private Thread deadlines;
    private PendingWrite active;
    private FrameListener listener;
    private boolean started;
    private boolean connected;
    private boolean tcpConnected;
    private boolean handshaking;
    private long handshakeDeadline;
    private boolean closeFinished;
    private long notifying;
    private Throwable cleanupFailure;
    private volatile TransportFailure closure;

    private TcpTransport(Socket socket, TcpTransportConfig config, InetSocketAddress remote, long connectDeadline) {
        this(socket, config, remote, connectDeadline, null);
    }

    private TcpTransport(
            Socket socket, TcpTransportConfig config, InetSocketAddress remote, long connectDeadline, TlsConfig tls) {
        this.socket = socket;
        this.ioSocket = socket;
        this.tls = tls;
        tcpConnected = remote == null;
        handshaking = tls != null && remote == null;
        if (handshaking)
            handshakeDeadline = System.nanoTime() + tls.handshakeTimeout().toNanos();
        this.config = config;
        this.remote = remote;
        this.connectDeadline = connectDeadline;
    }

    /**
     * Returns a pending direct connection; start installs callbacks before connecting or reading.
     * Closing the returned transport cancels connection establishment and any late completion.
     * No DNS lookup occurs here. The deadline starts at the caller's chosen nanoTime instant, so
     * delaying start consumes the same budget. An expired deadline is reported after start.
     * @param remote already resolved remote address
     * @param config per-connection frame and admission bounds
     * @param deadlineNanos absolute System.nanoTime deadline with positive future offset below 2^63 ns
     * @return owned, unstarted connection attempt
     * @throws IllegalArgumentException if the address is unresolved
     */
    public static TcpTransport connect(InetSocketAddress remote, TcpTransportConfig config, long deadlineNanos) {
        Objects.requireNonNull(remote, "remote");
        Objects.requireNonNull(config, "config");
        if (remote.isUnresolved()) throw new IllegalArgumentException("TCP address must already be resolved");
        return new TcpTransport(new Socket(Proxy.NO_PROXY), config, remote, deadlineNanos);
    }

    /** Creates an explicit TLS connection attempt.
     * @param remote resolved TCP destination
     * @param config frame bounds
     * @param deadlineNanos absolute TCP connect deadline
     * @param tls TLS client policy
     * @return owned unstarted transport */
    public static TcpTransport connect(
            InetSocketAddress remote, TcpTransportConfig config, long deadlineNanos, TlsConfig tls) {
        Objects.requireNonNull(remote, "remote");
        Objects.requireNonNull(config, "config");
        if (remote.isUnresolved()) throw new IllegalArgumentException("TCP address must already be resolved");
        if (tls != null && !tls.clientMode())
            throw new IllegalArgumentException("Connecting requires TLS client policy");
        return new TcpTransport(new Socket(Proxy.NO_PROXY), config, remote, deadlineNanos, tls);
    }

    /**
     * Transfers ownership of an open connected socket after argument validation and option setup.
     * On failure, the caller still owns and must close the socket; options may be partly changed.
     * Adoption disables linger and read timeout, enables TCP_NODELAY and applies the send-buffer hint.
     * @param socket open connected socket, neither half closed nor using a nonblocking channel
     * @param config per-connection frame and admission bounds
     * @return owned transport whose I/O begins only after start
     * @throws IllegalArgumentException if the socket is not suitable for blocking frame I/O
     * @throws IOException if socket options cannot be configured
     */
    public static TcpTransport adopt(Socket socket, TcpTransportConfig config) throws IOException {
        return adopt(socket, config, null);
    }

    /** Adopts a connected socket with an independently bounded TLS handshake after start.
     * @param socket connected blocking socket; ownership transfers after validation and option setup
     * @param config frame allocation/admission bounds
     * @param tls explicit TLS role and identity policy, or null for plain TCP
     * @return owned unstarted transport
     * @throws IOException socket option setup failed; caller still owns the socket */
    public static TcpTransport adopt(Socket socket, TcpTransportConfig config, TlsConfig tls) throws IOException {
        Objects.requireNonNull(socket, "socket");
        Objects.requireNonNull(config, "config");
        if (!socket.isConnected()
                || socket.isClosed()
                || socket.isInputShutdown()
                || socket.isOutputShutdown()
                || (socket.getChannel() != null && !socket.getChannel().isBlocking()))
            throw new IllegalArgumentException("Adoption requires an open connected blocking socket");
        configure(socket, config);
        return new TcpTransport(socket, config, null, 0, tls);
    }

    private static void configure(Socket socket, TcpTransportConfig config) throws IOException {
        socket.setSoLinger(false, 0);
        socket.setSoTimeout(0);
        socket.setTcpNoDelay(true);
        if (config.sendBufferBytes() != 0) socket.setSendBufferSize(config.sendBufferBytes());
    }

    @Override
    public void start(FrameListener listener) {
        Objects.requireNonNull(listener, "listener");
        lock.lock();
        try {
            if (started || closure != null) throw new IllegalStateException("Transport already started or closed");
            started = true;
            this.listener = listener;
            reader = Thread.ofVirtual().name("smpp-tcp-read").unstarted(this::readLoop);
            writer = Thread.ofVirtual().name("smpp-tcp-write").unstarted(this::writeLoop);
            deadlines = Thread.ofVirtual().name("smpp-tcp-deadlines").unstarted(this::deadlineLoop);
            reader.start();
            writer.start();
            deadlines.start();
            Thread.ofVirtual().name("smpp-tcp-cleanup").start(this::awaitWorkers);
        } finally {
            lock.unlock();
        }
    }

    private void readLoop() {
        PduFramer framer = new PduFramer(config.maximumFrameLength());
        try {
            if (closure != null) return;
            if (remote != null) {
                long remaining = connectDeadline - System.nanoTime();
                if (remaining <= 0) throw new SocketTimeoutException("Connect deadline expired");
                configure(socket, config);
                int timeoutMillis = (int) Math.min(Integer.MAX_VALUE, (remaining - 1) / 1_000_000 + 1);
                socket.connect(remote, timeoutMillis);
            }
            lock.lock();
            try {
                if (closure != null) return;
                long now = System.nanoTime();
                if (remote != null && now - connectDeadline >= 0)
                    throw new SocketTimeoutException("Connect deadline expired");
                tcpConnected = true;
                if (tls != null && remote != null) {
                    handshaking = true;
                    handshakeDeadline = now + tls.handshakeTimeout().toNanos();
                }
                changed.signalAll();
            } finally {
                lock.unlock();
            }
            if (tls != null) {
                try {
                    SSLSocket secured = tls.layer(socket);
                    ioSocket = secured;
                    tls.configure(secured);
                    secured.startHandshake();
                } catch (IOException | RuntimeException | Error failure) {
                    throw new TransportFailure(TransportFailure.Kind.TLS_HANDSHAKE_FAILED, false, failure);
                }
            }
            lock.lock();
            try {
                if (closure != null) return;
                if (tls != null && System.nanoTime() - handshakeDeadline >= 0)
                    throw new TransportFailure(TransportFailure.Kind.TLS_HANDSHAKE_TIMEOUT, false, null);
                handshaking = false;
                connected = true;
                changed.signalAll();
            } finally {
                lock.unlock();
            }
            try {
                listener.connected();
            } catch (RuntimeException | Error failure) {
                throw new TransportFailure(TransportFailure.Kind.OBSERVER_FAILED, false, failure);
            }
            byte[] buffer = new byte[Math.min(8192, config.maximumFrameLength())];
            while (closure == null) {
                int count = ioSocket.getInputStream().read(buffer);
                if (count < 0) {
                    framer.endOfInput();
                    closeWith(new TransportFailure(TransportFailure.Kind.EOF, false, null));
                    break;
                }
                ByteBuffer source = ByteBuffer.wrap(buffer, 0, count);
                while (source.hasRemaining() && closure == null) {
                    var frame = framer.read(source);
                    if (frame.isPresent()) {
                        try {
                            listener.frame(frame.orElseThrow());
                        } catch (RuntimeException | Error failure) {
                            throw new TransportFailure(TransportFailure.Kind.OBSERVER_FAILED, false, failure);
                        }
                    }
                }
            }
        } catch (IOException failure) {
            TransportFailure.Kind kind = remote != null && !tcpConnected
                    ? failure instanceof SocketTimeoutException
                            ? TransportFailure.Kind.CONNECT_TIMEOUT
                            : TransportFailure.Kind.CONNECT_FAILED
                    : TransportFailure.Kind.READ_FAILED;
            closeWith(new TransportFailure(kind, false, failure));
        } catch (TransportFailure failure) {
            closeWith(failure);
        } catch (IllegalArgumentException failure) {
            closeWith(new TransportFailure(TransportFailure.Kind.MALFORMED_FRAME, false, failure));
        } catch (RuntimeException | Error failure) {
            closeWith(new TransportFailure(TransportFailure.Kind.OBSERVER_FAILED, false, failure));
        } finally {
            closeWith(new TransportFailure(TransportFailure.Kind.CLOSED, false, null));
            if (ioSocket != socket) {
                try {
                    ioSocket.close();
                } catch (IOException | RuntimeException | Error failure) {
                    recordCleanupFailure(failure);
                }
            }
            try {
                listener.closed(closure);
            } catch (RuntimeException | Error failure) {
                recordCleanupFailure(failure);
            }
        }
    }

    private void awaitWorkers() {
        try {
            awaitCleanup();
            termination.complete(null);
        } catch (TransportFailure failure) {
            termination.completeExceptionally(failure);
        }
    }

    void awaitCleanup() {
        boolean interrupted = false;
        List<Thread> workers;
        lock.lock();
        try {
            workers = started ? List.of(reader, writer, deadlines) : List.of();
        } finally {
            lock.unlock();
        }
        for (Thread worker : workers) {
            while (true) {
                try {
                    worker.join();
                    break;
                } catch (InterruptedException ignored) {
                    interrupted = true;
                }
            }
        }
        Throwable failure;
        lock.lock();
        try {
            while (!closeFinished || notifying != 0 || occupiedCount[0] != 0 || occupiedCount[1] != 0)
                changed.awaitUninterruptibly();
            failure = cleanupFailure;
        } finally {
            lock.unlock();
        }
        if (interrupted) Thread.currentThread().interrupt();
        if (failure != null) throw new TransportFailure(TransportFailure.Kind.CLEANUP_FAILED, false, failure);
    }

    @Override
    public WriteHandle write(byte[] frame, WriteClass writeClass, long deadlineNanos, WriteObserver observer) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(writeClass, "writeClass");
        Objects.requireNonNull(observer, "observer");
        if (frame.length < 16
                || frame.length > config.maximumFrameLength()
                || Integer.toUnsignedLong(ByteBuffer.wrap(frame).getInt()) != frame.length)
            throw new IllegalArgumentException("Invalid complete frame length");
        lock.lock();
        try {
            if (!started || closure != null) throw new TransportFailure(TransportFailure.Kind.CLOSED, false, null);
            if (System.nanoTime() - deadlineNanos >= 0)
                throw new TransportFailure(TransportFailure.Kind.WRITE_TIMEOUT, false, null);
            int index = writeClass.ordinal();
            int maximumCount =
                    writeClass == WriteClass.ORDINARY ? config.ordinaryWriteCount() : config.controlWriteCount();
            long maximumBytes =
                    writeClass == WriteClass.ORDINARY ? config.ordinaryWriteBytes() : config.controlWriteBytes();
            if (occupiedCount[index] >= maximumCount || frame.length > maximumBytes - occupiedBytes[index])
                throw new TransportFailure(TransportFailure.Kind.FULL, false, null);
            PendingWrite pending = new PendingWrite(frame.clone(), writeClass, deadlineNanos, observer);
            queue(writeClass).addLast(pending);
            occupiedCount[index]++;
            occupiedBytes[index] += frame.length;
            changed.signalAll();
            return pending;
        } finally {
            lock.unlock();
        }
    }

    private void writeLoop() {
        try {
            while (true) {
                PendingWrite pending;
                lock.lockInterruptibly();
                try {
                    while (closure == null && (!connected || (ordinary.isEmpty() && control.isEmpty())))
                        changed.await();
                    if (closure != null) return;
                    pending = (control.isEmpty() ? ordinary : control).removeFirst();
                    pending.claimed = true;
                    active = pending;
                } finally {
                    lock.unlock();
                }
                try {
                    if (System.nanoTime() - pending.deadline >= 0) {
                        finish(pending, new TransportFailure(TransportFailure.Kind.WRITE_TIMEOUT, false, null));
                        continue;
                    }
                    if (!pending.observer.beforeWrite()) {
                        finish(pending, new TransportFailure(TransportFailure.Kind.REJECTED, false, null));
                        continue;
                    }
                    if (closure != null) continue;
                    if (System.nanoTime() - pending.deadline >= 0) {
                        closeWith(new TransportFailure(TransportFailure.Kind.WRITE_TIMEOUT, true, null), pending);
                        continue;
                    }
                    ioSocket.getOutputStream().write(pending.frame);
                    finish(pending, null);
                } catch (IOException failure) {
                    closeWith(new TransportFailure(TransportFailure.Kind.WRITE_FAILED, true, failure));
                } catch (RuntimeException | Error failure) {
                    closeWith(new TransportFailure(TransportFailure.Kind.OBSERVER_FAILED, true, failure));
                }
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            closeWith(new TransportFailure(TransportFailure.Kind.CLOSED, false, failure));
        }
    }

    private void deadlineLoop() {
        try {
            while (true) {
                List<PendingWrite> expired = new ArrayList<>();
                PendingWrite expiredActive = null;
                boolean connectExpired = false;
                boolean handshakeExpired = false;
                lock.lockInterruptibly();
                try {
                    if (closure != null) return;
                    long now = System.nanoTime();
                    long remaining = Long.MAX_VALUE;
                    if (remote != null && !tcpConnected) {
                        long delay = connectDeadline - now;
                        if (delay <= 0) connectExpired = true;
                        else remaining = Math.min(remaining, delay);
                    }
                    if (handshaking) {
                        long delay = handshakeDeadline - now;
                        if (delay <= 0) handshakeExpired = true;
                        else remaining = Math.min(remaining, delay);
                    }
                    for (ArrayDeque<PendingWrite> queue : List.of(ordinary, control)) {
                        var entries = queue.iterator();
                        while (entries.hasNext()) {
                            PendingWrite pending = entries.next();
                            long delay = pending.deadline - now;
                            if (delay <= 0) {
                                entries.remove();
                                expired.add(pending);
                            } else remaining = Math.min(remaining, delay);
                        }
                    }
                    if (active != null) {
                        long delay = active.deadline - now;
                        if (delay <= 0) expiredActive = active;
                        else remaining = Math.min(remaining, delay);
                    }
                    if (expired.isEmpty() && expiredActive == null && !connectExpired && !handshakeExpired) {
                        if (remaining == Long.MAX_VALUE) changed.await();
                        else changed.awaitNanos(remaining);
                        continue;
                    }
                } finally {
                    lock.unlock();
                }
                for (PendingWrite pending : expired)
                    finish(pending, new TransportFailure(TransportFailure.Kind.WRITE_TIMEOUT, false, null));
                if (connectExpired) closeWith(new TransportFailure(TransportFailure.Kind.CONNECT_TIMEOUT, false, null));
                if (handshakeExpired)
                    closeWith(new TransportFailure(TransportFailure.Kind.TLS_HANDSHAKE_TIMEOUT, false, null));
                if (expiredActive != null)
                    closeWith(new TransportFailure(TransportFailure.Kind.WRITE_TIMEOUT, true, null), expiredActive);
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            closeWith(new TransportFailure(TransportFailure.Kind.CLOSED, false, failure));
        }
    }

    private void finish(PendingWrite pending, TransportFailure failure) {
        boolean expiredCompletion = false;
        lock.lock();
        try {
            if (pending.done) return;
            if (failure == null && System.nanoTime() - pending.deadline >= 0) {
                failure = new TransportFailure(TransportFailure.Kind.WRITE_TIMEOUT, true, null);
                expiredCompletion = true;
            }
            pending.done = true;
            notifying++;
            occupiedCount[pending.writeClass.ordinal()]--;
            occupiedBytes[pending.writeClass.ordinal()] -= pending.frame.length;
            if (active == pending) active = null;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
        if (expiredCompletion) closeWith(failure);
        try {
            if (failure == null) pending.observer.written();
            else pending.observer.failed(failure);
        } catch (RuntimeException | Error callbackFailure) {
            recordCleanupFailure(callbackFailure);
            closeWith(new TransportFailure(TransportFailure.Kind.OBSERVER_FAILED, pending.claimed, callbackFailure));
        } finally {
            lock.lock();
            try {
                notifying--;
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }

    private ArrayDeque<PendingWrite> queue(WriteClass writeClass) {
        return writeClass == WriteClass.ORDINARY ? ordinary : control;
    }

    private void recordCleanupFailure(Throwable failure) {
        lock.lock();
        try {
            if (cleanupFailure == null) cleanupFailure = failure;
        } finally {
            lock.unlock();
        }
    }

    private final class PendingWrite implements WriteHandle {
        final byte[] frame;
        final WriteClass writeClass;
        final long deadline;
        final WriteObserver observer;
        boolean claimed;
        boolean done;

        PendingWrite(byte[] frame, WriteClass writeClass, long deadline, WriteObserver observer) {
            this.frame = frame;
            this.writeClass = writeClass;
            this.deadline = deadline;
            this.observer = observer;
        }

        @Override
        public boolean cancel() {
            lock.lock();
            try {
                if (done || claimed || !queue(writeClass).remove(this)) return false;
            } finally {
                lock.unlock();
            }
            finish(this, new TransportFailure(TransportFailure.Kind.CANCELLED, false, null));
            return true;
        }
    }

    @Override
    public CompletionStage<Void> termination() {
        return termination.minimalCompletionStage();
    }

    @Override
    public void close() {
        closeWith(new TransportFailure(TransportFailure.Kind.CLOSED, false, null));
    }

    private void closeWith(TransportFailure failure) {
        closeWith(failure, null);
    }

    private void closeWith(TransportFailure failure, PendingWrite expectedActive) {
        List<PendingWrite> failures;
        lock.lock();
        try {
            if (closure != null) return;
            if (expectedActive != null && (active != expectedActive || expectedActive.done)) return;
            closure = failure;
            failures = new ArrayList<>(ordinary);
            failures.addAll(control);
            ordinary.clear();
            control.clear();
            if (active != null) failures.add(active);
            changed.signalAll();
        } finally {
            lock.unlock();
        }
        try {
            try {
                socket.close();
            } catch (IOException | RuntimeException | Error closeFailure) {
                recordCleanupFailure(closeFailure);
            }
            if (reader != null && reader != Thread.currentThread()) reader.interrupt();
            if (writer != null && writer != Thread.currentThread()) writer.interrupt();
            if (deadlines != null && deadlines != Thread.currentThread()) deadlines.interrupt();
            for (PendingWrite pending : failures)
                finish(pending, new TransportFailure(failure.kind(), pending.claimed, failure.getCause()));
        } finally {
            lock.lock();
            try {
                closeFinished = true;
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }
        if (!started) Thread.ofVirtual().name("smpp-tcp-cleanup").start(this::awaitWorkers);
    }
}
