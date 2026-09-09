package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.Outbind;
import org.junit.jupiter.api.Test;

class OutbindLifecycleTest {
    @Test
    void successfulAuthenticationClosesImmediatelyIfFollowUpBindCannotReserveItsResultCallback() throws Exception {
        EndpointOptions defaults = EndpointOptions.defaults();
        EndpointOptions options = new EndpointOptions(
                2,
                2,
                4096,
                1,
                1,
                Duration.ofSeconds(1),
                Duration.ofSeconds(5),
                Duration.ofSeconds(1),
                Duration.ofMillis(100),
                defaults.pduLimits());
        CountDownLatch authenticated = new CountDownLatch(1);
        try (OutbindListener listener = listener(
                options,
                (request, peer) -> {
                    authenticated.countDown();
                    return CompletableFuture.completedFuture(true);
                },
                ignored -> fail("Bind cannot be admitted"))) {
            try (RawPeer peer =
                    RawPeer.connect(listener.start().toCompletableFuture().get(2, TimeUnit.SECONDS))) {
                peer.send(outbind(1));
                assertTrue(authenticated.await(2, TimeUnit.SECONDS));
                peer.timeout(300);
                assertTrue(peer.closedByEndpoint());
            }
        }
    }

    @Test
    void threeFourOutbindRejectsNonReceiverBindsBeforeAuthentication() throws Exception {
        for (long command : new long[] {2, 9}) {
            AtomicInteger authentications = new AtomicInteger();
            try (ServerSocket listener = new ServerSocket(0, 2, java.net.InetAddress.getLoopbackAddress());
                    OutbindConnector connector = connector((request, peer) -> {
                        authentications.incrementAndGet();
                        return CompletableFuture.completedFuture(BindDecision.ACCEPT);
                    })) {
                ConnectionAttempt attempt = connector.connectAttempt(
                        (InetSocketAddress) listener.getLocalSocketAddress(),
                        new Outbind("mc", "pw", CommonOperationsEndpointTest.EMPTY));
                try (RawPeer peer = RawPeer.accept(listener)) {
                    assertEquals(0x0b, RawPeer.command(peer.read()));
                    peer.send(RawPeer.bind(command, 31, 0x34));
                    assertEquals(4, RawPeer.status(peer.read()));
                    assertTrue(peer.closedByEndpoint());
                    assertThrows(
                            ExecutionException.class,
                            () -> attempt.result().toCompletableFuture().get(2, TimeUnit.SECONDS));
                    assertEquals(0, authentications.get());
                }
            }
        }
    }

