package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.request.RequestFailure;
import kg.aidarbek.smpp.session.SessionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SmppEndpointsTest {
    @Test
    void gracefulShutdownDrainsAnAlreadyAdmittedEnquiryBeforeSendingUnbind() throws Exception {
        try (ServerSocket listener = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress());
                SmppClient client = new SmppClient()) {
            ConnectionAttempt attempt = client.connectAttempt(new ClientConfig(
                    (InetSocketAddress) listener.getLocalSocketAddress(),
                    new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x34, 0, 0, ""),
                    true));
            try (RawPeer peer = RawPeer.accept(listener)) {
                byte[] bind = peer.read();
                peer.send(RawPeer.bindResponse(0x80000009L, 0, RawPeer.sequence(bind), 0x34));
                BoundSession session = attempt.result().toCompletableFuture().get(2, TimeUnit.SECONDS);
                var enquiry = session.enquireLink();
                byte[] request = peer.read();
                assertEquals(0x15, RawPeer.command(request));
                var stopping = client.shutdown(Duration.ofSeconds(2));
                peer.timeout(100);
                assertThrows(
                        SocketTimeoutException.class, peer::read, "Unbind must wait for the pending enquiry outcome");
                peer.timeout(2000);
                peer.send(RawPeer.header(0x80000015L, 0, RawPeer.sequence(request)));
                assertEquals(
                        0,
                        enquiry.result()
                                .toCompletableFuture()
                                .get(2, TimeUnit.SECONDS)
                                .commandStatus());
                byte[] unbind = peer.read();
                assertEquals(6, RawPeer.command(unbind));
                peer.send(RawPeer.header(0x80000006L, 0, RawPeer.sequence(unbind)));
                assertTrue(
                        stopping.toCompletableFuture().get(3, TimeUnit.SECONDS).complete());
                assertTrue(peer.closedByEndpoint());
            }
        }
    }

    @Test
    void invalidShutdownDoesNotConsumeAnUnstartedServerLifecycle() throws Exception {
        SmppServer server = new SmppServer(
                new ServerConfig(
                        new InetSocketAddress("127.0.0.1", 0),
                        Set.of(SmppVersion.V3_4),
                        SmppVersion.V3_4,
                        "demo",
                        1,
                        1),
                EndpointOptions.defaults(),
                (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                ignored -> {});
        try {
            assertThrows(NullPointerException.class, () -> server.shutdown(null));
            assertThrows(IllegalArgumentException.class, () -> server.shutdown(Duration.ofSeconds(-1)));
            assertTrue(server.start()
                            .toCompletableFuture()
                            .get(2, TimeUnit.SECONDS)
                            .getPort()
                    > 0);
        } finally {
            server.close();
            assertTrue(server.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
        }
    }

    @Test
    void explicitAttemptCancellationWinsDuringAuthenticationButCannotCancelAnEstablishedSession() throws Exception {
        CompletableFuture<BindDecision> delayed = new CompletableFuture<>();
        CountDownLatch authenticating = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        SmppServer server = new SmppServer(
                new ServerConfig(
                        new InetSocketAddress("127.0.0.1", 0),
                        Set.of(SmppVersion.V3_4),
                        SmppVersion.V3_4,
                        "demo",
                        2,
                        2),
                EndpointOptions.defaults(),
                (request, peer) -> {
                    if (calls.incrementAndGet() == 1) {
                        authenticating.countDown();
                        return delayed;
                    }
                    return CompletableFuture.completedFuture(BindDecision.ACCEPT);
                },
                ignored -> {});
        SmppClient client = new SmppClient();
        try {
            InetSocketAddress address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            ClientConfig config =
                    new ClientConfig(address, new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x34, 0, 0, ""), true);
            ConnectionAttempt cancelled = client.connectAttempt(config);
            assertTrue(authenticating.await(2, TimeUnit.SECONDS));
            assertTrue(cancelled.cancel());
            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> cancelled.result().toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertEquals(RequestFailure.Reason.CANCELLED, ((RequestFailure) failure.getCause()).reason());
            assertFalse(cancelled.cancel());
            delayed.complete(BindDecision.ACCEPT);
            ConnectionAttempt successful = client.connectAttempt(config);
            BoundSession session = successful.result().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertFalse(successful.cancel());
            assertEquals(
                    0,
                    session.enquireLink()
                            .result()
                            .toCompletableFuture()
                            .get(2, TimeUnit.SECONDS)
                            .commandStatus());
        } finally {
            delayed.complete(BindDecision.ACCEPT);
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

    @ParameterizedTest
    @MethodSource("connectionVariants")
    void clientAndServerBindEveryModeAndProfileExchangeEnquiriesAndUnbind(
            BindMode mode, SmppVersion version, boolean serverUnbind) throws Exception {
        CompletableFuture<BoundSession> accepted = new CompletableFuture<>();
        SmppServer server = new SmppServer(
                new ServerConfig(new InetSocketAddress("127.0.0.1", 0), Set.of(version), version, "demo", 2, 2),
                EndpointOptions.defaults(),
                (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                accepted::complete);
        SmppClient client = new SmppClient();
        try {
            InetSocketAddress address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            BoundSession session = client.connect(new ClientConfig(
                            address,
                            new BindRequest(mode, "demo", "demo", "", version.interfaceVersion(), 0, 0, ""),
                            true))
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS);
            assertNotNull(session, "A successful connection must expose its bound session");
            BoundSession peer = accepted.get(3, TimeUnit.SECONDS);
            assertEquals(mode, session.bindMode());
            assertEquals(
                    version,
                    session.negotiation().effectiveProfile().orElseThrow().version());
            assertEquals(
                    version, peer.negotiation().effectiveProfile().orElseThrow().version());
            assertEquals(
                    0,
                    session.enquireLink()
                            .result()
                            .toCompletableFuture()
                            .get(2, TimeUnit.SECONDS)
                            .commandStatus());
            assertEquals(
                    0,
                    peer.enquireLink()
                            .result()
                            .toCompletableFuture()
                            .get(2, TimeUnit.SECONDS)
                            .commandStatus());
            assertEquals(
                    0,
                    (serverUnbind ? peer : session)
                            .unbind()
                            .result()
                            .toCompletableFuture()
                            .get(2, TimeUnit.SECONDS)
                            .commandStatus());
            session.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            peer.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(SessionState.CLOSED, session.state());
            assertEquals(SessionState.CLOSED, peer.state());
        } finally {
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

    static Stream<Arguments> connectionVariants() {
        return bindVariants()
                .flatMap(variant -> Stream.of(false, true)
                        .map(serverUnbind -> Arguments.of(variant.get()[0], variant.get()[1], serverUnbind)));
    }

    static Stream<Arguments> bindVariants() {
        return Stream.of(BindMode.values())
                .flatMap(mode -> Stream.of(SmppVersion.values()).map(version -> Arguments.of(mode, version)));
    }

    @Test
    void serverBindsARealListenerAndClosesAnAcceptedIdleConnection() throws Exception {
        SmppServer server = new SmppServer(
                new ServerConfig(
                        new InetSocketAddress("127.0.0.1", 0),
                        Set.of(SmppVersion.V3_4, SmppVersion.V5_0),
                        SmppVersion.V5_0,
                        "demo",
                        1,
                        1),
                EndpointOptions.defaults(),
                (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                ignored -> {});
        try {
            InetSocketAddress address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertTrue(address.getPort() > 0, "Server must bind the requested ephemeral TCP listener");
            try (Socket peer = new Socket()) {
                peer.connect(address, 2000);
                peer.setSoTimeout(2000);
                peer.getOutputStream().write(HexFormat.of().parseHex("00000010000000150000000000000001"));
                assertArrayEquals(
                        HexFormat.of().parseHex("00000010800000150000000000000001"),
                        peer.getInputStream().readNBytes(16));
                server.close();
                assertTrue(server.termination()
                        .toCompletableFuture()
                        .get(3, TimeUnit.SECONDS)
                        .complete());
                assertTrue(peer.getInputStream().read() < 0);
            }
        } finally {
            server.close();
        }
    }
}
