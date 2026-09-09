package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.SubmitSm;
import kg.aidarbek.smpp.request.RequestFailure;
import kg.aidarbek.smpp.request.RequestHandle;
import kg.aidarbek.smpp.request.TransmissionCertainty;
import org.junit.jupiter.api.Test;

class ReconnectFailureTest {
    @Test
    void cancelDuringAuthenticationClosesTheAttemptAndLateAcceptanceCannotPublishOrReconnect() throws Exception {
        CompletableFuture<BindDecision> decision = new CompletableFuture<>();
        CountDownLatch entered = new CountDownLatch(1);
        LinkedBlockingQueue<BoundSession> offered = new LinkedBlockingQueue<>(1);
        SmppServer server = new SmppServer(
                new ServerConfig(
                        new InetSocketAddress("127.0.0.1", 0),
                        java.util.Set.of(SmppVersion.V3_4),
                        SmppVersion.V3_4,
                        "demo",
                        1,
                        1),
                EndpointOptions.defaults(),
                (request, peer) -> {
                    entered.countDown();
                    return decision;
                },
                session -> {});
        SmppClient client = new SmppClient();
        try {
            InetSocketAddress address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            try (ReconnectHandle handle = client.reconnect(
                    TlsEndpointsTest.config(address, BindMode.TRANSCEIVER, SmppVersion.V3_4),
                    new ReconnectPolicy(3, Duration.ZERO),
                    offered::add)) {
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                assertTrue(handle.cancel());
                ReconnectResult stopped =
                        handle.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
                assertEquals(ReconnectResult.Reason.CANCELLED, stopped.reason());
                assertEquals(1, stopped.attempts());
                assertEquals(0, stopped.publishedSessions());
                decision.complete(BindDecision.ACCEPT);
                assertFalse(handle.cancel());
                assertTrue(handle.currentSession().isEmpty());
                assertTrue(offered.isEmpty());
                assertEquals(1, handle.attempts());
            }
        } finally {
            decision.complete(BindDecision.ACCEPT);
            client.close();
            server.close();
            assertTrue(client.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
            assertTrue(server.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
        }
    }

    @Test
    void fullNotificationCapacityClosesAnUnpublishedGenerationWithoutExtraWork() throws Exception {
        EndpointOptions defaults = EndpointOptions.defaults();
        EndpointOptions options = new EndpointOptions(
                2,
                8,
                65536,
                1,
                5,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                Duration.ofMillis(100),
                defaults.pduLimits());
        SmppClient client = new SmppClient(options);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        LinkedBlockingQueue<BoundSession> offered = new LinkedBlockingQueue<>(1);
        try (ServerSocket listener = new ServerSocket(0, 2, InetAddress.getLoopbackAddress())) {
            var initial = client.connect(config(listener));
            try (RawPeer first = RawPeer.accept(listener)) {
                bind(first);
                BoundSession session = initial.toCompletableFuture().get(2, TimeUnit.SECONDS);
                var enquiry = session.enquireLink();
                enquiry.result().thenRun(() -> {
                    entered.countDown();
                    await(release);
                });
                byte[] request = first.read();
                first.send(RawPeer.header(0x80000015L, 0, RawPeer.sequence(request)));
                assertTrue(entered.await(2, TimeUnit.SECONDS));
                try (ReconnectHandle handle = client.reconnect(
                                config(listener), new ReconnectPolicy(3, Duration.ZERO), offered::add);
                        RawPeer replacement = RawPeer.accept(listener)) {
                    bind(replacement);
                    assertTrue(
                            replacement.closedByEndpoint(),
                            "An internally ready session without observer capacity must retire");
                    assertTrue(offered.isEmpty());
                    client.close();
                    EndpointTermination stopped =
                            client.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
                    assertEquals(0, stopped.remainingConnections());
                    assertEquals(6, stopped.remainingNotifications());
                    assertFalse(stopped.complete());
                    assertFalse(handle.termination().toCompletableFuture().isDone());
                    release.countDown();
                    ReconnectResult result =
                            handle.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
                    assertEquals(ReconnectResult.Reason.NOTIFICATION_CAPACITY, result.reason());
                    assertEquals(1, result.attempts());
                    assertEquals(0, result.publishedSessions());
                    assertTrue(offered.isEmpty());
                    session.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
                }
            }
        } finally {
            release.countDown();
            client.close();
            client.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void ambiguousSubmissionTerminatesInItsOriginalGenerationAndIsNeverReplayed() throws Exception {
        SmppClient client = new SmppClient();
        LinkedBlockingQueue<BoundSession> sessions = new LinkedBlockingQueue<>(2);
        try (ServerSocket listener = new ServerSocket(0, 2, InetAddress.getLoopbackAddress());
                ReconnectHandle handle =
                        client.reconnect(config(listener), new ReconnectPolicy(3, Duration.ZERO), sessions::add)) {
            RequestHandle<?> submitted;
            try (RawPeer first = RawPeer.accept(listener)) {
                bind(first);
                BoundSession session = sessions.poll(2, TimeUnit.SECONDS);
                assertNotNull(session);
                submitted = session.submission().orElseThrow().send(new SubmitSm(ExchangeMatrixTest.shortMessage()));
                assertEquals(4, RawPeer.command(first.read()));
            }
            RequestFailure failure = (RequestFailure) assertThrows(
                            ExecutionException.class,
                            () -> submitted.result().toCompletableFuture().get(2, TimeUnit.SECONDS))
                    .getCause();
            assertEquals(RequestFailure.Reason.DISCONNECTED, failure.reason());
            assertEquals(TransmissionCertainty.MAY_HAVE_BEEN_SENT, failure.transmission());
            try (RawPeer second = RawPeer.accept(listener)) {
                bind(second);
                BoundSession replacement = sessions.poll(2, TimeUnit.SECONDS);
                assertNotNull(replacement);
                second.timeout(150);
                assertThrows(
                        SocketTimeoutException.class,
                        second::read,
                        "No original submission may enter the fresh generation");
                second.timeout(2000);
                var enquiry = replacement.enquireLink();
                byte[] request = second.read();
                assertEquals(0x15, RawPeer.command(request));
                assertEquals(2, RawPeer.sequence(request));
                second.send(RawPeer.header(0x80000015L, 0, RawPeer.sequence(request)));
                assertEquals(
                        0,
                        enquiry.result()
                                .toCompletableFuture()
                                .get(2, TimeUnit.SECONDS)
                                .commandStatus());
                handle.cancel();
                assertEquals(
                        2,
                        handle.termination()
                                .toCompletableFuture()
                                .get(2, TimeUnit.SECONDS)
                                .attempts());
            }
        } finally {
            client.close();
            assertTrue(client.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
        }
    }

    @Test
    void blockedObserverRetainsItsCapacityButCannotDelayPhysicalSocketCleanupOrBoundedShutdown() throws Exception {
        EndpointOptions defaults = EndpointOptions.defaults();
        EndpointOptions options = new EndpointOptions(
                1,
                8,
                65536,
                1,
                8,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                Duration.ofMillis(100),
                defaults.pduLimits());
        SmppClient client = new SmppClient(options);
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<BoundSession> ready = new AtomicReference<>();
        try (ServerSocket listener = new ServerSocket(0, 2, InetAddress.getLoopbackAddress());
                ReconnectHandle handle =
                        client.reconnect(config(listener), new ReconnectPolicy(100, Duration.ZERO), session -> {
                            ready.set(session);
                            entered.countDown();
                            await(release);
                        })) {
            try {
                try (RawPeer first = RawPeer.accept(listener)) {
                    bind(first);
                    assertTrue(entered.await(2, TimeUnit.SECONDS));
                    assertThrows(
                            EndpointException.class,
                            () -> client.reconnect(
                                    config(listener), new ReconnectPolicy(1, Duration.ZERO), ignored -> {}));
                }
                listener.setSoTimeout(150);
                assertThrows(
                        SocketTimeoutException.class,
                        listener::accept,
                        "Successive observer invocations cannot overtake an unfinished observer");
                EndpointTermination stopped = client.shutdown(Duration.ofMillis(100))
                        .toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                assertEquals(0, stopped.remainingConnections());
                assertTrue(stopped.remainingNotifications() >= 2);
                assertFalse(stopped.complete());
                assertFalse(handle.termination().toCompletableFuture().isDone());
                release.countDown();
                assertEquals(
                        ReconnectResult.Reason.ENDPOINT_CLOSED,
                        handle.termination()
                                .toCompletableFuture()
                                .get(2, TimeUnit.SECONDS)
                                .reason());
                ready.get().termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
                assertEquals(1, handle.attempts());
            } finally {
                release.countDown();
            }
        } finally {
            release.countDown();
            client.close();
            client.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void throwingObserverStopsTheLoopAndPreservesItsFailure() throws Exception {
        SmppClient client = new SmppClient();
        RuntimeException callback = new IllegalStateException("fixture observer failure");
        try (ServerSocket listener = new ServerSocket(0, 2, InetAddress.getLoopbackAddress());
                ReconnectHandle handle =
                        client.reconnect(config(listener), new ReconnectPolicy(10, Duration.ZERO), session -> {
                            throw callback;
                        })) {
            try (RawPeer peer = RawPeer.accept(listener)) {
                bind(peer);
                assertTrue(peer.closedByEndpoint());
            }
            ReconnectResult result = handle.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(ReconnectResult.Reason.CALLBACK_FAILED, result.reason());
            assertSame(callback, result.lastFailure().orElseThrow());
            assertEquals(1, result.attempts());
        } finally {
            client.close();
            assertTrue(client.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
        }
    }

    static ClientConfig config(ServerSocket listener) {
        return TlsEndpointsTest.config(
                (InetSocketAddress) listener.getLocalSocketAddress(), BindMode.TRANSCEIVER, SmppVersion.V3_4);
    }

    static void bind(RawPeer peer) throws Exception {
        byte[] request = peer.read();
        assertEquals(9, RawPeer.command(request));
        peer.send(RawPeer.bindResponse(0x80000009L, 0, RawPeer.sequence(request), 0x34));
    }

    static void await(CountDownLatch release) {
        try {
            if (!release.await(4, TimeUnit.SECONDS))
                throw new IllegalStateException("Fixture observer release expired");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }
}
