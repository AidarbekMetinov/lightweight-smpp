package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.message.ReceiptTlvs;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.DeliverSm;
import kg.aidarbek.smpp.protocol.DeliverSmResponse;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.ShortMessage;
import kg.aidarbek.smpp.protocol.SubmitSm;
import kg.aidarbek.smpp.protocol.SubmitSmResponse;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

/** Executed 5.0 application hooks for receipt options and extended service data, without network policy. */
class Smpp5ServicesTest {
    private static final OptionalParameters EMPTY = new OptionalParameters(List.of());
    private static final Address SOURCE = new Address(1, 1, "source"), DESTINATION = new Address(1, 1, "destination");

    @Test
    void receiptOptionsAndExtendedRoutingDataReachBothTypedApplicationRoles() throws Exception {
        OptionalParameters routing = new OptionalParameters(List.of(
                new Tlv(0x060b, new byte[] {(byte) 128, 1, 2}),
                new Tlv(0x060d, ascii("100101\0")),
                new Tlv(0x060f, ascii("123456")),
                new Tlv(0x060e, ascii("100101\0")),
                new Tlv(0x0610, ascii("654321")),
                new Tlv(0x0611, new byte[] {2}),
                new Tlv(0x0612, ascii("1234567890")),
                new Tlv(0x0613, new byte[] {1})));
        ArrayBlockingQueue<ReceiptTlvs> receipts = new ArrayBlockingQueue<>(3);
        ArrayBlockingQueue<Integer> requested = new ArrayBlockingQueue<>(3);
        EndpointHandlers mcHandlers = EndpointHandlers.builder()
                .on(MessageOperations.SUBMIT_SM, request -> {
                    assertEquals(routing, request.pdu().command().fields().optionalParameters());
                    requested.add(request.pdu().command().fields().registeredDelivery());
                    return CompletableFuture.completedFuture(new HandlerResponse<>(
                            0, new SubmitSmResponse(new MessageResponse(Optional.of("Id-A"), congestion(70)))));
                })
                .build();
        EndpointHandlers esmeHandlers = EndpointHandlers.builder()
                .on(MessageOperations.DELIVER_SM, request -> {
                    receipts.add(
                            ReceiptTlvs.read(request.pdu().command().fields().optionalParameters()));
                    return CompletableFuture.completedFuture(new HandlerResponse<>(
                            0, new DeliverSmResponse(new MessageResponse(Optional.of(""), congestion(30)))));
                })
                .build();
        CompletableFuture<BoundSession> bound = new CompletableFuture<>();
        SmppServer server = CommonOperationsEndpointTest.server(SmppVersion.V5_0, mcHandlers, bound);
        SmppClient client = new SmppClient(
                EndpointOptions.defaults(), new ExchangeConfig(ExchangeOptions.defaults(), esmeHandlers));
        try (server;
                client) {
            BoundSession esme =
                    CommonOperationsEndpointTest.bind(client, server, SmppVersion.V5_0, BindMode.TRANSCEIVER);
            BoundSession mc = bound.get(2, TimeUnit.SECONDS);
            int[] states = {2, 9, 0}, registered = {3, 1, 16}, esm = {4, 4, 32};
            for (int index = 0; index < states.length; index++) {
                int state = states[index];
                var submission = esme.submission()
                        .orElseThrow()
                        .send(new SubmitSm(message(0, registered[index], routing)))
                        .result()
                        .toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                assertEquals(0, submission.commandStatus());
                assertEquals(registered[index], requested.poll(2, TimeUnit.SECONDS));
                assertEquals(70, esme.congestion().orElseThrow().level());
                OptionalParameters receipt = ReceiptTlvs.build(
                        Optional.of("Id-A"), OptionalInt.of(state), Optional.of(new OctetString(new byte[] {8, 0, 1})));
                var delivery = mc.delivery()
                        .orElseThrow()
                        .send(new DeliverSm(message(esm[index], 0, receipt)))
                        .result()
                        .toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                assertEquals(0, delivery.commandStatus());
                assertEquals(30, mc.congestion().orElseThrow().level());
                ReceiptTlvs received = receipts.poll(2, TimeUnit.SECONDS);
                assertNotNull(received);
                assertEquals(Optional.of("Id-A"), received.messageId());
                assertEquals(OptionalInt.of(state), received.messageState());
                assertEquals(receipt, received.parameters());
            }
        }
        assertTrue(client.termination()
                .toCompletableFuture()
                .get(2, TimeUnit.SECONDS)
                .complete());
        assertTrue(server.termination()
                .toCompletableFuture()
                .get(2, TimeUnit.SECONDS)
                .complete());
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static OptionalParameters congestion(int value) {
        return new OptionalParameters(List.of(new Tlv(0x0428, new byte[] {(byte) value})));
    }

    private static ShortMessage message(int esm, int registered, OptionalParameters parameters) {
        return new ShortMessage(
                "",
                SOURCE,
                DESTINATION,
                esm,
                0,
                0,
                "",
                "",
                registered,
                0,
                4,
                0,
                new OctetString(new byte[0]),
                parameters);
    }
}
