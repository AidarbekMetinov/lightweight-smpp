package kg.aidarbek.smpp.endpoint;

import static kg.aidarbek.smpp.endpoint.CommonOperationsEndpointTest.DESTINATION;
import static kg.aidarbek.smpp.endpoint.CommonOperationsEndpointTest.EMPTY;
import static kg.aidarbek.smpp.endpoint.CommonOperationsEndpointTest.SOURCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.AlertNotification;
import kg.aidarbek.smpp.request.BoundedNotifications;
import kg.aidarbek.smpp.request.RequestOptions;
import kg.aidarbek.smpp.spi.FrameTransportContract;
import kg.aidarbek.smpp.spi.TransportFailure;
import org.junit.jupiter.api.Test;

class AlertWriteContractTest {
    @Test
    void controlledPortSettlesAThrowingGuardAndClosesAllAcceptedWork() throws Exception {
        try (Fixture fixture = new Fixture()) {
            var throwing = new GuardObserver(false, true);
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> fixture.transport.write(
                    RawPeer.header(0x15, 0, 73),
                    kg.aidarbek.smpp.spi.WriteClass.ORDINARY,
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(2),
                    throwing));
            fixture.transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(1, throwing.failures);
            assertEquals(0, throwing.writes);
        }
    }

    @Test
    void controlledPortReportsObserverCleanupFailureAndStillSettlesOtherAcceptedWrites() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.transport.deferred = true;
            var throwing = new GuardObserver(true);
            var other = new GuardObserver();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            fixture.transport.write(
                    RawPeer.header(0x15, 0, 71), kg.aidarbek.smpp.spi.WriteClass.ORDINARY, deadline, throwing);
            fixture.transport.write(
                    RawPeer.header(0x15, 0, 72), kg.aidarbek.smpp.spi.WriteClass.ORDINARY, deadline, other);
            org.junit.jupiter.api.Assertions.assertDoesNotThrow(fixture.transport::close);
            ExecutionException failed = assertThrows(
                    ExecutionException.class,
                    () -> fixture.transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertEquals(TransportFailure.Kind.CLEANUP_FAILED, ((TransportFailure) failed.getCause()).kind());
            assertEquals(1, throwing.failures);
            assertEquals(1, other.failures);
        }
    }

    @Test
    void gracefulDrainWaitsForAcceptedNotificationAndRejectsRetainedSenderAdmission() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.transport.deferred = true;
            var sender = fixture.session.alerts().orElseThrow();
            NotificationSend alert = sender.send(alert());
            fixture.connection.beginShutdown(System.nanoTime() + TimeUnit.SECONDS.toNanos(2));
            assertTrue(fixture.session.alerts().isEmpty());
            assertThrows(IllegalStateException.class, () -> sender.send(alert()));
            assertTrue(fixture.transport.written.isEmpty());
            assertTrue(fixture.transport.claimNext());
            fixture.transport.completeClaimed();
            assertEquals(0x102, RawPeer.command(fixture.transport.written.remove()));
            alert.result().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertTrue(fixture.transport.claimNext());
            fixture.transport.completeClaimed();
            byte[] unbind = fixture.transport.written.remove();
            assertEquals(6, RawPeer.command(unbind));
            assertTrue(RawPeer.sequence(unbind) > alert.sequenceNumber());
            fixture.transport.receive(RawPeer.header(0x80000006L, 0, RawPeer.sequence(unbind)));
            fixture.connection.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void controlledPortCannotReportQueuedCancellationAfterAConcurrentGuardWins() throws Exception {
        CommonTestTransport transport = new CommonTestTransport();
        var ready = new java.util.concurrent.CountDownLatch(1);
        var release = new CompletableFuture<Void>();
        transport.cancellationReady = () -> {
            ready.countDown();
            release.join();
        };
        var callbacks = new kg.aidarbek.smpp.request.BoundedNotifications(8, 1);
        var connection = EndpointConnection.client(
                transport,
                new ClientConfig(
                        new InetSocketAddress("127.0.0.1", 1),
                        new kg.aidarbek.smpp.protocol.BindRequest(
                                kg.aidarbek.smpp.protocol.BindMode.RECEIVER, "", "", "", 0x34, 0, 0, ""),
                        false),
                EndpointOptions.defaults(),
                callbacks);
        try {
            connection.start();
            transport.written.remove();
            transport.receive(RawPeer.bindResponse(0x80000001L, 0, 1, 0x34));
            connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS);
            transport.deferred = true;
            var observer = new GuardObserver();
            var write = transport.write(
                    RawPeer.header(0x15, 0, 2),
                    kg.aidarbek.smpp.spi.WriteClass.ORDINARY,
                    System.nanoTime() + TimeUnit.SECONDS.toNanos(2),
                    observer);
            var cancelled = new CompletableFuture<Boolean>();
            Thread.ofVirtual().start(() -> cancelled.complete(write.cancel()));
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            assertTrue(transport.claimNext());
            release.complete(null);
            assertFalse(cancelled.get(2, TimeUnit.SECONDS));
            transport.completeClaimed();
            assertEquals(1, observer.writes);
        } finally {
            release.complete(null);
            connection.close();
            callbacks.close();
            assertTrue(callbacks.awaitTermination(Duration.ofSeconds(2)));
        }
    }
    /** Minimal internal observer distinguishing a permitted physical write from terminal cancellation. */
    private static final class GuardObserver implements kg.aidarbek.smpp.spi.WriteObserver {
        int writes;
        int failures;
        private final boolean throwOnFailure;
        private final boolean throwOnGuard;

        GuardObserver() {
            this(false);
        }

        GuardObserver(boolean throwOnFailure) {
            this(throwOnFailure, false);
        }

        GuardObserver(boolean throwOnFailure, boolean throwOnGuard) {
            this.throwOnFailure = throwOnFailure;
            this.throwOnGuard = throwOnGuard;
        }

        @Override
        public boolean beforeWrite() {
            if (throwOnGuard) throw new IllegalStateException("Fixture guard failed");
            return true;
        }

        @Override
        public void written() {
            writes++;
        }

        @Override
        public void failed(TransportFailure failure) {
            failures++;
            if (throwOnFailure) throw new IllegalStateException("Fixture observer failed");
        }
    }

    @Test
    void blockedApplicationCompletionConsumesBoundedCapacityWithoutHoldingTransportProgress() throws Exception {
        CompletableFuture<Void> release = new CompletableFuture<>();
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        try (Fixture fixture = new Fixture()) {
            fixture.transport.deferred = true;
            NotificationSend first = fixture.session.alerts().orElseThrow().send(alert());
            var dependent = first.result().thenRun(() -> {
                entered.countDown();
                release.join();
            });
            assertTrue(fixture.transport.claimNext());
            fixture.transport.completeClaimed();
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            fixture.transport.written.remove();
            fixture.transport.deferred = false;
            int admitted = 1;
            while (admitted < 20) {
                try {
                    fixture.session.alerts().orElseThrow().send(alert());
                    fixture.transport.written.remove();
                    admitted++;
                } catch (java.util.concurrent.RejectedExecutionException full) {
                    break;
                }
            }
            assertTrue(admitted < 20);
            assertEquals(16, fixture.callbacks.outstandingCount());
            fixture.session.close();
            fixture.transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertFalse(fixture.connection.termination().toCompletableFuture().isDone());
            release.complete(null);
            dependent.toCompletableFuture().get(2, TimeUnit.SECONDS);
        } finally {
            release.complete(null);
        }
    }

    @Test
    void expiredGuardReportsWriteTimeoutWithoutSendingOrClosingAnOtherwiseHealthySession() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.transport.deferred = true;
            NotificationSend send =
                    fixture.session.alerts().orElseThrow().send(alert(), RequestOptions.timeout(Duration.ofMillis(20)));
            Thread.sleep(40);
            assertFalse(fixture.transport.claimNext());
            assertEquals(TransportFailure.Kind.WRITE_TIMEOUT, failure(send).kind());
            assertTrue(fixture.transport.written.isEmpty());
            assertTrue(fixture.session.alerts().isPresent());
        }
    }

    @Test
    void queuedCancellationWinsAndClaimedCancellationLosesWithoutSequenceReuse() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.transport.deferred = true;
            NotificationSend cancelled = fixture.session.alerts().orElseThrow().send(alert());
            assertTrue(cancelled.cancel());
            assertFalse(cancelled.cancel());
            assertEquals(TransportFailure.Kind.CANCELLED, failure(cancelled).kind());
            NotificationSend claimed = fixture.session.alerts().orElseThrow().send(alert());
            assertTrue(claimed.sequenceNumber() > cancelled.sequenceNumber());
            assertTrue(fixture.transport.claimNext());
            assertFalse(claimed.cancel());
            fixture.transport.completeClaimed();
            claimed.result().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(claimed.sequenceNumber(), RawPeer.sequence(fixture.transport.written.remove()));
            fixture.transport.receive(RawPeer.header(0x80000000L, 3, cancelled.sequenceNumber()));
            assertTrue(fixture.session.alerts().isPresent());
        }
    }

    @Test
    void transportCapacityAndCloseSettleAllAcceptedNotifications() throws Exception {
        try (Fixture fixture = new Fixture()) {
            fixture.transport.deferred = true;
            NotificationSend[] accepted = new NotificationSend[4];
            for (int i = 0; i < 4; i++)
                accepted[i] = fixture.session.alerts().orElseThrow().send(alert());
            NotificationSend rejected = fixture.session.alerts().orElseThrow().send(alert());
            assertEquals(TransportFailure.Kind.FULL, failure(rejected).kind());
            fixture.session.close();
            for (NotificationSend send : accepted)
                assertEquals(TransportFailure.Kind.CLOSED, failure(send).kind());
            assertTrue(fixture.transport.written.isEmpty());
        }
    }

    @Test
    void controlledPortSharesTheRealFrameOwnershipContract() throws Exception {
        try (CommonTestTransport transport = new CommonTestTransport()) {
            FrameTransportContract.verify(
                    transport, transport::receive, () -> transport.written.poll(2, TimeUnit.SECONDS));
        }
    }

    private static AlertNotification alert() {
        return new AlertNotification(SOURCE, DESTINATION, EMPTY);
    }

    private static TransportFailure failure(NotificationSend send) {
        return (TransportFailure) assertThrows(
                        ExecutionException.class,
                        () -> send.result().toCompletableFuture().get(2, TimeUnit.SECONDS))
                .getCause();
    }
    /** Starts a bound MC against an explicitly driven frame port with owned bounded callbacks. */
    private static final class Fixture implements AutoCloseable {
        final CommonTestTransport transport = new CommonTestTransport();
        final BoundedNotifications callbacks = new BoundedNotifications(16, 1);
        final AuthenticationDispatcher authentication = new AuthenticationDispatcher(1, 1, null);
        final EndpointConnection connection;
        final BoundSession session;

        Fixture() throws Exception {
            CompletableFuture<BoundSession> bound = new CompletableFuture<>();
            connection = EndpointConnection.server(
                    transport,
                    new InetSocketAddress("127.0.0.1", 1),
                    new ServerConfig(
                            new InetSocketAddress("127.0.0.1", 0),
                            Set.of(SmppVersion.V3_4),
                            SmppVersion.V3_4,
                            "mc",
                            1,
                            1),
                    EndpointOptions.defaults(),
                    callbacks,
                    authentication,
                    (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                    bound::complete);
            connection.start();
            transport.receive(RawPeer.bind(1, 7, 0x34));
            session = bound.get(2, TimeUnit.SECONDS);
            assertEquals(0x80000001L, RawPeer.command(transport.written.remove()));
        }

        @Override
        public void close() {
            connection.close();
            authentication.close();
            callbacks.close();
            try {
                assertTrue(authentication.awaitTermination(Duration.ofSeconds(2)));
                assertTrue(callbacks.awaitTermination(Duration.ofSeconds(2)));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        }
    }
}
