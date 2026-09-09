package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.DeliverSmResponse;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.request.BoundedNotifications;
import org.junit.jupiter.api.Test;

class SessionResourcesTest {
    @Test
    void observationsRetainRequestAndReplyOwnershipThroughActiveWrites() throws Exception {
        BoundedNotifications notifications = new BoundedNotifications(16, 1);
        HandlerDispatcher handlers = new HandlerDispatcher(1, 1);
        FakeFrameTransport port = new FakeFrameTransport();
        CompletableFuture<HandlerResponse<DeliverSmResponse>> decision = new CompletableFuture<>();
        CountDownLatch entered = new CountDownLatch(1);
        HandlerResponse<DeliverSmResponse> accepted = new HandlerResponse<>(
                0, new DeliverSmResponse(new MessageResponse(Optional.of(""), EndpointPdus.NO_PARAMETERS)));
        EndpointConnection connection = EndpointConnection.client(
                port,
                TlsEndpointsTest.config(new InetSocketAddress("127.0.0.1", 1), BindMode.TRANSCEIVER, SmppVersion.V3_4),
                EndpointOptions.defaults(),
                notifications,
                handlers,
                new ExchangeConfig(
                        ExchangeOptions.defaults(),
                        EndpointHandlers.builder()
                                .on(MessageOperations.DELIVER_SM, incoming -> {
                                    entered.countDown();
                                    return decision;
                                })
                                .build()));
        try {
            connection.start();
            port.writes.remove();
            port.receive(RawPeer.bindResponse(0x80000009L, 0, 1, 0x34));
            BoundSession session = connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(new SessionResources(0, 0, 0, 0), session.resources());
            var request = session.enquireLink();
            byte[] enquiry = port.writes.remove();
            assertEquals(1, session.resources().pendingRequests());
            assertEquals(16, session.resources().pendingRequestBytes());
            port.deferWrites = true;
            port.receive(HexFormat.of().parseHex("00000021000000050000000000000002" + "00".repeat(17)));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            SessionResources waiting = session.resources();
            assertEquals(1, waiting.pendingReplies());
            assertTrue(waiting.retainedReplyBytes() >= 49);
            decision.complete(accepted);
            assertEquals(
                    1,
                    session.resources().pendingReplies(),
                    "A queued/active response write retains its reply reservation");
            port.deferred.remove().send();
            assertEquals(0, session.resources().pendingReplies());
            assertEquals(0, session.resources().retainedReplyBytes());
            port.receive(RawPeer.header(0x80000015L, 0, RawPeer.sequence(enquiry)));
            request.result().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(new SessionResources(0, 0, 0, 0), session.resources());
            assertEquals(1, waiting.pendingReplies(), "Earlier samples remain immutable");
        } finally {
            decision.complete(accepted);
            connection.close();
            handlers.close();
            notifications.close();
            assertTrue(handlers.awaitTermination(Duration.ofSeconds(2)));
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
        }
    }
}
