package kg.aidarbek.smpp.endpoint;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.DataSm;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.ShortMessage;
import kg.aidarbek.smpp.protocol.SubmitSm;
import kg.aidarbek.smpp.protocol.Tlv;
import kg.aidarbek.smpp.request.BoundedNotifications;
import kg.aidarbek.smpp.request.RequestHandle;

/** Finite manually invoked allocation diagnostic over real coordinator logic and a controlled frame port. */
public final class EndpointSendAllocationProbe {
    private EndpointSendAllocationProbe() {}

    /** Measures only calling-thread allocation; arguments are version, submit/data, warmup and measured cycles. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 4) throw new IllegalArgumentException("version operation warmup measured");
        SmppVersion version =
                switch (arguments[0]) {
                    case "3.4" -> SmppVersion.V3_4;
                    case "5.0" -> SmppVersion.V5_0;
                    default -> throw new IllegalArgumentException("Expected 3.4 or 5.0");
                };
        boolean data =
                switch (arguments[1]) {
                    case "submit" -> false;
                    case "data" -> true;
                    default -> throw new IllegalArgumentException("Expected submit or data");
                };
        int warmup = boundedCount(arguments[2]);
        int measured = boundedCount(arguments[3]);
        ThreadMXBean allocation = ManagementFactory.getPlatformMXBean(ThreadMXBean.class);
        if (allocation == null || !allocation.isThreadAllocatedMemorySupported())
            throw new IllegalStateException("Calling-thread allocation counters unavailable");
        allocation.setThreadAllocatedMemoryEnabled(true);
        long bytes;
        long elapsed;
        try (Connection connection = new Connection(version, data)) {
            for (int index = 0; index < warmup; index++) connection.exchange();
            long caller = Thread.currentThread().threadId();
            long before = allocation.getThreadAllocatedBytes(caller);
            long started = System.nanoTime();
            for (int index = 0; index < measured; index++) connection.exchange();
            elapsed = System.nanoTime() - started;
            bytes = allocation.getThreadAllocatedBytes(caller) - before;
            if (bytes < 0 || connection.session.resources().pendingRequests() != 0)
                throw new IllegalStateException("Invalid allocation counter or unsettled request");
        }
        System.out.println("{\"version\":\"" + arguments[0] + "\",\"operation\":\"" + arguments[1]
                + "\",\"warmup\":" + warmup + ",\"measured\":" + measured
                + ",\"callingThreadAllocatedBytes\":" + bytes + ",\"elapsedNanos\":" + elapsed
                + ",\"payloadBytes\":160,\"pending\":0,\"cleanupComplete\":true}");
    }

    private static int boundedCount(String value) {
        int count = Integer.parseInt(value);
        if (count < 1 || count > 100000) throw new IllegalArgumentException("Cycle count must be 1..100000");
        return count;
    }

    /** Owns one bound coordinator and fixed immutable request, settling each response before the next cycle. */
    private static final class Connection implements AutoCloseable {
        private final BoundedNotifications notifications = new BoundedNotifications(32, 1);
        private final FakeFrameTransport transport = new FakeFrameTransport();
        private final EndpointConnection coordinator;
        private final BoundSession session;
        private final SubmitSm submission;
        private final DataSm dataMessage;

        Connection(SmppVersion version, boolean data) throws Exception {
            byte[] payload = new byte[160];
            Address source = new Address(0, 0, "source");
            Address destination = new Address(0, 0, "destination");
            submission = data
                    ? null
                    : new SubmitSm(new ShortMessage(
                            "",
                            source,
                            destination,
                            0,
                            0,
                            0,
                            "",
                            "",
                            0,
                            0,
                            4,
                            0,
                            new OctetString(payload),
                            new OptionalParameters(List.of())));
            dataMessage = data
                    ? new DataSm(
                            "", source, destination, 0, 0, 4, new OptionalParameters(List.of(new Tlv(0x0424, payload))))
                    : null;
            coordinator = EndpointConnection.client(
                    transport,
                    new ClientConfig(
                            new InetSocketAddress("127.0.0.1", 1),
                            new BindRequest(BindMode.TRANSCEIVER, "", "", "", version.interfaceVersion(), 0, 0, ""),
                            true),
                    EndpointOptions.defaults(),
                    notifications);
            try {
                coordinator.start();
                transport.writes.remove();
                transport.receive(RawPeer.bindResponse(0x80000009L, 0, 1, version.interfaceVersion()));
                session = coordinator.bound().toCompletableFuture().get(2, TimeUnit.SECONDS);
            } catch (Exception | Error failure) {
                close();
                throw failure;
            }
        }

        void exchange() throws Exception {
            RequestHandle<?> handle = submission == null
                    ? session.dataMessages().orElseThrow().send(dataMessage)
                    : session.submission().orElseThrow().send(submission);
            byte[] frame = transport.writes.remove();
            long expectedCommand = submission == null ? 0x103 : 4;
            if (RawPeer.command(frame) != expectedCommand
                    || RawPeer.sequence(frame) != handle.identity().sequenceNumber())
                throw new IllegalStateException("Unexpected outgoing frame identity");
            byte[] response = ByteBuffer.allocate(19)
                    .putInt(19)
                    .putInt((int) (expectedCommand | 0x80000000L))
                    .putInt(0)
                    .putInt((int) handle.identity().sequenceNumber())
                    .put((byte) 'i')
                    .put((byte) 'd')
                    .put((byte) 0)
                    .array();
            transport.receive(response);
            if (handle.result().toCompletableFuture().get(2, TimeUnit.SECONDS).commandStatus() != 0)
                throw new IllegalStateException("Unexpected peer rejection");
        }

        @Override
        public void close() {
            coordinator.close();
            notifications.close();
            try {
                transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
                if (!notifications.awaitTermination(Duration.ofSeconds(2)))
                    throw new IllegalStateException("Notification ownership did not retire");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted during probe cleanup", interrupted);
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) {
                throw new IllegalStateException("Probe transport cleanup failed", failure);
            }
        }
    }
}
