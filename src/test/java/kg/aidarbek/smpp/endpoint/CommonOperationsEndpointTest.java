package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.CancelSm;
import kg.aidarbek.smpp.protocol.CancelSmResponse;
import kg.aidarbek.smpp.protocol.MultiDestination;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.QuerySm;
import kg.aidarbek.smpp.protocol.QuerySmResponse;
import kg.aidarbek.smpp.protocol.ReplaceSm;
import kg.aidarbek.smpp.protocol.ReplaceSmResponse;
import kg.aidarbek.smpp.protocol.SubmitMulti;
import kg.aidarbek.smpp.protocol.SubmitMultiResponse;
import kg.aidarbek.smpp.protocol.UnsuccessfulDestination;
import org.junit.jupiter.api.Test;

class CommonOperationsEndpointTest {
    static final OptionalParameters EMPTY = new OptionalParameters(List.of());
    static final Address SOURCE = new Address(0, 0, "source");
    static final Address DESTINATION = new Address(0, 0, "destination");

    @Test
    void capabilitiesAndAbsentHandlersFollowEveryRoleModeAndProfile() throws Exception {
        for (SmppVersion version : SmppVersion.values()) {
            for (BindMode mode : BindMode.values()) {
                CompletableFuture<BoundSession> bound = new CompletableFuture<>();
                try (SmppServer server = server(version, EndpointHandlers.empty(), bound);
                        SmppClient client = new SmppClient()) {
                    BoundSession esme = bind(client, server, version, mode);
                    BoundSession mc = bound.get(2, TimeUnit.SECONDS);
                    boolean sending = mode != BindMode.RECEIVER;
                    boolean replacing = mode == BindMode.TRANSMITTER
                            || (mode == BindMode.TRANSCEIVER && version == SmppVersion.V5_0);
                    assertEquals(sending, esme.query().isPresent());
                    assertEquals(sending, esme.cancel().isPresent());
                    assertEquals(sending, esme.multipleSubmission().isPresent());
                    assertEquals(replacing, esme.replace().isPresent());
                    assertTrue(mc.query().isEmpty());
                    assertTrue(mc.cancel().isEmpty());
                    assertTrue(mc.replace().isEmpty());
                    assertTrue(mc.multipleSubmission().isEmpty());
                    if (sending) {
                        var query = esme.query()
                                .orElseThrow()
                                .send(new QuerySm("unknown-id", SOURCE, EMPTY))
                                .result()
                                .toCompletableFuture()
                                .get(2, TimeUnit.SECONDS);
                        assertEquals(8, query.commandStatus());
                        assertEquals(
                                version == SmppVersion.V3_4,
                                query.command().result().isPresent());
                        query.command().result().ifPresent(result -> assertEquals("unknown-id", result.messageId()));
                        assertEquals(
                                8,
                                esme.cancel()
                                        .orElseThrow()
                                        .send(new CancelSm("", "id", SOURCE, DESTINATION, EMPTY))
                                        .result()
                                        .toCompletableFuture()
                                        .get(2, TimeUnit.SECONDS)
                                        .commandStatus());
                        var multi = esme.multipleSubmission()
                                .orElseThrow()
                                .send(multi())
                                .result()
                                .toCompletableFuture()
                                .get(2, TimeUnit.SECONDS);
                        assertEquals(8, multi.commandStatus());
                        assertEquals(
                                version == SmppVersion.V3_4,
                                multi.command().result().isPresent());
                    }
                    if (replacing)
                        assertEquals(
                                8,
                                esme.replace()
                                        .orElseThrow()
                                        .send(replacement())
                                        .result()
                                        .toCompletableFuture()
                                        .get(2, TimeUnit.SECONDS)
                                        .commandStatus());
                    if (sending) {
                        var capability = esme.query().orElseThrow();
                        esme.close();
                        assertTrue(esme.query().isEmpty());
                        assertThrows(
                                IllegalStateException.class, () -> capability.send(new QuerySm("id", SOURCE, EMPTY)));
                    }
                }
            }
        }
    }

