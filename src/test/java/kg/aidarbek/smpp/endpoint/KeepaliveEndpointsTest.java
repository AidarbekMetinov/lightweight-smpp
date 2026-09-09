package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.Outbind;
import kg.aidarbek.smpp.request.RequestFailure;
import kg.aidarbek.smpp.session.SessionState;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KeepaliveEndpointsTest {
    @ParameterizedTest
    @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void everyOwnerSchedulesEnquiriesAndClosesAnUnresponsiveLink(boolean outgoing, boolean reversed) throws Exception {
        try (Pair pair = new Pair(outgoing, reversed)) {
            byte[] first = pair.peer.read();
            assertEquals(0x15, RawPeer.command(first));
            pair.peer.send(RawPeer.header(0x80000015L, 0, RawPeer.sequence(first)));
            byte[] second = pair.peer.read();
            assertEquals(0x15, RawPeer.command(second));
            assertTrue(pair.peer.closedByEndpoint());
            pair.session.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(SessionState.CLOSED, pair.session.state());
            RequestFailure failure = (RequestFailure) pair.session.closeReason().orElseThrow();
            assertEquals(RequestFailure.Reason.DEADLINE_EXPIRED, failure.reason());
            assertEquals(RawPeer.sequence(second), failure.sequenceNumber());
        }
    }

    static final class Pair implements AutoCloseable {
        AutoCloseable owner;
        CompletionStage<EndpointTermination> termination;
        ServerSocket listener;
        RawPeer peer;
        BoundSession session;

        Pair(boolean outgoing, boolean reversed) throws Exception {
            ConnectionLifecycle lifecycle = ConnectionLifecycle.defaults()
                    .withKeepalive(new KeepalivePolicy(Duration.ofMillis(100), Duration.ofMillis(200)));
            CompletableFuture<BoundSession> accepted = new CompletableFuture<>();
            BindRequest bind = new BindRequest(BindMode.RECEIVER, "demo", "demo", "", 0x34, 0, 0, "");
            try {
                if (outgoing) {
                    listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
                    InetSocketAddress address = (InetSocketAddress) listener.getLocalSocketAddress();
                    CompletionStage<BoundSession> binding;
                    if (reversed) {
                        OutbindConnector connector = new OutbindConnector(
                                new OutbindConnectorConfig(Set.of(SmppVersion.V3_4), SmppVersion.V3_4, "mc", 1, 1),
                                EndpointOptions.defaults(),
                                (request, remote) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                                ExchangeConfig.defaults(),
                                lifecycle);
                        owner = connector;
                        termination = connector.termination();
                        binding = connector
                                .connectAttempt(address, new Outbind("mc", "demo", CommonOperationsEndpointTest.EMPTY))
                                .result();
                    } else {
                        SmppClient client =
                                new SmppClient(EndpointOptions.defaults(), ExchangeConfig.defaults(), lifecycle);
                        owner = client;
                        termination = client.termination();
                        binding = client.connect(new ClientConfig(address, bind, true));
                    }
                    peer = RawPeer.accept(listener);
                    byte[] request = peer.read();
                    if (reversed) {
                        assertEquals(0x0b, RawPeer.command(request));
                        peer.send(RawPeer.bind(1, 17, 0x34));
                        assertEquals(0x80000001L, RawPeer.command(peer.read()));
                    } else peer.send(RawPeer.bindResponse(0x80000001L, 0, RawPeer.sequence(request), 0x34));
                    session = binding.toCompletableFuture().get(2, TimeUnit.SECONDS);
                } else {
                    InetSocketAddress address;
                    if (reversed) {
                        OutbindListener endpoint = new OutbindListener(
                                new OutbindListenerConfig(new InetSocketAddress("127.0.0.1", 0), bind, true),
                                EndpointOptions.defaults(),
                                (request, remote) -> CompletableFuture.completedFuture(true),
                                accepted::complete,
                                ExchangeConfig.defaults(),
                                lifecycle);
                        owner = endpoint;
                        termination = endpoint.termination();
                        address = endpoint.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
                    } else {
                        SmppServer server = new SmppServer(
                                new ServerConfig(
                                        new InetSocketAddress("127.0.0.1", 0),
                                        Set.of(SmppVersion.V3_4),
                                        SmppVersion.V3_4,
                                        "mc",
                                        1,
                                        1),
                                EndpointOptions.defaults(),
                                (request, remote) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                                accepted::complete,
                                null,
                                ExchangeConfig.defaults(),
                                lifecycle);
                        owner = server;
                        termination = server.termination();
                        address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
                    }
                    peer = RawPeer.connect(address);
                    if (reversed) {
                        peer.send(HexFormat.of().parseHex("000000160000000b00000000000000016d6300707700"));
                        byte[] request = peer.read();
                        peer.send(RawPeer.bindResponse(0x80000001L, 0, RawPeer.sequence(request), 0x34));
                    } else {
                        peer.send(RawPeer.bind(1, 17, 0x34));
                        assertEquals(0x80000001L, RawPeer.command(peer.read()));
                    }
                    session = accepted.get(2, TimeUnit.SECONDS);
                }
            } catch (Exception | Error failure) {
                close();
                throw failure;
            }
        }

        @Override
        public void close() {
            try {
                try {
                    if (peer != null) peer.close();
                } finally {
                    try {
                        if (listener != null) listener.close();
                    } finally {
                        if (owner != null) owner.close();
                    }
                }
                if (termination != null)
                    assertTrue(termination
                            .toCompletableFuture()
                            .get(3, TimeUnit.SECONDS)
                            .complete());
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
        }
    }
}
