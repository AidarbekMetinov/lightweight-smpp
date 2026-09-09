package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.request.BoundedNotifications;
import org.junit.jupiter.api.Test;

class EndpointCongestionTest {
    @Test
    void onlyAcceptedMatchedSupportedResponsesUpdateTheSnapshotIncludingBindAndNack() throws Exception {
        for (int version : new int[] {0x34, 0x50}) {
            AtomicLong clock = new AtomicLong(System.nanoTime());
            BoundedNotifications callbacks = new BoundedNotifications(32, 1);
            FakeFrameTransport transport = new FakeFrameTransport();
            EndpointConnection connection = EndpointConnection.client(
                    transport,
                    new ClientConfig(
                            new InetSocketAddress("127.0.0.1", 1),
                            new BindRequest(BindMode.TRANSCEIVER, "", "", "", version, 0, 0, ""),
                            false),
                    EndpointOptions.defaults(),
                    callbacks,
                    clock::get);
            try {
                connection.start();
                transport.writes.poll(2, TimeUnit.SECONDS);
                transport.receive(frame(0x80000009L, 0, 1, "0002100001" + Integer.toHexString(version) + "0428000150"));
                BoundSession session = connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS);
                if (version == 0x50)
                    assertEquals(
                            new CongestionObservation(80, 0x80000009L, 1, clock.get()),
                            session.congestion().orElseThrow());
                else assertTrue(session.congestion().isEmpty());
                var first = session.enquireLink();
                transport.writes.poll(2, TimeUnit.SECONDS);
                long sequence = first.identity().sequenceNumber();
                clock.addAndGet(10);
                transport.receive(frame(0x80000015L, 0, sequence + 100, "0428000164"));
                assertEquals(
                        version == 0x50 ? 80 : -1,
                        session.congestion().map(CongestionObservation::level).orElse(-1));
                // Correct sequence but wrong concrete response must not change the sample.
                transport.receive(frame(0x80000006L, 0, sequence, "0428000164"));
                assertFalse(first.isDone());
                transport.receive(frame(0x80000015L, 0, sequence, "0428000100"));
                first.result().toCompletableFuture().get(2, TimeUnit.SECONDS);
                assertEquals(
                        version == 0x50 ? 0 : -1,
                        session.congestion().map(CongestionObservation::level).orElse(-1));
                var next = session.enquireLink();
                transport.writes.poll(2, TimeUnit.SECONDS);
                transport.receive(frame(0x80000015L, 0, next.identity().sequenceNumber(), "04280001ff"));
                next.result().toCompletableFuture().get(2, TimeUnit.SECONDS);
                assertEquals(
                        version == 0x50 ? 0 : -1,
                        session.congestion().map(CongestionObservation::level).orElse(-1));
                transport.receive(frame(0x80000015L, 0, sequence, "0428000164"));
                assertEquals(
                        version == 0x50 ? 0 : -1,
                        session.congestion().map(CongestionObservation::level).orElse(-1));
                var nacked = session.enquireLink();
                transport.writes.poll(2, TimeUnit.SECONDS);
                transport.receive(frame(0x80000000L, 3, nacked.identity().sequenceNumber(), "0428000164"));
                assertTrue(
                        assertThrows(
                                                java.util.concurrent.ExecutionException.class,
                                                () -> nacked.result()
                                                        .toCompletableFuture()
                                                        .get(2, TimeUnit.SECONDS))
                                        .getCause()
                                instanceof kg.aidarbek.smpp.request.PeerNackException);
                assertEquals(
                        version == 0x50 ? 100 : -1,
                        session.congestion().map(CongestionObservation::level).orElse(-1));
            } finally {
                connection.close();
                callbacks.close();
                assertTrue(callbacks.awaitTermination(Duration.ofSeconds(2)));
            }
        }
    }

    @Test
    void cancelledExpiredAndContextInvalidResponsesCannotOverwriteAcceptedCongestion() throws Exception {
        AtomicLong clock = new AtomicLong(System.nanoTime());
        BoundedNotifications callbacks = new BoundedNotifications(32, 1);
        FakeFrameTransport transport = new FakeFrameTransport();
        EndpointConnection connection = EndpointConnection.client(
                transport,
                new ClientConfig(
                        new InetSocketAddress("127.0.0.1", 1),
                        new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x50, 0, 0, ""),
                        false),
                EndpointOptions.defaults(),
                callbacks,
                clock::get);
        try {
            connection.start();
            transport.writes.poll(2, TimeUnit.SECONDS);
            transport.receive(frame(0x80000009L, 0, 1, "0002100001500428000150"));
            BoundSession session = connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS);
            var cancelled = session.enquireLink();
            transport.writes.poll(2, TimeUnit.SECONDS);
            assertTrue(cancelled.cancel());
            transport.receive(frame(0x80000015L, 0, cancelled.identity().sequenceNumber(), "0428000164"));
            assertEquals(80, session.congestion().orElseThrow().level());
            var expired = session.enquireLink(kg.aidarbek.smpp.request.RequestOptions.timeout(Duration.ofMillis(100)));
            transport.writes.poll(2, TimeUnit.SECONDS);
            clock.addAndGet(TimeUnit.SECONDS.toNanos(1));
            transport.receive(frame(0x80000015L, 0, expired.identity().sequenceNumber(), "0428000164"));
            assertTrue(expired.isDone());
            assertEquals(80, session.congestion().orElseThrow().level());
            var query = session.queryBroadcast()
                    .orElseThrow()
                    .send(new kg.aidarbek.smpp.protocol.QueryBroadcastSm(
                            "id", BroadcastEndpointTest.SOURCE, BroadcastEndpointTest.EMPTY));
            transport.writes.poll(2, TimeUnit.SECONDS);
            transport.receive(frame(
                    0x80000112L,
                    0,
                    query.identity().sequenceNumber(),
                    "77726f6e6700042700010106060002004106080001640428000163"));
            assertTrue(query.isDone());
            assertEquals(kg.aidarbek.smpp.session.SessionState.CLOSED, session.state());
            assertEquals(80, session.congestion().orElseThrow().level());
        } finally {
            connection.close();
            callbacks.close();
            assertTrue(callbacks.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    static byte[] frame(long command, long status, long sequence, String bodyHex) {
        byte[] body = HexFormat.of().parseHex(bodyHex);
        return ByteBuffer.allocate(16 + body.length)
                .putInt(16 + body.length)
                .putInt((int) command)
                .putInt((int) status)
                .putInt((int) sequence)
                .put(body)
                .array();
    }
}