    @Test
    void allPairedCommonOperationsReachTypedHandlersAndPreservePerDestinationResults() throws Exception {
        for (SmppVersion version : SmppVersion.values()) {
            EndpointHandlers handlers = EndpointHandlers.builder()
                    .on(
                            CommonOperations.QUERY_SM,
                            request -> CompletableFuture.completedFuture(new HandlerResponse<>(
                                    0,
                                    new QuerySmResponse(
                                            Optional.of(new QuerySmResponse.Result(
                                                    request.pdu().command().messageId(), "", 7, 0)),
                                            EMPTY))))
                    .on(
                            CommonOperations.CANCEL_SM,
                            request -> CompletableFuture.completedFuture(
                                    new HandlerResponse<>(0, new CancelSmResponse(EMPTY))))
                    .on(
                            CommonOperations.REPLACE_SM,
                            request -> CompletableFuture.completedFuture(
                                    new HandlerResponse<>(0, new ReplaceSmResponse(EMPTY))))
                    .on(
                            CommonOperations.SUBMIT_MULTI,
                            request -> CompletableFuture.completedFuture(new HandlerResponse<>(
                                    0,
                                    new SubmitMultiResponse(
                                            Optional.of(new SubmitMultiResponse.Result(
                                                    "multi-id", List.of(new UnsuccessfulDestination(DESTINATION, 11)))),
                                            EMPTY))))
                    .build();
            CompletableFuture<BoundSession> mc = new CompletableFuture<>();
            try (SmppServer server = server(version, handlers, mc);
                    SmppClient client = new SmppClient()) {
                BoundSession esme = bind(client, server, version, BindMode.TRANSMITTER);
                BoundSession messageCenter = mc.get(2, TimeUnit.SECONDS);
                assertFalse(messageCenter.sender(CommonOperations.QUERY_SM).isPresent());
                QuerySm query = new QuerySm("queried", SOURCE, EMPTY);
                assertEquals(
                        "queried",
                        esme.sender(CommonOperations.QUERY_SM)
                                .orElseThrow()
                                .send(query)
                                .result()
                                .toCompletableFuture()
                                .get(2, TimeUnit.SECONDS)
                                .command()
                                .result()
                                .orElseThrow()
                                .messageId());
                assertEquals(
                        0,
                        esme.sender(CommonOperations.CANCEL_SM)
                                .orElseThrow()
                                .send(new CancelSm("", "id", SOURCE, DESTINATION, EMPTY))
                                .result()
                                .toCompletableFuture()
                                .get(2, TimeUnit.SECONDS)
                                .commandStatus());
                assertEquals(
                        0,
                        esme.sender(CommonOperations.REPLACE_SM)
                                .orElseThrow()
                                .send(replacement())
                                .result()
                                .toCompletableFuture()
                                .get(2, TimeUnit.SECONDS)
                                .commandStatus());
                var response = esme.sender(CommonOperations.SUBMIT_MULTI)
                        .orElseThrow()
                        .send(multi())
                        .result()
                        .toCompletableFuture()
                        .get(2, TimeUnit.SECONDS);
                assertEquals(
                        "multi-id", response.command().result().orElseThrow().messageId());
                assertEquals(
                        List.of(new UnsuccessfulDestination(DESTINATION, 11)),
                        response.command().result().orElseThrow().unsuccessful());
            }
        }
    }

    static SmppServer server(SmppVersion version, EndpointHandlers handlers, CompletableFuture<BoundSession> bound) {
        return new SmppServer(
                new ServerConfig(new InetSocketAddress("127.0.0.1", 0), Set.of(version), version, "mc", 1, 4),
                EndpointOptions.defaults(),
                (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                bound::complete,
                null,
                new ExchangeConfig(ExchangeOptions.defaults(), handlers));
    }

    static BoundSession bind(SmppClient client, SmppServer server, SmppVersion version, BindMode mode)
            throws Exception {
        InetSocketAddress address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
        return client.connect(new ClientConfig(
                        address, new BindRequest(mode, "esme", "pw", "", version.interfaceVersion(), 0, 0, ""), true))
                .toCompletableFuture()
                .get(2, TimeUnit.SECONDS);
    }

    static ReplaceSm replacement() {
        return new ReplaceSm("id", SOURCE, "", "", 0, 0, new OctetString(new byte[] {0, (byte) 255}), EMPTY);
    }

    static SubmitMulti multi() {
        return new SubmitMulti(
                "",
                SOURCE,
                List.of(new MultiDestination.Sme(DESTINATION), new MultiDestination.DistributionList("list")),
                0,
                0,
                0,
                "",
                "",
                0,
                0,
                4,
                0,
                new OctetString(new byte[] {0, (byte) 255}),
                EMPTY);
    }
}
