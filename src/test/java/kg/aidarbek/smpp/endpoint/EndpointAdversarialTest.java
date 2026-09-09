package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.request.RequestFailure;
import kg.aidarbek.smpp.spi.TransportFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class EndpointAdversarialTest {
    @ParameterizedTest
    @ValueSource(strings = {"reject", "throw", "error", "null-stage", "failed-stage", "null-decision"})
    void authenticationFailuresProduceBoundedNegativeReplies(String behavior) throws Exception {
        SmppServer server = new SmppServer(
                serverConfig(1, 1),
                options(4, Duration.ofSeconds(1), 2, 16),
                (request, address) -> switch (behavior) {
                    case "reject" -> CompletableFuture.completedFuture(new BindDecision(0x0e));
                    case "throw" -> throw new IllegalStateException("application failed");
                    case "error" -> throw new AssertionError("application failed");
                    case "null-stage" -> null;
                    case "failed-stage" ->
                        CompletableFuture.failedFuture(new IllegalStateException("application failed"));
                    case "null-decision" -> CompletableFuture.completedFuture(null);
                    default -> throw new IllegalArgumentException("Unknown fixture behavior");
                },
                ignored -> {});
        try {
            InetSocketAddress address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            try (RawPeer peer = RawPeer.connect(address)) {
                peer.send(RawPeer.bind(9, 17, 0x50));
                byte[] response = peer.read();
                assertEquals(0x80000009L, RawPeer.command(response));
                assertEquals(behavior.equals("reject") ? 0x0e : 8, RawPeer.status(response));
                assertEquals(17, RawPeer.sequence(response));
                assertTrue(peer.closedByEndpoint());
                assertTrue(server.sessions().isEmpty());
            }
        } finally {
            server.close();
            assertTrue(server.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
        }
    }

    @ParameterizedTest
    @MethodSource("versionCases")
    void rawAdvertisementsRemainSeparateFromRequestedAndEffectiveVersion(
            int requested, Integer advertised, boolean strict, Integer effective) throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                SmppClient client = new SmppClient()) {
            ConnectionAttempt attempt = client.connectAttempt(
                    clientConfig((InetSocketAddress) listener.getLocalSocketAddress(), requested, strict));
            try (RawPeer peer = RawPeer.accept(listener)) {
                byte[] bind = peer.read();
                peer.send(RawPeer.bindResponse(0x80000009L, 0, RawPeer.sequence(bind), advertised));
                if (effective == null) {
                    ExecutionException error = assertThrows(
                            ExecutionException.class,
                            () -> attempt.result().toCompletableFuture().get(2, TimeUnit.SECONDS));
                    EndpointException failure = (EndpointException) error.getCause();
                    assertEquals(EndpointException.Reason.VERSION_REJECTED, failure.reason());
                    assertEquals(
                            advertised == null ? -1 : advertised,
                            failure.advertisement().orElse(-1));
                    assertTrue(peer.closedByEndpoint());
                } else {
                    BoundSession session =
                            attempt.result().toCompletableFuture().get(2, TimeUnit.SECONDS);
                    assertEquals(requested, session.negotiation().requestedInterfaceVersion());
                    assertEquals(
                            advertised == null ? -1 : advertised,
                            session.negotiation().advertisement().orElse(-1));
                    assertEquals(
                            effective.intValue(),
                            session.negotiation()
                                    .effectiveProfile()
                                    .orElseThrow()
                                    .version()
                                    .interfaceVersion());
                    var enquiry = session.enquireLink();
                    byte[] request = peer.read();
                    peer.send(RawPeer.header(0x80000015L, 0, RawPeer.sequence(request)));
                    assertEquals(
                            0,
                            enquiry.result()
                                    .toCompletableFuture()
                                    .get(2, TimeUnit.SECONDS)
                                    .commandStatus());
                }
            } finally {
                assertTrue(client.shutdown(Duration.ofSeconds(2))
                        .toCompletableFuture()
                        .get(3, TimeUnit.SECONDS)
                        .complete());
            }
        }
    }

    static Stream<Arguments> versionCases() {
        return Stream.of(0x34, 0x50)
                .flatMap(requested -> Stream.of((Integer) null, 0x34, 0x50, 0x60)
                        .flatMap(advertised -> Stream.of(false, true).map(strict -> {
                            Integer effective = advertised == null
                                    ? (strict ? null : Integer.valueOf(0x34))
                                    : advertised == 0x60 || (requested == 0x50 && advertised == 0x34)
                                            ? null
                                            : requested;
                            return Arguments.of(requested, advertised, strict, effective);
                        })));
    }

    @Test
    void rawWrongResponsesCannotBindAndMalformedResponsesCloseWithoutANackLoop() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                SmppClient client = new SmppClient()) {
            var result = client.connect(clientConfig((InetSocketAddress) listener.getLocalSocketAddress(), 0x34, true));
            try (RawPeer peer = RawPeer.accept(listener)) {
                byte[] bind = peer.read();
                peer.send(RawPeer.header(0x80000006L, 0, RawPeer.sequence(bind)));
                peer.send(RawPeer.bindResponse(0x80000009L, 0, 99, 0x34));
                peer.send(RawPeer.header(0x15, 0, 77));
                byte[] negative = peer.read();
                assertEquals(0x80000015L, RawPeer.command(negative));
                assertEquals(4, RawPeer.status(negative));
                assertFalse(result.toCompletableFuture().isDone());
                peer.send(RawPeer.bindResponse(0x80000009L, 0, RawPeer.sequence(bind), 0x34));
                BoundSession session = result.toCompletableFuture().get(2, TimeUnit.SECONDS);
                peer.send(ByteBuffer.allocate(17)
                        .putInt(17)
                        .putInt(0x80000015)
                        .putInt(0)
                        .putInt(2)
                        .put((byte) 1)
                        .array());
                assertTrue(peer.closedByEndpoint(), "Malformed response must close without another response");
                session.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void rawDisconnectAndRefusedConnectRetainTheirTransportCause() throws Exception {
        InetSocketAddress refused;
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                SmppClient client = new SmppClient()) {
            refused = (InetSocketAddress) listener.getLocalSocketAddress();
            var binding = client.connect(clientConfig(refused, 0x50, true));
            try (RawPeer peer = RawPeer.accept(listener)) {
                assertEquals(9, RawPeer.command(peer.read()));
            }
            ExecutionException disconnected = assertThrows(
                    ExecutionException.class,
                    () -> binding.toCompletableFuture().get(2, TimeUnit.SECONDS));
            RequestFailure failure = (RequestFailure) disconnected.getCause();
            assertEquals(RequestFailure.Reason.DISCONNECTED, failure.reason());
            assertTrue(failure.getCause() instanceof TransportFailure);
        }
        try (SmppClient client = new SmppClient()) {
            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> client.connect(clientConfig(refused, 0x50, true))
                            .toCompletableFuture()
                            .get(2, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof TransportFailure);
            assertEquals(TransportFailure.Kind.CONNECT_FAILED, ((TransportFailure) failure.getCause()).kind());
        }
    }

    @Test
    void idleAcceptedConnectionsAndBlockedInvocationsHaveBoundedLifetimesAndPhysicalCapacity() throws Exception {
        CompletableFuture<Void> release = new CompletableFuture<>();
        CountDownLatch invoked = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        SmppServer server = new SmppServer(
                serverConfig(1, 0),
                options(2, Duration.ofMillis(150), 2, 16),
                (request, peer) -> {
                    calls.incrementAndGet();
                    invoked.countDown();
                    release.join();
                    return CompletableFuture.completedFuture(BindDecision.ACCEPT);
                },
                ignored -> {});
        try {
            InetSocketAddress address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            try (RawPeer idle = RawPeer.connect(address)) {
                assertTrue(idle.closedByEndpoint());
            }
            try (RawPeer blocked = RawPeer.connect(address)) {
                blocked.send(RawPeer.bind(9, 1, 0x50));
                assertTrue(invoked.await(2, TimeUnit.SECONDS));
                assertTrue(blocked.closedByEndpoint());
            }
            try (RawPeer overloaded = RawPeer.connect(address)) {
                overloaded.send(RawPeer.bind(9, 2, 0x50));
                assertEquals(8, RawPeer.status(overloaded.read()));
                assertTrue(overloaded.closedByEndpoint());
            }
            EndpointTermination stopped = server.shutdown(Duration.ofMillis(150))
                    .toCompletableFuture()
                    .get(2, TimeUnit.SECONDS);
            assertFalse(stopped.complete());
            assertEquals(1, stopped.remainingAuthentications());
            assertEquals(0, stopped.remainingConnections());
            assertEquals(1, calls.get());
        } finally {
            release.complete(null);
            server.close();
        }
    }

    @Test
    void authenticationQueueIsFiniteAndSuppliedExecutorSurvivesEndpointClosure() throws Exception {
        CompletableFuture<BindDecision> release = new CompletableFuture<>();
        CountDownLatch firstInvoked = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (var supplied = Executors.newSingleThreadExecutor()) {
            SmppServer server = new SmppServer(
                    serverConfig(1, 1),
                    options(3, Duration.ofSeconds(2), 2, 16),
                    (request, peer) -> {
                        if (calls.incrementAndGet() == 1) {
                            firstInvoked.countDown();
                            return release;
                        }
                        return CompletableFuture.completedFuture(BindDecision.ACCEPT);
                    },
                    ignored -> {},
                    supplied);
            try {
                InetSocketAddress address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
                try (RawPeer first = RawPeer.connect(address);
                        RawPeer second = RawPeer.connect(address);
                        RawPeer third = RawPeer.connect(address)) {
                    first.send(RawPeer.bind(9, 1, 0x50));
                    assertTrue(firstInvoked.await(2, TimeUnit.SECONDS));
                    second.send(RawPeer.bind(9, 2, 0x50));
                    second.send(RawPeer.bind(9, 3, 0x50));
                    assertEquals(
                            4,
                            RawPeer.status(second.read()),
                            "Duplicate bind acknowledges that the queued bind was processed");
                    third.send(RawPeer.bind(9, 4, 0x50));
                    assertEquals(8, RawPeer.status(third.read()));
                    assertTrue(third.closedByEndpoint());
                    assertEquals(1, calls.get());
                    release.complete(BindDecision.ACCEPT);
                    assertEquals(0, RawPeer.status(first.read()));
                    assertEquals(0, RawPeer.status(second.read()));
                }
            } finally {
                release.complete(BindDecision.ACCEPT);
                server.close();
                assertTrue(server.termination()
                        .toCompletableFuture()
                        .get(3, TimeUnit.SECONDS)
                        .complete());
            }
            assertEquals(42, supplied.submit(() -> 42).get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void blockedNotificationsStayGloballyBoundedWhileNetworkCleanupContinues() throws Exception {
        CompletableFuture<Void> release = new CompletableFuture<>();
        CountDownLatch notification = new CountDownLatch(1);
        SmppServer server = new SmppServer(
                serverConfig(1, 1),
                options(2, Duration.ofSeconds(2), 1, 3),
                (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                session -> {
                    notification.countDown();
                    release.join();
                });
        try {
            InetSocketAddress address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            try (RawPeer first = RawPeer.connect(address)) {
                first.send(RawPeer.bind(9, 1, 0x50));
                assertEquals(0, RawPeer.status(first.read()));
                assertTrue(notification.await(2, TimeUnit.SECONDS));
                first.send(RawPeer.header(6, 0, 2));
                assertEquals(0x80000006L, RawPeer.command(first.read()));
                assertTrue(first.closedByEndpoint());
            }
            try (RawPeer second = RawPeer.connect(address)) {
                second.send(RawPeer.bind(9, 3, 0x50));
                assertEquals(0, RawPeer.status(second.read()));
                second.send(RawPeer.header(0x15, 0, 4));
                assertEquals(0x80000015L, RawPeer.command(second.read()));
                second.send(RawPeer.header(6, 0, 5));
                assertEquals(0x80000006L, RawPeer.command(second.read()));
                assertTrue(second.closedByEndpoint());
            }
            for (int attempt = 0; attempt < 3; attempt++) {
                try (RawPeer excess = RawPeer.connect(address)) {
                    assertTrue(excess.closedByEndpoint());
                }
            }
            EndpointTermination stopped = server.shutdown(Duration.ofMillis(150))
                    .toCompletableFuture()
                    .get(2, TimeUnit.SECONDS);
            assertFalse(stopped.complete());
            assertEquals(0, stopped.remainingConnections());
            assertEquals(4, stopped.remainingNotifications());
            assertFalse(stopped.applicationWorkersTerminated());
        } finally {
            release.complete(null);
            server.close();
        }
    }

    @Test
    void connectionAdmissionAndGracefulDeadlineStayBounded() throws Exception {
        SmppServer server = new SmppServer(
                serverConfig(1, 1),
                options(1, Duration.ofSeconds(2), 2, 16),
                (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                ignored -> {});
        try {
            InetSocketAddress address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            try (RawPeer first = RawPeer.connect(address)) {
                first.send(RawPeer.header(0x15, 0, 1));
                assertEquals(0x80000015L, RawPeer.command(first.read()));
                try (RawPeer excess = RawPeer.connect(address)) {
                    assertTrue(excess.closedByEndpoint());
                }
                first.send(RawPeer.bind(9, 2, 0x50));
                assertEquals(0, RawPeer.status(first.read()));
                var stopping = server.shutdown(Duration.ofMillis(150));
                assertEquals(6, RawPeer.command(first.read()));
                assertTrue(first.closedByEndpoint());
                stopping.toCompletableFuture().get(2, TimeUnit.SECONDS);
            }
        } finally {
            server.close();
        }
    }

    @Test
    void clientAdmissionControlCancellationAndPeerNackShareTheRealRequestWindow() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                SmppClient client = new SmppClient(options(1, Duration.ofSeconds(2), 2, 16))) {
            ClientConfig config = clientConfig((InetSocketAddress) listener.getLocalSocketAddress(), 0x50, true);
            var binding = client.connect(config);
            try (RawPeer peer = RawPeer.accept(listener)) {
                byte[] bind = peer.read();
                ExecutionException excess = assertThrows(
                        ExecutionException.class,
                        () -> client.connect(config).toCompletableFuture().get(2, TimeUnit.SECONDS));
                assertEquals(EndpointException.Reason.CAPACITY, ((EndpointException) excess.getCause()).reason());
                peer.send(RawPeer.bindResponse(0x80000009L, 0, RawPeer.sequence(bind), 0x50));
                BoundSession session = binding.toCompletableFuture().get(2, TimeUnit.SECONDS);
                var cancelled = session.enquireLink();
                byte[] first = peer.read();
                assertTrue(cancelled.cancel());
                ExecutionException cancellation = assertThrows(
                        ExecutionException.class,
                        () -> cancelled.result().toCompletableFuture().get(2, TimeUnit.SECONDS));
                assertEquals(RequestFailure.Reason.CANCELLED, ((RequestFailure) cancellation.getCause()).reason());
                peer.send(RawPeer.header(0x80000015L, 0, RawPeer.sequence(first)));
                var nacked = session.enquireLink();
                byte[] second = peer.read();
                assertEquals(RawPeer.sequence(first) + 1, RawPeer.sequence(second));
                peer.send(RawPeer.header(0x80000000L, 3, RawPeer.sequence(second)));
                ExecutionException negative = assertThrows(
                        ExecutionException.class,
                        () -> nacked.result().toCompletableFuture().get(2, TimeUnit.SECONDS));
                assertTrue(negative.getCause() instanceof kg.aidarbek.smpp.request.PeerNackException);
                assertEquals(
                        3,
                        ((kg.aidarbek.smpp.request.PeerNackException) negative.getCause())
                                .nack()
                                .commandStatus());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("kg.aidarbek.smpp.endpoint.SmppEndpointsTest#bindVariants")
    void unavailableMessageServicesFollowTheActualVersionModeAndDirectionMatrix(BindMode mode, SmppVersion version)
            throws Exception {
        SmppServer server = new SmppServer(
                serverConfig(1, 1),
                options(2, Duration.ofSeconds(2), 2, 16),
                (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                ignored -> {});
        try {
            InetSocketAddress address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            try (RawPeer peer = RawPeer.connect(address)) {
                peer.send(RawPeer.bind(mode.requestCommandId(), 1, version.interfaceVersion()));
                assertEquals(0, RawPeer.status(peer.read()));
                peer.send(emptyMessage(4, 2, 17));
                assertEquals(mode == BindMode.RECEIVER ? 4 : 8, RawPeer.status(peer.read()));
                peer.send(emptyMessage(0x103, 3, 10));
                assertEquals(
                        version == SmppVersion.V5_0 && mode == BindMode.RECEIVER ? 4 : 8, RawPeer.status(peer.read()));
            }
        } finally {
            server.close();
            assertTrue(server.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
        }
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                SmppClient client = new SmppClient()) {
            var binding = client.connect(new ClientConfig(
                    (InetSocketAddress) listener.getLocalSocketAddress(),
                    new BindRequest(mode, "", "", "", version.interfaceVersion(), 0, 0, ""),
                    true));
            try (RawPeer peer = RawPeer.accept(listener)) {
                byte[] bind = peer.read();
                peer.send(RawPeer.bindResponse(
                        mode.responseCommandId(), 0, RawPeer.sequence(bind), version.interfaceVersion()));
                binding.toCompletableFuture().get(2, TimeUnit.SECONDS);
                peer.send(emptyMessage(5, 2, 17));
                assertEquals(mode == BindMode.TRANSMITTER ? 4 : 8, RawPeer.status(peer.read()));
                peer.send(emptyMessage(0x103, 3, 10));
                assertEquals(
                        version == SmppVersion.V5_0 && mode == BindMode.TRANSMITTER ? 4 : 8,
                        RawPeer.status(peer.read()));
            }
        }
    }

    private static byte[] emptyMessage(long command, int sequence, int bodyLength) {
        return ByteBuffer.allocate(16 + bodyLength)
                .putInt(16 + bodyLength)
                .putInt((int) command)
                .putInt(0)
                .putInt(sequence)
                .array();
    }

    private static ClientConfig clientConfig(InetSocketAddress address, int version, boolean strict) {
        return new ClientConfig(address, new BindRequest(BindMode.TRANSCEIVER, "", "", "", version, 0, 0, ""), strict);
    }

    static ServerConfig serverConfig(int authenticationConcurrency, int authenticationQueue) {
        return new ServerConfig(
                new InetSocketAddress("127.0.0.1", 0),
                Set.of(SmppVersion.V3_4, SmppVersion.V5_0),
                SmppVersion.V5_0,
                "demo",
                authenticationConcurrency,
                authenticationQueue);
    }

    static EndpointOptions options(int connections, Duration bindTimeout, int callbackThreads, int callbackQueue) {
        EndpointOptions defaults = EndpointOptions.defaults();
        return new EndpointOptions(
                connections,
                defaults.requestWindow(),
                defaults.maximumPendingBytes(),
                callbackThreads,
                callbackQueue,
                defaults.connectTimeout(),
                bindTimeout,
                defaults.requestTimeout(),
                defaults.shutdownTimeout(),
                defaults.pduLimits());
    }
}
