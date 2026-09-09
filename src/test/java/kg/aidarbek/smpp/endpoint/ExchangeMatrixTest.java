package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.DataSm;
import kg.aidarbek.smpp.protocol.DataSmResponse;
import kg.aidarbek.smpp.protocol.DeliverSm;
import kg.aidarbek.smpp.protocol.DeliverSmResponse;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.ShortMessage;
import kg.aidarbek.smpp.protocol.SubmitSm;
import kg.aidarbek.smpp.protocol.SubmitSmResponse;
import kg.aidarbek.smpp.protocol.Tlv;
import kg.aidarbek.smpp.request.RequestHandle;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Real paired endpoints exercise the independent 3.4/5.0 role and bind-mode permission matrix. */
class ExchangeMatrixTest {
    @ParameterizedTest(name = "{0} {1}, rejected={2}")
    @MethodSource("exchanges")
    void realEndpointsExchangeOnlyPermittedOperationsAndPreserveApplicationStatus(
            SmppVersion version, BindMode mode, boolean rejected) throws Exception {
        long status = rejected ? 0x400 : 0;
        EndpointHandlers services = EndpointHandlers.builder()
                .on(
                        MessageOperations.SUBMIT_SM,
                        incoming -> CompletableFuture.completedFuture(
                                new HandlerResponse<>(status, new SubmitSmResponse(result(rejected, "accepted")))))
                .on(
                        MessageOperations.DELIVER_SM,
                        incoming -> CompletableFuture.completedFuture(
                                new HandlerResponse<>(status, new DeliverSmResponse(result(rejected, "")))))
                .on(MessageOperations.DATA_SM, incoming -> {
                    assertEquals(
                            List.of(new Tlv(0x0424, new byte[] {1, 2, 3})),
                            incoming.pdu().command().optionalParameters().entries());
                    return CompletableFuture.completedFuture(
                            new HandlerResponse<>(status, new DataSmResponse(result(rejected, "data"))));
                })
                .build();
        ExchangeConfig exchange = new ExchangeConfig(ExchangeOptions.defaults(), services);
        CompletableFuture<BoundSession> accepted = new CompletableFuture<>();
        SmppServer server = new SmppServer(
                new ServerConfig(new InetSocketAddress("127.0.0.1", 0), Set.of(version), version, "mc", 1, 1),
                EndpointOptions.defaults(),
                (bind, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                accepted::complete,
                null,
                exchange);
        SmppClient client = new SmppClient(EndpointOptions.defaults(), exchange);
        try {
            var address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            BoundSession esme = client.connect(new ClientConfig(
                            address, new BindRequest(mode, "", "", "", version.interfaceVersion(), 0, 0, ""), true))
                    .toCompletableFuture()
                    .get(2, TimeUnit.SECONDS);
            BoundSession mc = accepted.get(2, TimeUnit.SECONDS);
            assertEquals(mode != BindMode.RECEIVER, esme.submission().isPresent());
            assertTrue(esme.delivery().isEmpty());
            assertTrue(mc.submission().isEmpty());
            assertEquals(mode != BindMode.TRANSMITTER, mc.delivery().isPresent());
            assertEquals(
                    version == SmppVersion.V3_4 || mode != BindMode.RECEIVER,
                    esme.dataMessages().isPresent());
            assertEquals(
                    version == SmppVersion.V3_4 || mode != BindMode.TRANSMITTER,
                    mc.dataMessages().isPresent());
            mc.enquireLink().result().toCompletableFuture().get(2, TimeUnit.SECONDS);
            List<RequestHandle<?>> requests = new ArrayList<>();
            esme.submission().ifPresent(sender -> requests.add(sender.send(new SubmitSm(shortMessage()))));
            mc.delivery().ifPresent(sender -> requests.add(sender.send(new DeliverSm(shortMessage()))));
            esme.dataMessages().ifPresent(sender -> requests.add(sender.send(data())));
            mc.dataMessages().ifPresent(sender -> requests.add(sender.send(data())));
            for (RequestHandle<?> request : requests)
                assertEquals(
                        status,
                        request.result()
                                .toCompletableFuture()
                                .get(2, TimeUnit.SECONDS)
                                .commandStatus());
            if (mode == BindMode.TRANSCEIVER) {
                assertEquals(2, requests.get(0).identity().sequenceNumber());
                assertEquals(2, requests.get(1).identity().sequenceNumber());
            }
            assertEquals(
                    0,
                    esme.unbind()
                            .result()
                            .toCompletableFuture()
                            .get(2, TimeUnit.SECONDS)
                            .commandStatus());
        } finally {
            client.close();
            server.close();
            assertTrue(client.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
            assertTrue(server.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
        }
    }

    private static MessageResponse result(boolean rejected, String id) {
        return new MessageResponse(rejected ? Optional.empty() : Optional.of(id), new OptionalParameters(List.of()));
    }

    static ShortMessage shortMessage() {
        return new ShortMessage(
                "",
                new Address(0, 0, "source"),
                new Address(0, 0, "destination"),
                0,
                0,
                0,
                "",
                "",
                0,
                0,
                0,
                0,
                new OctetString(new byte[] {1, 2, 3}),
                new OptionalParameters(List.of()));
    }

    static DataSm data() {
        return new DataSm(
                "",
                new Address(0, 0, "source"),
                new Address(0, 0, "destination"),
                0,
                0,
                0,
                new OptionalParameters(List.of(new Tlv(0x0424, new byte[] {1, 2, 3}))));
    }

    private static Stream<Arguments> exchanges() {
        return Stream.of(SmppVersion.values())
                .flatMap(version -> Stream.of(BindMode.values())
                        .flatMap(
                                mode -> Stream.of(false, true).map(rejected -> Arguments.of(version, mode, rejected))));
    }
}
