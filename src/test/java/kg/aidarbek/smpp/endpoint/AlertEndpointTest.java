package kg.aidarbek.smpp.endpoint;

import static kg.aidarbek.smpp.endpoint.CommonOperationsEndpointTest.DESTINATION;
import static kg.aidarbek.smpp.endpoint.CommonOperationsEndpointTest.EMPTY;
import static kg.aidarbek.smpp.endpoint.CommonOperationsEndpointTest.SOURCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.AlertNotification;
import kg.aidarbek.smpp.protocol.BindMode;
import org.junit.jupiter.api.Test;

class AlertEndpointTest {
    @Test
    void alertUsesTypedOffIoHandlerAndLocalWriteCompletionWithoutAResponseWindow() throws Exception {
        for (SmppVersion version : SmppVersion.values()) {
            CompletableFuture<IncomingNotification<AlertNotification>> received = new CompletableFuture<>();
            CompletableFuture<String> handlerThread = new CompletableFuture<>();
            EndpointHandlers handlers = EndpointHandlers.builder()
                    .onAlert(notification -> {
                        handlerThread.complete(Thread.currentThread().getName());
                        received.complete(notification);
                        return CompletableFuture.completedFuture(null);
                    })
                    .build();
            CompletableFuture<BoundSession> serverSession = new CompletableFuture<>();
            try (SmppServer server =
                            CommonOperationsEndpointTest.server(version, EndpointHandlers.empty(), serverSession);
                    SmppClient client = new SmppClient(
                            EndpointOptions.defaults(), new ExchangeConfig(ExchangeOptions.defaults(), handlers))) {
                BoundSession esme = CommonOperationsEndpointTest.bind(client, server, version, BindMode.RECEIVER);
                BoundSession mc = serverSession.get(2, TimeUnit.SECONDS);
                assertTrue(esme.alerts().isEmpty());
                AlertNotification command = new AlertNotification(SOURCE, DESTINATION, EMPTY);
                NotificationSend sent = mc.alerts().orElseThrow().send(command);
                sent.result().toCompletableFuture().get(2, TimeUnit.SECONDS);
                assertEquals(command, received.get(2, TimeUnit.SECONDS).pdu().command());
                assertEquals(sent.sequenceNumber(), received.get().pdu().sequenceNumber());
                assertTrue(handlerThread.get().startsWith("smpp-handler-"));
                assertFalse(sent.cancel());
                var enquiry = mc.enquireLink();
                assertTrue(enquiry.identity().sequenceNumber() > sent.sequenceNumber());
                assertEquals(
                        0,
                        enquiry.result()
                                .toCompletableFuture()
                                .get(2, TimeUnit.SECONDS)
                                .commandStatus());
            }
        }
    }

    @Test
    void ordinaryPeerReceivesOnlyAnAlertAndNoInventedResponseIsNeeded() throws Exception {
        CompletableFuture<BoundSession> bound = new CompletableFuture<>();
        try (SmppServer server =
                CommonOperationsEndpointTest.server(SmppVersion.V3_4, EndpointHandlers.empty(), bound)) {
            InetSocketAddress address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            try (RawPeer peer = RawPeer.connect(address)) {
                peer.send(RawPeer.bind(1, 7, 0x34));
                assertEquals(0x80000001L, RawPeer.command(peer.read()));
                BoundSession mc = bound.get(2, TimeUnit.SECONDS);
                NotificationSend sent =
                        mc.alerts().orElseThrow().send(new AlertNotification(SOURCE, DESTINATION, EMPTY));
                byte[] frame = peer.read();
                assertEquals(0x102, RawPeer.command(frame));
                assertEquals(sent.sequenceNumber(), RawPeer.sequence(frame));
                sent.result().toCompletableFuture().get(2, TimeUnit.SECONDS);
                peer.timeout(100);
                assertThrows(SocketTimeoutException.class, peer::read);
            }
        }
    }
}
