package kg.aidarbek.smpp.transport;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kg.aidarbek.smpp.spi.FrameListener;
import kg.aidarbek.smpp.spi.FrameTransport;
import kg.aidarbek.smpp.spi.TransportFailure;
import kg.aidarbek.smpp.spi.WriteClass;
import kg.aidarbek.smpp.spi.WriteObserver;

/** Explicitly launched, development-only raw-frame echo experiment; no default load task. */
public final class TransportExperiment {
    private TransportExperiment() {}

    /** Runs server or client in its own JVM; see docs/TRANSPORT.md for the exact invocation. */
    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("server")) {
            try (ServerRun server = new ServerRun(Integer.parseInt(args[1]), Integer.parseInt(args[2]))) {
                System.out.println("PORT=" + server.port());
                System.out.println("SERVER_ECHO_WRITES=" + server.awaitFrames());
            }
        } else if (args.length == 4 && args[0].equals("client")) {
            Result result = runClient(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]));
            System.out.println("CLIENT_ECHO_FRAMES=" + result.frames());
            System.out.println("CLIENT_WRITES=" + result.writes());
            System.out.println("ELAPSED_NANOS=" + result.elapsedNanos());
        } else {
            throw new IllegalArgumentException("Expected server CONNECTIONS ROUNDS or client PORT CONNECTIONS ROUNDS");
        }
        Runtime runtime = Runtime.getRuntime();
        System.out.println("HEAP_USED_AFTER_CLEANUP=" + (runtime.totalMemory() - runtime.freeMemory()));
        System.out.println("AVAILABLE_PROCESSORS=" + runtime.availableProcessors());
    }

    static Result runClient(int port, int connections, int rounds) throws Exception {
        validate(connections, rounds);
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid experiment port");
        List<ClientPeer> peers = new ArrayList<>();
        try {
            InetSocketAddress remote = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
            for (int i = 0; i < connections; i++) {
                ClientPeer peer =
                        new ClientPeer(TcpTransport.connect(remote, TcpTransportConfig.defaults(), deadline()), rounds);
                peers.add(peer);
                peer.transport.start(peer);
            }
            for (ClientPeer peer : peers) peer.connected.get(5, TimeUnit.SECONDS);
            long start = System.nanoTime();
            int frames = 0;
            for (int round = 1; round <= rounds; round++) {
                byte[] frame = ByteBuffer.allocate(16)
                        .putInt(16)
                        .putInt(15)
                        .putInt(0)
                        .putInt(round)
                        .array();
                for (ClientPeer peer : peers) peer.transport.write(frame, WriteClass.ORDINARY, deadline(), peer);
                for (ClientPeer peer : peers) {
                    byte[] response = peer.frames.poll(5, TimeUnit.SECONDS);
                    if (!Arrays.equals(frame, response))
                        throw new IllegalStateException("Missing or incorrect echoed frame");
                    frames++;
                }
            }
            int writes = 0;
            for (ClientPeer peer : peers) writes += peer.writesDone.get(5, TimeUnit.SECONDS);
            return new Result(frames, writes, System.nanoTime() - start);
        } finally {
            for (ClientPeer peer : peers) peer.transport.close();
            for (ClientPeer peer : peers)
                peer.transport.termination().toCompletableFuture().get(5, TimeUnit.SECONDS);
        }
    }

    private static long deadline() {
        return System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    }

    private static void validate(int connections, int rounds) {
        if (connections < 1 || connections > 64 || rounds < 1 || rounds > 10000)
            throw new IllegalArgumentException("Experiment bounds: 1..64 connections and 1..10000 rounds");
    }

    record Result(int frames, int writes, long elapsedNanos) {}

    static final class ServerRun implements AutoCloseable {
        private final TcpListener listener;
        private final int rounds;
        private final int expected;
        private final AtomicInteger accepted = new AtomicInteger();
        private final AtomicInteger written = new AtomicInteger();
        private final List<FrameTransport> transports = new CopyOnWriteArrayList<>();
        private final CompletableFuture<Integer> done = new CompletableFuture<>();

        ServerRun(int connections, int rounds) throws IOException {
            validate(connections, rounds);
            this.rounds = rounds;
            expected = connections * rounds;
            listener = TcpListener.bind(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), 0),
                    connections,
                    TcpTransportConfig.defaults(),
                    (transport, peer) -> {
                        if (accepted.incrementAndGet() > connections) return false;
                        transports.add(transport);
                        transport.start(new ServerPeer(this, transport));
                        return true;
                    });
        }

        int port() {
            return listener.localAddress().getPort();
        }

        int awaitFrames() throws Exception {
            return done.get(30, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            listener.close();
            listener.termination()
                    .toCompletableFuture()
                    .orTimeout(5, TimeUnit.SECONDS)
                    .join();
            for (FrameTransport transport : transports) transport.close();
            for (FrameTransport transport : transports)
                transport
                        .termination()
                        .toCompletableFuture()
                        .orTimeout(5, TimeUnit.SECONDS)
                        .join();
        }
    }

    private static final class ServerPeer implements FrameListener, WriteObserver {
        private final ServerRun server;
        private final FrameTransport transport;
        private int received;

        ServerPeer(ServerRun server, FrameTransport transport) {
            this.server = server;
            this.transport = transport;
        }

        @Override
        public void connected() {}

        @Override
        public void frame(byte[] frame) {
            if (++received > server.rounds) throw new IllegalStateException("Extra experiment frame");
            transport.write(frame, WriteClass.ORDINARY, deadline(), this);
        }

        @Override
        public void closed(TransportFailure failure) {
            if (received != server.rounds) server.done.completeExceptionally(failure);
        }

        @Override
        public boolean beforeWrite() {
            return true;
        }

        @Override
        public void written() {
            int completed = server.written.incrementAndGet();
            if (completed == server.expected) server.done.complete(completed);
        }

        @Override
        public void failed(TransportFailure failure) {
            server.done.completeExceptionally(failure);
        }
    }

    private static final class ClientPeer implements FrameListener, WriteObserver {
        private final TcpTransport transport;
        private final int rounds;
        private final CompletableFuture<Void> connected = new CompletableFuture<>();
        private final ArrayBlockingQueue<byte[]> frames = new ArrayBlockingQueue<>(1);
        private final CompletableFuture<Integer> writesDone = new CompletableFuture<>();
        private final AtomicInteger written = new AtomicInteger();

        ClientPeer(TcpTransport transport, int rounds) {
            this.transport = transport;
            this.rounds = rounds;
        }

        @Override
        public void connected() {
            connected.complete(null);
        }

        @Override
        public void frame(byte[] frame) {
            if (!frames.offer(frame)) throw new IllegalStateException("Experiment response capacity exceeded");
        }

        @Override
        public void closed(TransportFailure failure) {
            connected.completeExceptionally(failure);
            writesDone.completeExceptionally(failure);
        }

        @Override
        public boolean beforeWrite() {
            return true;
        }

        @Override
        public void written() {
            int completed = written.incrementAndGet();
            if (completed == rounds) writesDone.complete(completed);
        }

        @Override
        public void failed(TransportFailure failure) {
            writesDone.completeExceptionally(failure);
        }
    }
}
