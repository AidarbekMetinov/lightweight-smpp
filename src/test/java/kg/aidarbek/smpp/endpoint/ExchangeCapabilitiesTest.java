package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.DataSm;
import kg.aidarbek.smpp.protocol.DataSmResponse;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Tlv;
import kg.aidarbek.smpp.request.RequestOptions;
import kg.aidarbek.smpp.session.SendRequirements;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Actual fields must obey negotiated restrictions even when the caller requests COMMON sends. */
class ExchangeCapabilitiesTest {
    @ParameterizedTest
    @EnumSource(SmppVersion.class)
    void missingAdvertisementRestrictsActualRequestAndHandlerResponseFeaturesBeforeWireAdmission(SmppVersion requested)
            throws Exception {
        EndpointHandlers handlers = EndpointHandlers.builder()
                .on(
                        MessageOperations.DATA_SM,
                        incoming -> CompletableFuture.completedFuture(new HandlerResponse<>(
                                0,
                                new DataSmResponse(new MessageResponse(
                                        Optional.of("id"),
                                        new OptionalParameters(List.of(new Tlv(0x1401, new byte[] {1}))))),
                                SendRequirements.COMMON)))
                .build();
        SmppClient client =
                new SmppClient(EndpointOptions.defaults(), new ExchangeConfig(ExchangeOptions.defaults(), handlers));
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var binding = client.connect(new ClientConfig(
                    (InetSocketAddress) listener.getLocalSocketAddress(),
                    new BindRequest(BindMode.TRANSCEIVER, "", "", "", requested.interfaceVersion(), 0, 0, ""),
                    false));
            try (RawPeer peer = RawPeer.accept(listener)) {
                peer.send(RawPeer.bindResponse(0x80000009L, 0, RawPeer.sequence(peer.read()), null));
                BoundSession session = binding.toCompletableFuture().get(2, TimeUnit.SECONDS);
                assertEquals(
                        SmppVersion.V3_4,
                        session.negotiation().effectiveProfile().orElseThrow().version());
                var sender = session.dataMessages().orElseThrow();
                RequestOptions deadline = RequestOptions.timeout(Duration.ofSeconds(2));
                assertThrows(
                        IllegalArgumentException.class,
                        () -> sender.send(ExchangeMatrixTest.data(), deadline, SendRequirements.COMMON));
                assertThrows(
                        IllegalArgumentException.class,
                        () -> sender.send(data(3), deadline, SendRequirements.COMMON),
                        "5.0 registered_delivery flags cannot bypass the effective 3.4 codec");
                var sent = sender.send(data(0), deadline, SendRequirements.COMMON);
                byte[] request = peer.read();
                assertEquals(
                        2,
                        RawPeer.sequence(request),
                        "Rejected actual fields must consume no sequence or network request");
                peer.send(HexFormat.of().parseHex("0000001180000103000000000000000200"));
                assertEquals(
                        0,
                        sent.result()
                                .toCompletableFuture()
                                .get(2, TimeUnit.SECONDS)
                                .commandStatus());
                peer.send(HexFormat.of().parseHex("0000001a000001030000000000000007" + "00".repeat(10)));
                byte[] response = peer.read();
                assertEquals(8, RawPeer.status(response), "Actual handler-response TLVs need negotiated support too");
                assertEquals(16, response.length);
                var unbind = session.unbind();
                byte[] closing = peer.read();
                peer.send(RawPeer.header(0x80000006L, 0, RawPeer.sequence(closing)));
                unbind.result().toCompletableFuture().get(2, TimeUnit.SECONDS);
                assertThrows(IllegalStateException.class, () -> sender.send(data(0)));
                assertTrue(session.dataMessages().isEmpty());
            }
        } finally {
            client.close();
            assertTrue(client.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
        }
    }

    private static DataSm data(int registeredDelivery) {
        return new DataSm(
                "", new Address(0, 0, ""), new Address(0, 0, ""), 0, registeredDelivery, 0, EndpointPdus.NO_PARAMETERS);
    }
}
