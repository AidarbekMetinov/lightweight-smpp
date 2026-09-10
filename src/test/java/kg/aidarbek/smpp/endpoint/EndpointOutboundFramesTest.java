package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.AlertNotification;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.ShortMessage;
import kg.aidarbek.smpp.protocol.SubmitSm;
import kg.aidarbek.smpp.request.BoundedNotifications;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Independent complete frames characterize outgoing identity, preflight rejection and payload ownership. */
class EndpointOutboundFramesTest {
    @ParameterizedTest
    @EnumSource(SmppVersion.class)
    void controlAndMessagesUseAssignedSequencesWithoutChangingValidatedBodies(SmppVersion version) throws Exception {
        BoundedNotifications notifications = new BoundedNotifications(16, 1);
        FakeFrameTransport transport = new FakeFrameTransport();
        EndpointConnection connection = EndpointConnection.client(
                transport,
                new ClientConfig(
                        new InetSocketAddress("127.0.0.1", 1),
                        new BindRequest(BindMode.TRANSCEIVER, "", "", "", version.interfaceVersion(), 0, 0, ""),
                        true),
                EndpointOptions.defaults(),
                notifications);
        try {
            connection.start();
            byte[] bind = transport.writes.remove();
            assertArrayEquals(
                    hex("00000017000000090000000000000001000000" + (version == SmppVersion.V3_4 ? "34" : "50")
                            + "000000"),
                    bind);
            transport.receive(RawPeer.bindResponse(0x80000009L, 0, 1, version.interfaceVersion()));
            BoundSession session = connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS);

            var enquiry = session.enquireLink();
            assertArrayEquals(hex("00000010000000150000000000000002"), transport.writes.remove());
            transport.receive(RawPeer.header(0x80000015L, 0, 2));
            enquiry.result().toCompletableFuture().get(2, TimeUnit.SECONDS);

            byte[] callerBytes = {1, 2, 3};
            SubmitSm message = submission(new OctetString(callerBytes), 0);
            Arrays.fill(callerBytes, (byte) 9);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> session.submission().orElseThrow().send(submission(new OctetString(new byte[0]), 255)));
            assertTrue(transport.writes.isEmpty());

            var first = session.submission().orElseThrow().send(message);
            var second = session.submission().orElseThrow().send(message);
            String body = "00" + "00007300" + "00006400" + "000000" + "0000" + "0000" + "040003" + "010203";
            assertArrayEquals(hex("00000026000000040000000000000003" + body), transport.writes.remove());
            assertArrayEquals(hex("00000026000000040000000000000004" + body), transport.writes.remove());
            assertEquals(3, first.identity().sequenceNumber());
            assertEquals(4, second.identity().sequenceNumber());
            assertArrayEquals(
                    new byte[] {1, 2, 3}, message.fields().shortMessage().value());
            first.cancel();
            second.cancel();
            var unbind = session.unbind();
            assertArrayEquals(hex("00000010000000060000000000000005"), transport.writes.remove());
            transport.receive(RawPeer.header(0x80000006L, 0, 5));
            unbind.result().toCompletableFuture().get(2, TimeUnit.SECONDS);
        } finally {
            connection.close();
            notifications.close();
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @ParameterizedTest
    @EnumSource(SmppVersion.class)
    void alertsAndControlsShareOutgoingSequencesWithoutChangingAddressBytes(SmppVersion version) throws Exception {
        BoundedNotifications notifications = new BoundedNotifications(16, 1);
        AuthenticationDispatcher authentication = new AuthenticationDispatcher(1, 1, null);
        FakeFrameTransport transport = new FakeFrameTransport();
        CompletableFuture<BoundSession> bound = new CompletableFuture<>();
        EndpointConnection connection = EndpointConnection.server(
                transport,
                new InetSocketAddress("127.0.0.1", 1),
                new ServerConfig(new InetSocketAddress("127.0.0.1", 0), Set.of(version), version, "mc", 1, 1),
                EndpointOptions.defaults(),
                notifications,
                authentication,
                (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                bound::complete);
        try {
            connection.start();
            transport.receive(RawPeer.bind(1, 7, version.interfaceVersion()));
            BoundSession session = bound.get(2, TimeUnit.SECONDS);
            assertEquals(0x80000001L, RawPeer.command(transport.writes.remove()));
            AlertNotification command = new AlertNotification(
                    new Address(0, 0, "s"), new Address(0, 0, "d"), new OptionalParameters(List.of()));
            var first = session.alerts().orElseThrow().send(command);
            assertArrayEquals(hex("000000180000010200000000000000010000730000006400"), transport.writes.remove());
            first.result().toCompletableFuture().get(2, TimeUnit.SECONDS);
            var enquiry = session.enquireLink();
            assertArrayEquals(hex("00000010000000150000000000000002"), transport.writes.remove());
            transport.receive(RawPeer.header(0x80000015L, 0, 2));
            enquiry.result().toCompletableFuture().get(2, TimeUnit.SECONDS);
            var second = session.alerts().orElseThrow().send(command);
            assertArrayEquals(hex("000000180000010200000000000000030000730000006400"), transport.writes.remove());
            second.result().toCompletableFuture().get(2, TimeUnit.SECONDS);
        } finally {
            connection.close();
            authentication.close();
            notifications.close();
            assertTrue(authentication.awaitTermination(Duration.ofSeconds(2)));
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    private static SubmitSm submission(OctetString bytes, int priority) {
        return new SubmitSm(new ShortMessage(
                "",
                new Address(0, 0, "s"),
                new Address(0, 0, "d"),
                0,
                0,
                priority,
                "",
                "",
                0,
                0,
                4,
                0,
                bytes,
                new OptionalParameters(List.of())));
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }
}
