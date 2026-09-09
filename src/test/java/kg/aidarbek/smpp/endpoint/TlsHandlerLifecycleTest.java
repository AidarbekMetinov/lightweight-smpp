package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.SubmitSm;
import kg.aidarbek.smpp.protocol.SubmitSmResponse;
import kg.aidarbek.smpp.transport.TlsConfig;
import kg.aidarbek.smpp.transport.TlsTestMaterial;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TlsHandlerLifecycleTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void blockingInvocationOrStageCannotStallTlsControlOrPhysicalCleanup(boolean invocationBlocks) throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        CompletableFuture<HandlerResponse<SubmitSmResponse>> decision = new CompletableFuture<>();
        HandlerResponse<SubmitSmResponse> accepted = new HandlerResponse<>(
                0, new SubmitSmResponse(new MessageResponse(Optional.of("id"), EndpointPdus.NO_PARAMETERS)));
        AtomicReference<IncomingRequest<SubmitSm>> incoming = new AtomicReference<>();
        EndpointHandlers handlers = EndpointHandlers.builder()
                .on(MessageOperations.SUBMIT_SM, request -> {
                    incoming.set(request);
                    entered.countDown();
                    if (invocationBlocks) ReconnectFailureTest.await(release);
                    return decision;
                })
                .build();
        EndpointOptions defaults = EndpointOptions.defaults();
        EndpointOptions options = new EndpointOptions(
                1,
                8,
                65536,
                2,
                16,
                defaults.connectTimeout(),
                defaults.bindTimeout(),
                defaults.requestTimeout(),
                Duration.ofMillis(100),
                defaults.pduLimits());
        KeepalivePolicy keepalive = new KeepalivePolicy(Duration.ofMillis(50), Duration.ofMillis(500));
        ConnectionLifecycle serverLifecycle = ConnectionLifecycle.defaults()
                .withTls(TlsConfig.server(TlsTestMaterial.context(true, true), Duration.ofSeconds(2)))
                .withKeepalive(keepalive);
        ConnectionLifecycle clientLifecycle = ConnectionLifecycle.defaults()
                .withTls(TlsConfig.client(TlsTestMaterial.context(false, true), "localhost", Duration.ofSeconds(2)))
                .withKeepalive(keepalive);
        SmppServer server = new SmppServer(
                new ServerConfig(
                        new InetSocketAddress("127.0.0.1", 0),
                        Set.of(SmppVersion.V5_0),
                        SmppVersion.V5_0,
                        "demo",
                        1,
                        1),
                options,
                (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                session -> {},
                null,
                new ExchangeConfig(new ExchangeOptions(1, 1, 2, 65536, Duration.ofSeconds(3)), handlers),
                serverLifecycle);
        SmppClient client = new SmppClient(options, ExchangeConfig.defaults(), clientLifecycle);
        try {
            InetSocketAddress address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            BoundSession session = client.connect(
                            TlsEndpointsTest.config(address, BindMode.TRANSCEIVER, SmppVersion.V5_0))
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS);
            var submission = session.submission().orElseThrow().send(new SubmitSm(ExchangeMatrixTest.shortMessage()));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals(
                    0,
                    session.enquireLink()
                            .result()
                            .toCompletableFuture()
                            .get(1, TimeUnit.SECONDS)
                            .commandStatus());
            BoundSession peer = server.sessions().getFirst();
            assertEquals(1, peer.resources().pendingReplies());
            assertEquals(
                    0,
                    peer.enquireLink()
                            .result()
                            .toCompletableFuture()
                            .get(1, TimeUnit.SECONDS)
                            .commandStatus());
            server.close();
            EndpointTermination stopped =
                    server.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(0, stopped.remainingConnections());
            assertEquals(1, stopped.remainingHandlers());
            assertFalse(stopped.complete());
            assertTrue(incoming.get().isCancelled());
            session.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertTrue(submission.isDone());
            release.countDown();
            decision.complete(accepted);
        } finally {
            release.countDown();
            decision.complete(accepted);
            client.close();
            server.close();
            assertTrue(client.termination()
                    .toCompletableFuture()
                    .get(2, TimeUnit.SECONDS)
                    .complete());
            server.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
        }
    }
}
