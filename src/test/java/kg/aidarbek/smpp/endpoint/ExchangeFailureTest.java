package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.SubmitSm;
import kg.aidarbek.smpp.protocol.SubmitSmResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Independent peers establish negative policy, control progress and retained callback cleanup. */
class ExchangeFailureTest {
    @Test
    void closedSessionsCannotBypassTheEndpointWideUnfinishedHandlerLimit() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<IncomingRequest<SubmitSm>> entered = new CompletableFuture<>();
        CompletableFuture<HandlerResponse<SubmitSmResponse>> decision = new CompletableFuture<>();
        EndpointHandlers handlers = EndpointHandlers.builder()
                .on(MessageOperations.SUBMIT_SM, incoming -> {
                    calls.incrementAndGet();
                    entered.complete(incoming);
                    return decision;
                })
                .build();
        SmppServer server = server(
                SmppVersion.V5_0,
                handlers,
                shutdownOptions(),
                new ExchangeOptions(1, 0, 2, 1024, Duration.ofSeconds(10)));
        try {
            var address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            IncomingRequest<SubmitSm> first;
            try (RawPeer peer = RawPeer.connect(address)) {
                peer.send(RawPeer.bind(9, 1, 0x50));
                peer.read();
                peer.send(submit(2));
                first = entered.get(2, TimeUnit.SECONDS);
            }
            first.session().termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertTrue(first.isCancelled());
            for (int index = 0; index < 5; index++) {
                try (RawPeer peer = RawPeer.connect(address)) {
                    peer.send(RawPeer.bind(9, 1, 0x50));
                    assertEquals(0, RawPeer.status(peer.read()));
                    peer.send(submit(2));
                    assertEquals(0x58, RawPeer.status(peer.read()));
                }
            }
            assertEquals(1, calls.get());
            server.close();
            assertEquals(
                    1,
                    server.termination()
                            .toCompletableFuture()
                            .get(2, TimeUnit.SECONDS)
                            .remainingHandlers());
        } finally {
            decision.complete(new HandlerResponse<>(0, accepted()));
            server.close();
        }
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("failures")
    void applicationFailureNeverImpliesAcceptanceOrStopsControlProgress(SmppVersion version, String behavior)
            throws Exception {
        EndpointHandlers.Builder handlers = EndpointHandlers.builder();
        if (!behavior.equals("absent"))
            handlers.on(MessageOperations.SUBMIT_SM, request -> {
                assertTrue(Thread.currentThread().getName().startsWith("smpp-handler-"));
                return switch (behavior) {
                    case "throw" -> throw new IllegalStateException("application failure");
                    case "error" -> throw new AssertionError("application error");
                    case "null-stage" -> null;
                    case "failed-stage" ->
                        CompletableFuture.failedFuture(new IllegalStateException("asynchronous failure"));
                    case "null-result" -> CompletableFuture.completedFuture(null);
                    case "invalid-result" -> CompletableFuture.completedFuture(new HandlerResponse<>(0, omitted()));
                    case "rejected" -> CompletableFuture.completedFuture(new HandlerResponse<>(0x49, omitted()));
                    default -> throw new AssertionError("Unrecognized test case");
                };
            });
        SmppServer server = server(version, handlers.build(), EndpointOptions.defaults(), ExchangeOptions.defaults());
        try {
            var address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            try (RawPeer peer = RawPeer.connect(address)) {
                peer.send(RawPeer.bind(9, 1, version.interfaceVersion()));
                peer.read();
                peer.send(submit(2));
                byte[] response = peer.read();
                assertEquals(0x80000004L, RawPeer.command(response));
                assertEquals(behavior.equals("rejected") ? 0x49 : 8, RawPeer.status(response));
                assertEquals(16, response.length);
                peer.send(RawPeer.header(0x15, 0, 3));
                assertEquals(0x80000015L, RawPeer.command(peer.read()));
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
    @ValueSource(booleans = {false, true})
    void blockedApplicationWorkDoesNotPreventUnbindAndIsReportedAtBoundedShutdown(boolean blockedInvocation)
            throws Exception {
        CompletableFuture<Void> release = new CompletableFuture<>();
        CompletableFuture<IncomingRequest<SubmitSm>> entered = new CompletableFuture<>();
        EndpointHandlers handlers = EndpointHandlers.builder()
                .on(MessageOperations.SUBMIT_SM, request -> {
                    entered.complete(request);
                    if (blockedInvocation) release.join();
                    return release.thenApply(ignored -> new HandlerResponse<>(0, accepted()));
                })
                .build();
        SmppServer server = server(
                SmppVersion.V5_0,
                handlers,
                shutdownOptions(),
                new ExchangeOptions(1, 0, 4, 1024, Duration.ofSeconds(2)));
        try {
            var address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            try (RawPeer peer = RawPeer.connect(address)) {
                peer.send(RawPeer.bind(9, 1, 0x50));
                peer.read();
                peer.send(submit(2));
                IncomingRequest<SubmitSm> request = entered.get(2, TimeUnit.SECONDS);
                peer.send(RawPeer.header(0x15, 0, 3));
                assertEquals(0x80000015L, RawPeer.command(peer.read()));
                peer.send(RawPeer.header(6, 0, 4));
                assertEquals(0x80000006L, RawPeer.command(peer.read()));
                assertTrue(peer.closedByEndpoint());
                assertTrue(request.isCancelled());
                server.close();
                EndpointTermination result =
                        server.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
                assertEquals(0, result.remainingConnections());
                assertEquals(1, result.remainingHandlers());
                assertTrue(!result.applicationWorkersTerminated());
                assertTrue(!result.complete());
                release.complete(null);
                assertEquals(1, result.remainingHandlers(), "Termination is an immutable deadline snapshot");
            }
        } finally {
            release.complete(null);
            server.close();
        }
    }

    @Test
    void gracefulShutdownDrainsAdmittedApplicationRepliesBeforeUnbinding() throws Exception {
        CompletableFuture<HandlerResponse<SubmitSmResponse>> decision = new CompletableFuture<>();
        CompletableFuture<IncomingRequest<SubmitSm>> entered = new CompletableFuture<>();
        EndpointHandlers handlers = EndpointHandlers.builder()
                .on(MessageOperations.SUBMIT_SM, request -> {
                    entered.complete(request);
                    return decision;
                })
                .build();
        SmppServer server = server(SmppVersion.V5_0, handlers, EndpointOptions.defaults(), ExchangeOptions.defaults());
        try {
            var address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            try (RawPeer peer = RawPeer.connect(address)) {
                peer.send(RawPeer.bind(9, 1, 0x50));
                peer.read();
                peer.send(submit(2));
                entered.get(2, TimeUnit.SECONDS);
                var shutdown = server.shutdown(Duration.ofSeconds(2));
                peer.send(RawPeer.header(0x15, 0, 3));
                assertEquals(0x80000015L, RawPeer.command(peer.read()), "Pending application work must delay unbind");
                decision.complete(new HandlerResponse<>(0, accepted()));
                assertEquals(0x80000004L, RawPeer.command(peer.read()));
                byte[] unbind = peer.read();
                assertEquals(6, RawPeer.command(unbind));
                peer.send(RawPeer.header(0x80000006L, 0, RawPeer.sequence(unbind)));
                assertTrue(
                        shutdown.toCompletableFuture().get(3, TimeUnit.SECONDS).complete());
            }
        } finally {
            decision.complete(new HandlerResponse<>(0, accepted()));
            server.close();
        }
    }

    private static EndpointOptions shutdownOptions() {
        EndpointOptions base = EndpointOptions.defaults();
        return new EndpointOptions(
                base.maximumConnections(),
                base.requestWindow(),
                base.maximumPendingBytes(),
                base.callbackThreads(),
                base.callbackQueue(),
                base.connectTimeout(),
                base.bindTimeout(),
                base.requestTimeout(),
                Duration.ofMillis(200),
                base.pduLimits());
    }

    private static SmppServer server(
            SmppVersion version, EndpointHandlers handlers, EndpointOptions options, ExchangeOptions exchange) {
        return new SmppServer(
                new ServerConfig(new InetSocketAddress("127.0.0.1", 0), Set.of(version), version, "mc", 1, 1),
                options,
                (bind, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                ignored -> {},
                null,
                new ExchangeConfig(exchange, handlers));
    }

    private static SubmitSmResponse accepted() {
        return new SubmitSmResponse(new MessageResponse(Optional.of("accepted"), EndpointPdus.NO_PARAMETERS));
    }

    private static SubmitSmResponse omitted() {
        return new SubmitSmResponse(new MessageResponse(Optional.empty(), EndpointPdus.NO_PARAMETERS));
    }

    private static byte[] submit(int sequence) {
        byte[] frame = HexFormat.of().parseHex("00000021000000040000000000000000" + "00".repeat(17));
        ByteBuffer.wrap(frame).putInt(12, sequence);
        return frame;
    }

    private static Stream<Arguments> failures() {
        return Stream.of(SmppVersion.values())
                .flatMap(version -> Stream.of(
                                "absent",
                                "throw",
                                "error",
                                "null-stage",
                                "failed-stage",
                                "null-result",
                                "invalid-result",
                                "rejected")
                        .map(behavior -> Arguments.of(version, behavior)));
    }
}
