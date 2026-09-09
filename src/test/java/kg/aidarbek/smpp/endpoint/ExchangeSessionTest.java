package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kg.aidarbek.smpp.codec.MessageCommandCodecs;
import kg.aidarbek.smpp.codec.PduCodec;
import kg.aidarbek.smpp.codec.PduLimits;
import kg.aidarbek.smpp.profile.MessageDirection;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.DataSm;
import kg.aidarbek.smpp.protocol.DataSmResponse;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.SubmitSm;
import kg.aidarbek.smpp.protocol.SubmitSmResponse;
import kg.aidarbek.smpp.protocol.Tlv;
import kg.aidarbek.smpp.request.PeerNackException;
import kg.aidarbek.smpp.request.RequestFailure;
import kg.aidarbek.smpp.request.TransmissionCertainty;
import kg.aidarbek.smpp.session.SessionState;
import org.junit.jupiter.api.Test;

class ExchangeSessionTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(SmppVersion.class)
    void messageResultsShareOutOfOrderNackCancellationAndLateResponseCorrelation(SmppVersion version) throws Exception {
        SmppClient client = new SmppClient();
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var binding = client.connect(new ClientConfig(
                    (InetSocketAddress) listener.getLocalSocketAddress(),
                    new BindRequest(BindMode.TRANSCEIVER, "", "", "", version.interfaceVersion(), 0, 0, ""),
                    true));
            try (RawPeer peer = RawPeer.accept(listener)) {
                peer.send(RawPeer.bindResponse(
                        0x80000009L, 0, RawPeer.sequence(peer.read()), version.interfaceVersion()));
                BoundSession session = binding.toCompletableFuture().get(2, TimeUnit.SECONDS);
                var sender = session.dataMessages().orElseThrow();
                var first = sender.send(ExchangeMatrixTest.data());
                var second = sender.send(ExchangeMatrixTest.data());
                var third = sender.send(ExchangeMatrixTest.data());
                assertEquals(2, RawPeer.sequence(peer.read()));
                assertEquals(3, RawPeer.sequence(peer.read()));
                assertEquals(4, RawPeer.sequence(peer.read()));
                peer.send(RawPeer.header(0x80000103L, 0x1234, 4));
                assertEquals(
                        0x1234,
                        third.result()
                                .toCompletableFuture()
                                .get(2, TimeUnit.SECONDS)
                                .commandStatus());
                peer.send(RawPeer.header(0x80000000L, 3, 3));
                ExecutionException nack = assertThrows(
                        ExecutionException.class,
                        () -> second.result().toCompletableFuture().get(2, TimeUnit.SECONDS));
                assertEquals(3, ((PeerNackException) nack.getCause()).nack().commandStatus());
                assertTrue(first.cancel());
                ExecutionException cancelled = assertThrows(
                        ExecutionException.class,
                        () -> first.result().toCompletableFuture().get(2, TimeUnit.SECONDS));
                assertEquals(
                        TransmissionCertainty.MAY_HAVE_BEEN_SENT,
                        ((RequestFailure) cancelled.getCause()).transmission());
                peer.send(RawPeer.header(0x80000103L, 0x400, 2));
                peer.send(RawPeer.header(0x80000103L, 0x400, 4));
                var current = sender.send(ExchangeMatrixTest.data());
                assertEquals(5, RawPeer.sequence(peer.read()));
                peer.send(HexFormat.of().parseHex("000000148000010300000000000000056e657700"));
                assertEquals(
                        "new",
                        current.result()
                                .toCompletableFuture()
                                .get(2, TimeUnit.SECONDS)
                                .command()
                                .fields()
                                .messageId()
                                .orElseThrow());
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
    void peerTransactionDiagnosticsCannotSettleAnIncompatibleOriginalRequest() throws Exception {
        SmppClient client = new SmppClient();
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var binding = client.connect(new ClientConfig(
                    (InetSocketAddress) listener.getLocalSocketAddress(),
                    new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x34, 0, 0, ""),
                    true));
            try (RawPeer peer = RawPeer.accept(listener)) {
                peer.send(RawPeer.bindResponse(0x80000009L, 0, RawPeer.sequence(peer.read()), 0x34));
                BoundSession session = binding.toCompletableFuture().get(2, TimeUnit.SECONDS);
                var request = session.dataMessages()
                        .orElseThrow()
                        .send(new DataSm(
                                "", new Address(0, 0, ""), new Address(0, 0, ""), 0, 0, 0, EndpointPdus.NO_PARAMETERS));
                assertEquals(2, RawPeer.sequence(peer.read()));
                peer.send(HexFormat.of().parseHex("0000001b8000010300000000000000026261640004230003030001"));
                var failure = assertThrows(
                        ExecutionException.class,
                        () -> request.result().toCompletableFuture().get(2, TimeUnit.SECONDS),
                        "Contextually invalid responses must fail the owning request and close without a nack loop");
                assertTrue(failure.getCause() instanceof RequestFailure);
                assertEquals(
                        TransmissionCertainty.MAY_HAVE_BEEN_SENT, ((RequestFailure) failure.getCause()).transmission());
                assertEquals(SessionState.CLOSED, session.state());
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
    void handlerTransactionDiagnosticsAreCheckedAgainstTheOriginalRequest() throws Exception {
        EndpointHandlers handlers = EndpointHandlers.builder()
                .on(
                        MessageOperations.DATA_SM,
                        request -> CompletableFuture.completedFuture(new HandlerResponse<>(
                                0,
                                new DataSmResponse(new MessageResponse(
                                        Optional.of("bad"),
                                        new OptionalParameters(List.of(new Tlv(0x0423, new byte[] {3, 0, 1}))))))))
                .build();
        SmppServer server = server(handlers, ExchangeOptions.defaults());
        try {
            var address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            try (RawPeer peer = RawPeer.connect(address)) {
                peer.send(RawPeer.bind(9, 1, 0x50));
                peer.read();
                peer.send(HexFormat.of().parseHex("0000001a000001030000000000000002" + "00".repeat(10)));
                byte[] response = peer.read();
                assertEquals(
                        8,
                        RawPeer.status(response),
                        "Transaction-only diagnostics cannot acknowledge a nontransaction request");
                assertEquals(16, response.length);
            }
        } finally {
            server.close();
            assertTrue(server.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
        }
    }

    @Test
    void replyBytesIncludeCompletedDecisionsWaitingBehindAnEarlierRequest() throws Exception {
        CompletableFuture<HandlerResponse<SubmitSmResponse>> first = new CompletableFuture<>();
        CompletableFuture<HandlerResponse<SubmitSmResponse>> second = new CompletableFuture<>();
        CountDownLatch secondInvoked = new CountDownLatch(1);
        EndpointHandlers handlers = EndpointHandlers.builder()
                .on(MessageOperations.SUBMIT_SM, request -> {
                    if (request.pdu().sequenceNumber() == 2) return first;
                    secondInvoked.countDown();
                    return second;
                })
                .build();
        SmppServer server = server(handlers, new ExchangeOptions(2, 2, 3, 98, Duration.ofSeconds(2)));
        try {
            var address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            try (RawPeer peer = RawPeer.connect(address)) {
                peer.send(RawPeer.bind(9, 1, 0x50));
                peer.read();
                peer.send(submit(2));
                peer.send(submit(3));
                assertTrue(secondInvoked.await(2, TimeUnit.SECONDS));
                long registrationBound = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (second.getNumberOfDependents() == 0 && System.nanoTime() - registrationBound < 0)
                    Thread.onSpinWait();
                assertTrue(second.getNumberOfDependents() > 0);
                second.complete(accepted("too-large"));
                peer.send(RawPeer.header(0x15, 0, 4));
                assertEquals(0x80000015L, RawPeer.command(peer.read()));
                first.complete(accepted("also-too-large"));
                byte[] response = peer.read();
                assertEquals(2, RawPeer.sequence(response));
                assertEquals(
                        8, RawPeer.status(response), "Buffered response budget must include both retained requests");
                response = peer.read();
                assertEquals(3, RawPeer.sequence(response));
                assertEquals(8, RawPeer.status(response), "Completed response must have been bounded while waiting");
            }
        } finally {
            first.complete(accepted("cleanup"));
            second.complete(accepted("cleanup"));
            server.close();
            assertTrue(server.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
        }
    }

    @Test
    void delayedDecisionPreservesReplyOrderWhileControlTrafficProgresses() throws Exception {
        CompletableFuture<HandlerResponse<SubmitSmResponse>> first = new CompletableFuture<>();
        CountDownLatch secondInvoked = new CountDownLatch(1);
        EndpointHandlers handlers = EndpointHandlers.builder()
                .on(MessageOperations.SUBMIT_SM, request -> {
                    if (request.pdu().sequenceNumber() == 2) return first;
                    secondInvoked.countDown();
                    return CompletableFuture.completedFuture(accepted("second"));
                })
                .build();
        SmppServer server = server(handlers, ExchangeOptions.defaults());
        try {
            var address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            try (RawPeer peer = RawPeer.connect(address)) {
                peer.send(RawPeer.bind(9, 1, 0x50));
                peer.read();
                peer.send(submit(2));
                peer.send(submit(3));
                assertTrue(secondInvoked.await(2, TimeUnit.SECONDS));
                peer.send(RawPeer.header(0x15, 0, 4));
                assertEquals(
                        0x80000015L,
                        RawPeer.command(peer.read()),
                        "Control reply must progress ahead of ordered application replies");
                first.complete(accepted("first"));
                assertEquals(2, RawPeer.sequence(peer.read()));
                assertEquals(3, RawPeer.sequence(peer.read()));
            }
        } finally {
            first.complete(accepted("first"));
            server.close();
            assertTrue(server.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
        }
    }

    private static HandlerResponse<SubmitSmResponse> accepted(String id) {
        return new HandlerResponse<>(
                0, new SubmitSmResponse(new MessageResponse(Optional.of(id), new OptionalParameters(List.of()))));
    }

    private static byte[] submit(int sequence) {
        byte[] frame = HexFormat.of().parseHex("00000021000000040000000000000000" + "00".repeat(17));
        ByteBuffer.wrap(frame).putInt(12, sequence);
        return frame;
    }

    private static SmppServer server(EndpointHandlers handlers, ExchangeOptions options) {
        return new SmppServer(
                new ServerConfig(
                        new InetSocketAddress("127.0.0.1", 0),
                        Set.of(SmppVersion.V5_0),
                        SmppVersion.V5_0,
                        "demo",
                        1,
                        1),
                EndpointOptions.defaults(),
                (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                ignored -> {},
                null,
                new ExchangeConfig(options, handlers));
    }

    @Test
    void registeredTypedApplicationAcceptanceDeterminesTheSubmissionResponse() throws Exception {
        AtomicInteger invoked = new AtomicInteger();
        EndpointHandlers handlers = EndpointHandlers.builder()
                .on(MessageOperations.SUBMIT_SM, request -> {
                    invoked.incrementAndGet();
                    assertEquals(3, request.pdu().sequenceNumber());
                    assertTrue(!request.isCancelled());
                    return CompletableFuture.completedFuture(new HandlerResponse<>(
                            0,
                            new SubmitSmResponse(
                                    new MessageResponse(Optional.of("stored"), new OptionalParameters(List.of())))));
                })
                .build();
        SmppServer server = new SmppServer(
                new ServerConfig(
                        new InetSocketAddress("127.0.0.1", 0),
                        Set.of(SmppVersion.V5_0),
                        SmppVersion.V5_0,
                        "demo",
                        1,
                        1),
                EndpointOptions.defaults(),
                (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                ignored -> {},
                null,
                new ExchangeConfig(ExchangeOptions.defaults(), handlers));
        try {
            var address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            try (RawPeer peer = RawPeer.connect(address)) {
                peer.send(RawPeer.bind(9, 1, 0x50));
                assertEquals(0, RawPeer.status(peer.read()));
                peer.send(HexFormat.of().parseHex("00000021000000040000000000000003" + "00".repeat(17)));
                byte[] response = peer.read();
                assertEquals(0, RawPeer.status(response), "Application acceptance must determine the paired response");
                assertEquals(
                        "0000001780000004000000000000000373746f72656400",
                        HexFormat.of().formatHex(response));
                assertEquals(1, invoked.get());
            }
        } finally {
            server.close();
            assertTrue(server.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
        }
    }

    @Test
    void typedSubmissionUsesTheExistingWireWindowAndPreservesTheTypedPeerResponse() throws Exception {
        SmppClient client = new SmppClient();
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var binding = client.connect(new ClientConfig(
                    (InetSocketAddress) listener.getLocalSocketAddress(),
                    new BindRequest(BindMode.TRANSMITTER, "", "", "", 0x34, 0, 0, ""),
                    true));
            try (RawPeer peer = RawPeer.accept(listener)) {
                peer.send(RawPeer.bindResponse(0x80000002L, 0, RawPeer.sequence(peer.read()), 0x34));
                BoundSession session = binding.toCompletableFuture().get(2, TimeUnit.SECONDS);
                assertTrue(
                        session.submission().isPresent(),
                        "Bound transmitter must expose its implemented submission capability");
                SubmitSm message = (SubmitSm) new PduCodec(
                                MessageCommandCodecs.all(MessageDirection.SUBMISSION), new PduLimits(1024, 128, 8))
                        .decode(
                                HexFormat.of().parseHex("00000021000000040000000000000001" + "00".repeat(17)),
                                ProtocolProfile.forVersion(SmppVersion.V3_4))
                        .command();
                var sent = session.submission().orElseThrow().send(message);
                byte[] request = peer.read();
                assertEquals(4, RawPeer.command(request));
                assertEquals(2, RawPeer.sequence(request));
                peer.send(HexFormat.of().parseHex("00000013800000040000000000000002696400"));
                assertEquals(
                        "id",
                        sent.result()
                                .toCompletableFuture()
                                .get(2, TimeUnit.SECONDS)
                                .command()
                                .fields()
                                .messageId()
                                .orElseThrow());
            }
        } finally {
            client.close();
            assertTrue(client.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
        }
    }
}
