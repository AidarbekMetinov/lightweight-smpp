package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.QuerySm;
import kg.aidarbek.smpp.request.BoundedNotifications;
import kg.aidarbek.smpp.request.PeerNackException;
import kg.aidarbek.smpp.request.RequestFailure;
import kg.aidarbek.smpp.request.TransmissionCertainty;
import kg.aidarbek.smpp.session.SessionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KeepaliveTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedControlReplyPreservesTheWinningLocalTransportCause(boolean afterGuard) throws Exception {
        try (Connection connection = new Connection(null)) {
            var failure = new kg.aidarbek.smpp.spi.TransportFailure(
                    kg.aidarbek.smpp.spi.TransportFailure.Kind.WRITE_FAILED,
                    afterGuard,
                    new java.io.IOException("fixture control reply failure"));
            connection.transport.writeFailure = failure;
            connection.transport.failAfterGuard = afterGuard;
            connection.transport.receive(RawPeer.header(0x15, 0, 7));
            assertEquals(SessionState.CLOSED, connection.session.state());
            assertSame(failure, connection.session.closeReason().orElseThrow());
            assertTrue(connection.transport.writes.isEmpty());
        }
    }

    @Test
    void saturatedControlCapacityFailsTheSingleUnsentHeartbeatWithoutAHiddenQueue() throws Exception {
        try (Connection connection =
                new Connection(new KeepalivePolicy(Duration.ofSeconds(1), Duration.ofSeconds(2)))) {
            connection.transport.deferWrites = true;
            for (int index = 0; index < 8; index++) connection.session.enquireLink();
            connection.advance(Duration.ofSeconds(1));
            assertEquals(8, connection.transport.deferred.size());
            assertEquals(8, connection.session.resources().pendingRequests());
            connection.advance(Duration.ZERO);
            assertEquals(SessionState.CLOSED, connection.session.state());
            RequestFailure failure =
                    (RequestFailure) connection.session.closeReason().orElseThrow();
            assertEquals(RequestFailure.Reason.WRITE_FAILED, failure.reason());
            assertEquals(TransmissionCertainty.NOT_SENT, failure.transmission());
            assertEquals(
                    kg.aidarbek.smpp.spi.TransportFailure.Kind.FULL,
                    ((kg.aidarbek.smpp.spi.TransportFailure) failure.getCause()).kind());
            assertTrue(connection.transport.deferred.isEmpty());
        }
    }

    @Test
    void manualEnquiryUsesControlCapacityWhileAutomaticMonitoringRemainsDisabled() throws Exception {
        try (Connection connection = new Connection(null)) {
            connection.advance(Duration.ofSeconds(3));
            assertNull(connection.transport.writes.poll());
            connection.transport.deferWrites = true;
            for (int index = 0; index < 8; index++)
                connection
                        .session
                        .query()
                        .orElseThrow()
                        .send(new QuerySm(
                                "id", CommonOperationsEndpointTest.SOURCE, CommonOperationsEndpointTest.EMPTY));
            connection.session.enquireLink();
            assertEquals(9, connection.transport.deferred.size());
            while (!connection.transport.deferred.isEmpty())
                connection.transport.deferred.remove().send();
            for (int index = 0; index < 8; index++)
                assertEquals(3, RawPeer.command(connection.transport.writes.remove()));
            assertEquals(0x15, RawPeer.command(connection.transport.writes.remove()));
        }
    }

    @Test
    void idleDeadlineSendsOneEnquiryAndMatchingResponseRestartsTheInterval() throws Exception {
        try (Connection connection =
                new Connection(new KeepalivePolicy(Duration.ofSeconds(1), Duration.ofSeconds(2)))) {
            connection.advance(Duration.ofMillis(999));
            assertNull(connection.transport.writes.poll());
            connection.advance(Duration.ofMillis(1));
            byte[] enquiry = connection.transport.writes.poll();
            assertNotNull(enquiry, "The idle deadline must create an enquiry in the existing request window");
            assertEquals(0x15, RawPeer.command(enquiry));
            connection.advance(Duration.ofMillis(1500));
            assertNull(connection.transport.writes.poll(), "Only one automatic enquiry can be outstanding");
            connection.transport.receive(RawPeer.header(0x80000015L, 0, RawPeer.sequence(enquiry)));
            connection.advance(Duration.ofMillis(999));
            assertNull(connection.transport.writes.poll());
            connection.advance(Duration.ofMillis(1));
            assertEquals(0x15, RawPeer.command(connection.transport.writes.remove()));
            assertEquals(SessionState.BOUND_TRX, connection.coordinator.state());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"timeout", "negative", "nack"})
    void failedAutomaticEnquiryClosesWithItsOriginalOutcome(String outcome) throws Exception {
        try (Connection connection =
                new Connection(new KeepalivePolicy(Duration.ofSeconds(1), Duration.ofSeconds(2)))) {
            connection.advance(Duration.ofSeconds(1));
            byte[] enquiry = connection.transport.writes.remove();
            long sequence = RawPeer.sequence(enquiry);
            if (outcome.equals("timeout")) connection.advance(Duration.ofSeconds(2));
            else {
                connection.transport.receive(
                        RawPeer.header(outcome.equals("nack") ? 0x80000000L : 0x80000015L, 8, sequence));
                connection.advance(Duration.ZERO);
            }
            assertEquals(SessionState.CLOSED, connection.session.state());
            RuntimeException failure = connection.session.closeReason().orElseThrow();
            switch (outcome) {
                case "timeout" -> {
                    RequestFailure requestFailure = (RequestFailure) failure;
                    assertEquals(RequestFailure.Reason.DEADLINE_EXPIRED, requestFailure.reason());
                    assertEquals(TransmissionCertainty.MAY_HAVE_BEEN_SENT, requestFailure.transmission());
                    assertEquals(sequence, requestFailure.sequenceNumber());
                }
                case "negative" ->
                    assertEquals(EndpointException.Reason.KEEPALIVE_REJECTED, ((EndpointException) failure).reason());
                case "nack" ->
                    assertEquals(sequence, ((PeerNackException) failure).nack().sequenceNumber());
                default -> throw new IllegalStateException("Unknown test case");
            }
        }
    }

    @Test
    void fullWindowDefersWithinTheOriginalIdleBudgetAndIncomingProgressResetsIt() throws Exception {
        try (Connection connection =
                new Connection(new KeepalivePolicy(Duration.ofSeconds(1), Duration.ofSeconds(2)), 1)) {
            var manual = connection.session.enquireLink();
            byte[] request = connection.transport.writes.remove();
            assertDoesNotThrow(() -> connection.advance(Duration.ofSeconds(1)));
            assertEquals(SessionState.BOUND_TRX, connection.session.state());
            assertNull(connection.transport.writes.poll());
            connection.transport.receive(RawPeer.header(0x80000015L, 0, RawPeer.sequence(request)));
            assertEquals(
                    0,
                    manual.result()
                            .toCompletableFuture()
                            .get(2, TimeUnit.SECONDS)
                            .commandStatus());
            connection.advance(Duration.ofMillis(999));
            assertNull(connection.transport.writes.poll());
            connection.advance(Duration.ofMillis(1));
            assertEquals(0x15, RawPeer.command(connection.transport.writes.remove()));
        }
    }

    @Test
    void windowSaturationCannotRestartAnExpiredAutomaticDeadline() throws Exception {
        try (Connection connection =
                new Connection(new KeepalivePolicy(Duration.ofSeconds(1), Duration.ofSeconds(2)), 1)) {
            connection.session.enquireLink();
            connection.transport.writes.remove();
            assertDoesNotThrow(() -> connection.advance(Duration.ofSeconds(1)));
            assertDoesNotThrow(() -> connection.advance(Duration.ofSeconds(2)));
            assertEquals(SessionState.CLOSED, connection.session.state());
            RequestFailure failure =
                    (RequestFailure) connection.session.closeReason().orElseThrow();
            assertEquals(RequestFailure.Reason.DEADLINE_EXPIRED, failure.reason());
            assertEquals(TransmissionCertainty.NOT_SENT, failure.transmission());
            assertNull(connection.transport.writes.poll());
        }
    }

    @Test
    void automaticEnquiryUsesReservedControlCapacityUnderOrdinaryWriteSaturation() throws Exception {
        try (Connection connection =
                new Connection(new KeepalivePolicy(Duration.ofSeconds(1), Duration.ofSeconds(2)))) {
            connection.transport.deferWrites = true;
            for (int index = 0; index < 8; index++)
                connection
                        .session
                        .query()
                        .orElseThrow()
                        .send(new QuerySm(
                                "id", CommonOperationsEndpointTest.SOURCE, CommonOperationsEndpointTest.EMPTY));
            connection.advance(Duration.ofSeconds(1));
            assertEquals(
                    9,
                    connection.transport.deferred.size(),
                    "One control enquiry must fit alongside eight ordinary writes");
            while (!connection.transport.deferred.isEmpty())
                connection.transport.deferred.remove().send();
            for (int index = 0; index < 8; index++)
                assertEquals(3, RawPeer.command(connection.transport.writes.remove()));
            assertEquals(0x15, RawPeer.command(connection.transport.writes.remove()));
        }
    }

    static final class Connection implements AutoCloseable {
        final AtomicLong clock = new AtomicLong(System.nanoTime());
        final BoundedNotifications notifications = new BoundedNotifications(32, 1);
        final FakeFrameTransport transport = new FakeFrameTransport();
        final EndpointConnection coordinator;
        final BoundSession session;

        Connection(KeepalivePolicy policy) throws Exception {
            this(policy, 32);
        }

        Connection(KeepalivePolicy policy, int window) throws Exception {
            EndpointOptions defaults = EndpointOptions.defaults();
            EndpointOptions options = new EndpointOptions(
                    2,
                    window,
                    65536,
                    1,
                    32,
                    defaults.connectTimeout(),
                    defaults.bindTimeout(),
                    defaults.requestTimeout(),
                    defaults.shutdownTimeout(),
                    defaults.pduLimits());
            coordinator = EndpointConnection.client(
                    transport,
                    TlsEndpointsTest.config(
                            new InetSocketAddress("127.0.0.1", 1), BindMode.TRANSCEIVER, SmppVersion.V3_4),
                    options,
                    notifications,
                    clock::get);
            try {
                coordinator.configureKeepalive(policy);
                coordinator.start();
                byte[] bind = transport.writes.remove();
                transport.receive(RawPeer.bindResponse(0x80000009L, 0, RawPeer.sequence(bind), 0x34));
                session = coordinator.bound().toCompletableFuture().get(2, TimeUnit.SECONDS);
            } catch (Exception | Error failure) {
                close();
                throw failure;
            }
        }

        void advance(Duration elapsed) {
            clock.addAndGet(elapsed.toNanos());
            coordinator.tick(clock.get());
        }

        @Override
        public void close() {
            coordinator.close();
            notifications.close();
            try {
                assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(failure);
            }
        }
    }
}
