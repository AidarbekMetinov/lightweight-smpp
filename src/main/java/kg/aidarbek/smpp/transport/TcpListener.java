package kg.aidarbek.smpp.transport;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import kg.aidarbek.smpp.spi.TransportFailure;

/** Single virtual-thread TCP acceptor with no accepted-connection queue. */
public final class TcpListener implements AutoCloseable {
    private final ServerSocket socket;
    private final CompletableFuture<Void> termination = new CompletableFuture<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final TcpTransportConfig config;
    private final TlsConfig tls;
    private final AcceptListener listener;
    private final Thread acceptor;
    private volatile boolean closed;
    private volatile TransportFailure failure;
    private TcpTransport handoff;
    private TcpTransport retiring;
    private boolean closeFinished;
    private Throwable cleanupFailure;

    private TcpListener(ServerSocket socket, TcpTransportConfig config, TlsConfig tls, AcceptListener listener) {
        this.tls = tls;
        this.socket = socket;
        this.config = config;
        this.listener = listener;
        acceptor = Thread.ofVirtual().name("smpp-tcp-accept").unstarted(this::acceptLoop);
    }

    /**
     * Binds a resolved local address and starts bounded, callback-driven connection admission.
     * Accepted connections have no library queue; the callback must reserve endpoint capacity.
     * @param local resolved bind address, with port zero to request an OS-assigned port
     * @param backlog positive operating-system pending-connection hint
     * @param config bounds applied to every adopted connection
     * @param listener internal, nonblocking ownership handoff callback
     * @return owned, started listener
     * @throws IllegalArgumentException if the address is unresolved or backlog is nonpositive
     * @throws IOException if binding fails
     */
    public static TcpListener bind(
            InetSocketAddress local, int backlog, TcpTransportConfig config, AcceptListener listener)
            throws IOException {
        return bind(local, backlog, config, null, listener);
    }

    /** Binds an optionally secured listener; handshakes start only after accepted ownership.
     * @param local resolved local address
     * @param backlog positive OS backlog hint
     * @param config frame admission bounds
     * @param tls TLS server policy, or null for plain TCP
     * @param listener internal nonblocking ownership callback
     * @return started listener
     * @throws IOException listener binding failed */
    public static TcpListener bind(
            InetSocketAddress local, int backlog, TcpTransportConfig config, TlsConfig tls, AcceptListener listener)
            throws IOException {
        Objects.requireNonNull(local, "local");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(listener, "listener");
        if (local.isUnresolved() || backlog < 1)
            throw new IllegalArgumentException("Listener requires a resolved address and positive backlog");
        if (tls != null && tls.clientMode()) throw new IllegalArgumentException("Listening requires TLS server policy");
        ServerSocket socket = new ServerSocket();
        try {
            socket.bind(local, backlog);
            TcpListener result = new TcpListener(socket, config, tls, listener);
            result.acceptor.start();
            Thread.ofVirtual().name("smpp-tcp-listener-cleanup").start(result::awaitAcceptor);
            return result;
        } catch (IOException | RuntimeException | Error failure) {
            socket.close();
            throw failure;
        }
    }

    private void acceptLoop() {
        try {
            while (!closed) {
                Socket accepted = socket.accept();
                TcpTransport transport;
                try {
                    transport = TcpTransport.adopt(accepted, config, tls);
                } catch (IOException | RuntimeException | Error rejected) {
                    accepted.close();
                    continue;
                }
                lock.lock();
                try {
                    handoff = transport;
                } finally {
                    lock.unlock();
                }
                boolean transferred = false;
                try {
                    if (!closed) transferred = listener.accept(transport, accepted.getRemoteSocketAddress());
                } catch (RuntimeException | Error rejected) {
                    transferred = false;
                }
                lock.lock();
                try {
                    transferred &= !closed;
                    handoff = null;
                } finally {
                    lock.unlock();
                }
                if (!transferred) {
                    transport.close();
                    transport.awaitCleanup();
                }
            }
        } catch (TransportFailure failure) {
            recordCleanupFailure(failure);
        } catch (IOException | RuntimeException | Error failure) {
            if (!closed) this.failure = new TransportFailure(TransportFailure.Kind.LISTEN_FAILED, false, failure);
        } finally {
            close();
        }
    }

    private void awaitAcceptor() {
        boolean interrupted = false;
        while (true) {
            try {
                acceptor.join();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        TcpTransport pending;
        lock.lock();
        try {
            while (!closeFinished) changed.awaitUninterruptibly();
            pending = retiring;
        } finally {
            lock.unlock();
        }
        if (pending != null) {
            try {
                pending.awaitCleanup();
            } catch (TransportFailure failure) {
                recordCleanupFailure(failure);
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
        if (cleanupFailure != null)
            termination.completeExceptionally(
                    new TransportFailure(TransportFailure.Kind.CLEANUP_FAILED, false, cleanupFailure));
        else if (failure == null) termination.complete(null);
        else termination.completeExceptionally(failure);
    }

    /**
     * Returns the actual bound address, including an OS-assigned port when zero was requested.
     * @return resolved local socket address
     */
    public InetSocketAddress localAddress() {
        return (InetSocketAddress) socket.getLocalSocketAddress();
    }

    /**
     * Completes after physical listener close, accept-loop termination and rejected-handoff cleanup.
     * Unexpected accept failure reports LISTEN_FAILED; failed physical or owned-child cleanup reports
     * CLEANUP_FAILED with the first retained cause. Already transferred connections are independent.
     * External dependent actions cannot postpone this listener's owned socket or worker cleanup.
     * @return read-only cleanup result, exceptionally completed when cleanup or acceptance failed
     */
    public CompletionStage<Void> termination() {
        return termination.minimalCompletionStage();
    }

    private void recordCleanupFailure(Throwable failure) {
        lock.lock();
        try {
            if (cleanupFailure == null) cleanupFailure = failure;
        } finally {
            lock.unlock();
        }
    }

    /** Stops accepting without closing connections whose ownership was already transferred. */
    @Override
    public void close() {
        TcpTransport pending;
        lock.lock();
        try {
            if (closed) return;
            closed = true;
            pending = handoff;
            retiring = pending;
        } finally {
            lock.unlock();
        }
        try {
            try {
                socket.close();
            } catch (IOException | RuntimeException | Error closeFailure) {
                recordCleanupFailure(closeFailure);
            }
            if (pending != null) pending.close();
            if (acceptor != Thread.currentThread()) acceptor.interrupt();
        } finally {
            lock.lock();
            try {
                closeFinished = true;
                changed.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }
}
