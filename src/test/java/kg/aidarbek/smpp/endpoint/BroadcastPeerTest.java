package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.BroadcastSm;
import kg.aidarbek.smpp.protocol.BroadcastSmResponse;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.QueryBroadcastSmResponse;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

/** Independent raw peers exercise malformed, absent, version and role paths beyond library loopback. */
class BroadcastPeerTest {
    private static final String BODY =
            "43425300010131323300000000000004000606000200410601000300001006040002000106050003090001";

    @Test
    void unknownParametersReachTheHandlerAndMalformedKnownValuesCloseAfterANack() throws Exception {
        CompletableFuture<BroadcastSm> received = new CompletableFuture<>();
        EndpointHandlers handlers = EndpointHandlers.builder()
                .on(BroadcastOperations.BROADCAST_SM, request -> {
                    received.complete(request.pdu().command());
                    return CompletableFuture.completedFuture(new HandlerResponse<>(
                            0,
                            new BroadcastSmResponse(
                                    new MessageResponse(Optional.of("Id"), BroadcastEndpointTest.EMPTY))));
                })
                .build();
        SmppServer server = CommonOperationsEndpointTest.server(SmppVersion.V5_0, handlers, new CompletableFuture<>());
        try (server) {
            InetSocketAddress address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            try (RawPeer peer = RawPeer.connect(address)) {
                peer.send(RawPeer.bind(2, 1, 0x50));
                assertEquals(0, RawPeer.status(peer.read()));
                peer.send(EndpointCongestionTest.frame(0x111, 0, 2, BODY + "15000001ff060b0000"));
                assertArrayEquals(EndpointCongestionTest.frame(0x80000111L, 0, 2, "496400"), peer.read());
                List<Tlv> tags =
                        received.get(2, TimeUnit.SECONDS).optionalParameters().entries();
                assertEquals(new Tlv(0x1500, new byte[] {(byte) 255}), tags.get(tags.size() - 2));
                assertEquals(new Tlv(0x060b, new byte[0]), tags.getLast());
                peer.send(EndpointCongestionTest.frame(0x111, 0, 3, BODY + "060000020000"));
                assertArrayEquals(RawPeer.header(0x80000000L, 2, 3), peer.read());
                assertTrue(peer.closedByEndpoint());
            }
        }
        assertTrue(server.termination()
                .toCompletableFuture()
                .get(2, TimeUnit.SECONDS)
                .complete());
    }

    @Test
    void rawUnsupportedVersionAndWrongBindModeDoNotInvokeAnOptionalBroadcastService() throws Exception {
        for (SmppVersion version : SmppVersion.values()) {
            SmppServer server =
                    CommonOperationsEndpointTest.server(version, EndpointHandlers.empty(), new CompletableFuture<>());
            try (server) {
                InetSocketAddress address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
                try (RawPeer peer = RawPeer.connect(address)) {
                    peer.send(RawPeer.bind(1, 1, version.interfaceVersion()));
                    assertEquals(0, RawPeer.status(peer.read()));
                    peer.send(EndpointCongestionTest.frame(0x111, 0, 2, BODY));
                    assertArrayEquals(RawPeer.header(0x80000000L, version == SmppVersion.V3_4 ? 3 : 4, 2), peer.read());
                    peer.send(RawPeer.header(0x15, 0, 3));
                    assertArrayEquals(RawPeer.header(0x80000015L, 0, 3), peer.read());
                }
            }
            assertTrue(server.termination()
                    .toCompletableFuture()
                    .get(2, TimeUnit.SECONDS)
                    .complete());
        }
        try (ServerSocket listener = new ServerSocket(0);
                SmppClient client = new SmppClient()) {
            var connecting = client.connect(new ClientConfig(
                    new InetSocketAddress("127.0.0.1", listener.getLocalPort()),
                    new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x50, 0, 0, ""),
                    true));
            try (RawPeer peer = RawPeer.accept(listener)) {
                byte[] bind = peer.read();
                peer.send(RawPeer.bindResponse(0x80000009L, 0, RawPeer.sequence(bind), 0x50));
                connecting.toCompletableFuture().get(2, TimeUnit.SECONDS);
                peer.send(EndpointCongestionTest.frame(0x111, 0, 22, BODY));
                assertArrayEquals(RawPeer.header(0x80000000L, 4, 22), peer.read());
            }
        }
    }

    @Test
    void invalidApplicationAreaAndQueryResultsBecomeCanonicalFailureReplies() throws Exception {
        EndpointHandlers handlers = EndpointHandlers.builder()
                .on(
                        BroadcastOperations.BROADCAST_SM,
                        request -> CompletableFuture.completedFuture(new HandlerResponse<>(
                                0,
                                new BroadcastSmResponse(new MessageResponse(
                                        Optional.of("ID"),
                                        new OptionalParameters(List.of(new Tlv(0x0606, new byte[] {0, 90}))))))))
                .on(
                        BroadcastOperations.QUERY_BROADCAST_SM,
                        request -> CompletableFuture.completedFuture(new HandlerResponse<>(
                                0,
                                new QueryBroadcastSmResponse(new MessageResponse(
                                        Optional.of("wrong"),
                                        new OptionalParameters(List.of(
                                                new Tlv(0x0427, new byte[] {1}),
                                                new Tlv(0x0606, new byte[] {0, 65}),
                                                new Tlv(0x0608, new byte[] {100}))))))))
                .build();
        SmppServer server = CommonOperationsEndpointTest.server(SmppVersion.V5_0, handlers, new CompletableFuture<>());
        try (server) {
            InetSocketAddress address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            try (RawPeer peer = RawPeer.connect(address)) {
                peer.send(RawPeer.bind(2, 1, 0x50));
                peer.read();
                peer.send(EndpointCongestionTest.frame(0x111, 0, 2, BODY));
                assertArrayEquals(RawPeer.header(0x80000111L, 8, 2), peer.read());
                peer.send(EndpointCongestionTest.frame(0x112, 0, 3, "494400000000"));
                assertArrayEquals(RawPeer.header(0x80000112L, 8, 3), peer.read());
                peer.send(RawPeer.header(0x15, 0, 4));
                assertArrayEquals(RawPeer.header(0x80000015L, 0, 4), peer.read());
            }
        }
        assertTrue(server.termination()
                .toCompletableFuture()
                .get(2, TimeUnit.SECONDS)
                .complete());
    }
}
