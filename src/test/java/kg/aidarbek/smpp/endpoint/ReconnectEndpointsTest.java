package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.Outbind;
import org.junit.jupiter.api.Test;

class ReconnectEndpointsTest {
    @Test
    void explicitReconnectPublishesFreshGenerationsAndCancellationStopsTheSequence() throws Exception {
        SmppClient client = new SmppClient();
        LinkedBlockingQueue<BoundSession> sessions = new LinkedBlockingQueue<>(2);
        try (ServerSocket listener = new ServerSocket(0, 2, InetAddress.getLoopbackAddress());
                ReconnectHandle reconnect = client.reconnect(
                        TlsEndpointsTest.config(
                                (InetSocketAddress) listener.getLocalSocketAddress(),
                                BindMode.TRANSCEIVER,
                                SmppVersion.V3_4),
                        new ReconnectPolicy(3, Duration.ofMillis(25)),
                        session -> {
                            assertTrue(Thread.currentThread().getName().startsWith("smpp-notification-"));
                            sessions.add(session);
                        })) {
            BoundSession first;
            try (RawPeer peer = RawPeer.accept(listener)) {
                byte[] request = peer.read();
                assertEquals(1, RawPeer.sequence(request));
                peer.send(RawPeer.bindResponse(0x80000009L, 0, RawPeer.sequence(request), 0x34));
                first = sessions.poll(2, TimeUnit.SECONDS);
                org.junit.jupiter.api.Assertions.assertNotNull(first);
            }
            try (RawPeer peer = RawPeer.accept(listener)) {
                byte[] request = peer.read();
                assertEquals(1, RawPeer.sequence(request));
                peer.send(RawPeer.bindResponse(0x80000009L, 0, RawPeer.sequence(request), 0x34));
                BoundSession second = sessions.poll(2, TimeUnit.SECONDS);
                org.junit.jupiter.api.Assertions.assertNotNull(second);
                assertNotEquals(first.id(), second.id());
                assertEquals(
                        second.id(), reconnect.currentSession().orElseThrow().id());
                assertTrue(reconnect.cancel());
                assertFalse(reconnect.cancel());
                assertTrue(peer.closedByEndpoint());
                ReconnectResult result =
                        reconnect.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
                assertEquals(ReconnectResult.Reason.CANCELLED, result.reason());
                assertEquals(2, result.attempts());
                assertEquals(2, result.publishedSessions());
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
    void explicitOutbindReconnectRepeatsOnlyConnectionAuthenticationOnFreshSockets() throws Exception {
        OutbindConnector connector = new OutbindConnector(
                new OutbindConnectorConfig(Set.of(SmppVersion.V3_4), SmppVersion.V3_4, "mc", 1, 1),
                EndpointOptions.defaults(),
                (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                ExchangeConfig.defaults());
        LinkedBlockingQueue<BoundSession> sessions = new LinkedBlockingQueue<>(2);
        try (ServerSocket listener = new ServerSocket(0, 2, InetAddress.getLoopbackAddress());
                ReconnectHandle handle = connector.reconnect(
                        (InetSocketAddress) listener.getLocalSocketAddress(),
                        new Outbind("mc", "demo", CommonOperationsEndpointTest.EMPTY),
                        new ReconnectPolicy(2, Duration.ZERO),
                        sessions::add)) {
            java.util.UUID previous = null;
            for (int index = 0; index < 2; index++) {
                try (RawPeer peer = RawPeer.accept(listener)) {
                    byte[] notification = peer.read();
                    assertEquals(0x0b, RawPeer.command(notification));
                    assertEquals(1, RawPeer.sequence(notification));
                    peer.send(RawPeer.bind(1, 17, 0x34));
                    assertEquals(0x80000001L, RawPeer.command(peer.read()));
                    BoundSession bound = sessions.poll(2, TimeUnit.SECONDS);
                    org.junit.jupiter.api.Assertions.assertNotNull(bound);
                    assertNotEquals(previous, bound.id());
                    previous = bound.id();
                }
            }
            ReconnectResult result = handle.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(ReconnectResult.Reason.ATTEMPTS_EXHAUSTED, result.reason());
            assertEquals(2, result.attempts());
            assertEquals(2, result.publishedSessions());
        } finally {
            connector.close();
            assertTrue(connector
                    .termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
        }
    }
}