    @Test
    void connectorReportsItsRejectedFollowUpBindStatusAndNeverReconnects() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 2, java.net.InetAddress.getLoopbackAddress());
                OutbindConnector connector =
                        connector((request, peer) -> CompletableFuture.completedFuture(new BindDecision(0x0e)))) {
            ConnectionAttempt attempt = connector.connectAttempt(
                    (InetSocketAddress) listener.getLocalSocketAddress(),
                    new Outbind("mc", "pw", CommonOperationsEndpointTest.EMPTY));
            try (RawPeer peer = RawPeer.accept(listener)) {
                byte[] notification = peer.read();
                assertEquals(0x0b, RawPeer.command(notification));
                assertArrayEquals(outbind(1), notification);
                peer.send(RawPeer.bind(1, 51, 0x34));
                byte[] rejected = peer.read();
                assertEquals(0x80000001L, RawPeer.command(rejected));
                assertEquals(0x0e, RawPeer.status(rejected));
                EndpointException failure = (EndpointException) assertThrows(
                                ExecutionException.class,
                                () -> attempt.result().toCompletableFuture().get(2, TimeUnit.SECONDS))
                        .getCause();
                assertEquals(EndpointException.Reason.BIND_REJECTED, failure.reason());
                assertEquals(0x0e, failure.commandStatus().orElseThrow());
                assertTrue(peer.closedByEndpoint());
            }
            listener.setSoTimeout(100);
            assertThrows(SocketTimeoutException.class, listener::accept);
        }
    }

    @Test
    void listenerWaitsForOutbindAndAuthDecisionWithoutSendingBindCredentials() throws Exception {
        CompletableFuture<Boolean> decision = new CompletableFuture<>();
        CountDownLatch invoked = new CountDownLatch(1);
        AtomicInteger bound = new AtomicInteger();
        try (OutbindListener listener = listener(
                EndpointOptions.defaults(),
                (request, peer) -> {
                    invoked.countDown();
                    return decision;
                },
                ignored -> bound.incrementAndGet())) {
            InetSocketAddress address = listener.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            try (RawPeer peer = RawPeer.connect(address)) {
                peer.timeout(60);
                assertThrows(SocketTimeoutException.class, peer::read);
                peer.send(outbind(17));
                assertTrue(invoked.await(2, TimeUnit.SECONDS));
                assertThrows(SocketTimeoutException.class, peer::read);
                decision.complete(false);
                peer.timeout(2000);
                assertTrue(peer.closedByEndpoint());
                assertEquals(0, bound.get());
            }
        } finally {
            decision.complete(false);
        }
    }

    @Test
    void duplicateOutbindClosesInsteadOfStartingAnotherAuthentication() throws Exception {
        CompletableFuture<Boolean> decision = new CompletableFuture<>();
        CountDownLatch invoked = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (OutbindListener listener = listener(
                EndpointOptions.defaults(),
                (request, peer) -> {
                    calls.incrementAndGet();
                    invoked.countDown();
                    return decision;
                },
                ignored -> fail("Late authentication cannot bind"))) {
            try (RawPeer peer =
                    RawPeer.connect(listener.start().toCompletableFuture().get(2, TimeUnit.SECONDS))) {
                peer.send(outbind(11));
                assertTrue(invoked.await(2, TimeUnit.SECONDS));
                peer.send(outbind(12));
                assertTrue(peer.closedByEndpoint());
                decision.complete(true);
                assertEquals(1, calls.get());
            }
        } finally {
            decision.complete(false);
        }
    }

    @Test
    void outbindAuthenticationTimeoutRetainsPhysicalCapacityAndPreventsLateBinding() throws Exception {
        CompletableFuture<Boolean> decision = new CompletableFuture<>();
        CountDownLatch invoked = new CountDownLatch(1);
        EndpointOptions options = options(Duration.ofMillis(150));
        OutbindListener listener = listener(
                options,
                (request, peer) -> {
                    invoked.countDown();
                    return decision;
                },
                ignored -> fail("Expired outbind cannot bind"));
        try {
            try (RawPeer peer =
                    RawPeer.connect(listener.start().toCompletableFuture().get(2, TimeUnit.SECONDS))) {
                peer.send(outbind(11));
                assertTrue(invoked.await(2, TimeUnit.SECONDS));
                assertTrue(peer.closedByEndpoint());
                EndpointTermination termination = listener.shutdown(Duration.ofMillis(80))
                        .toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                assertEquals(1, termination.remainingHandlers());
                assertFalse(termination.complete());
                decision.complete(true);
            }
        } finally {
            decision.complete(false);
            listener.close();
        }
    }

    @Test
    void cancellationAfterOutbindClosesTheOnlyAttemptAndRejectsLateBinding() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 2, java.net.InetAddress.getLoopbackAddress());
                OutbindConnector connector =
                        connector((request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT))) {
            ConnectionAttempt attempt = connector.connectAttempt(
                    (InetSocketAddress) listener.getLocalSocketAddress(),
                    new Outbind("mc", "pw", CommonOperationsEndpointTest.EMPTY));
            try (RawPeer peer = RawPeer.accept(listener)) {
                assertEquals(0x0b, RawPeer.command(peer.read()));
                assertTrue(attempt.cancel());
                assertFalse(attempt.cancel());
                EndpointException failure = (EndpointException) assertThrows(
                                ExecutionException.class,
                                () -> attempt.result().toCompletableFuture().get(2, TimeUnit.SECONDS))
                        .getCause();
                assertEquals(EndpointException.Reason.CANCELLED, failure.reason());
                assertTrue(peer.closedByEndpoint());
            }
        }
    }

    static OutbindConnector connector(BindAuthenticator authenticator) {
        return new OutbindConnector(
                new OutbindConnectorConfig(Set.of(SmppVersion.V3_4), SmppVersion.V3_4, "mc", 1, 1),
                EndpointOptions.defaults(),
                authenticator,
                ExchangeConfig.defaults());
    }

    static OutbindListener listener(
            EndpointOptions options,
            OutbindAuthenticator authenticator,
            java.util.function.Consumer<BoundSession> bound) {
        return new OutbindListener(
                new OutbindListenerConfig(
                        new InetSocketAddress("127.0.0.1", 0),
                        new BindRequest(BindMode.RECEIVER, "esme", "pw", "", 0x34, 0, 0, ""),
                        false),
                options,
                authenticator,
                bound,
                new ExchangeConfig(
                        new ExchangeOptions(1, 0, 2, 4096, Duration.ofSeconds(1)), EndpointHandlers.empty()));
    }

    static EndpointOptions options(Duration bind) {
        EndpointOptions defaults = EndpointOptions.defaults();
        return new EndpointOptions(
                4,
                2,
                4096,
                2,
                16,
                Duration.ofSeconds(1),
                bind,
                Duration.ofSeconds(1),
                Duration.ofMillis(100),
                defaults.pduLimits());
    }

    static byte[] outbind(long sequence) {
        return ByteBuffer.allocate(22)
                .putInt(22)
                .putInt(0x0b)
                .putInt(0)
                .putInt((int) sequence)
                .put(new byte[] {'m', 'c', 0, 'p', 'w', 0})
                .array();
    }
}
