package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BroadcastSm;
import kg.aidarbek.smpp.protocol.BroadcastSmResponse;
import kg.aidarbek.smpp.protocol.CancelBroadcastSm;
import kg.aidarbek.smpp.protocol.CancelBroadcastSmResponse;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.QueryBroadcastSm;
import kg.aidarbek.smpp.protocol.QueryBroadcastSmResponse;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

class BroadcastEndpointTest {
    static final OptionalParameters EMPTY = new OptionalParameters(List.of());
    static final Address SOURCE = new Address(0, 0, "source");

    static OptionalParameters areas() {
        return new OptionalParameters(
                List.of(new Tlv(0x0606, new byte[] {0, 65}), new Tlv(0x0606, new byte[] {0, 66})));
    }

    static BroadcastSm sample() {
        return new BroadcastSm(
                "CBS",
                SOURCE,
                "",
                0,
                "",
                "",
                0,
                4,
                0,
                new OptionalParameters(List.of(
                        new Tlv(0x0606, new byte[] {0, 65}),
                        new Tlv(0x0606, new byte[] {0, 66}),
                        new Tlv(0x0601, new byte[] {0, 0, 16}),
                        new Tlv(0x0604, new byte[] {0, 1}),
                        new Tlv(0x0605, new byte[] {9, 0, 1}),
                        new Tlv(0x0424, new byte[] {0, (byte) 255}))));
    }

    @Test
    void capabilitiesAndAbsentServicesFollowBothProfilesBothRolesAndAllBindModes() throws Exception {
        for (SmppVersion version : SmppVersion.values())
            for (BindMode mode : BindMode.values()) {
                CompletableFuture<BoundSession> bound = new CompletableFuture<>();
                SmppServer server = CommonOperationsEndpointTest.server(version, EndpointHandlers.empty(), bound);
                SmppClient client = new SmppClient();
                try (server;
                        client) {
                    BoundSession esme = CommonOperationsEndpointTest.bind(client, server, version, mode);
                    BoundSession mc = bound.get(2, TimeUnit.SECONDS);
                    boolean permitted = version == SmppVersion.V5_0 && mode != BindMode.RECEIVER;
                    assertEquals(permitted, esme.broadcast().isPresent());
                    assertEquals(permitted, esme.queryBroadcast().isPresent());
                    assertEquals(permitted, esme.cancelBroadcast().isPresent());
                    assertTrue(mc.broadcast().isEmpty());
                    assertTrue(mc.queryBroadcast().isEmpty());
                    assertTrue(mc.cancelBroadcast().isEmpty());
                    if (permitted) {
                        var submit = esme.broadcast()
                                .orElseThrow()
                                .send(sample())
                                .result()
                                .toCompletableFuture()
                                .get(2, TimeUnit.SECONDS);
                        assertEquals(8, submit.commandStatus());
                        assertTrue(submit.command().fields().messageId().isEmpty());
                        var query = esme.queryBroadcast()
                                .orElseThrow()
                                .send(new QueryBroadcastSm("id", SOURCE, EMPTY))
                                .result()
                                .toCompletableFuture()
                                .get(2, TimeUnit.SECONDS);
                        assertEquals(8, query.commandStatus());
                        assertTrue(query.command().fields().messageId().isEmpty());
                        assertEquals(
                                8,
                                esme.cancelBroadcast()
                                        .orElseThrow()
                                        .send(new CancelBroadcastSm("", "id", SOURCE, EMPTY))
                                        .result()
                                        .toCompletableFuture()
                                        .get(2, TimeUnit.SECONDS)
                                        .commandStatus());
                        var retained = esme.broadcast().orElseThrow();
                        esme.close();
                        assertTrue(esme.broadcast().isEmpty());
                        assertThrows(IllegalStateException.class, () -> retained.send(sample()));
                    }
                }
                client.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
                server.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            }
    }

    @Test
    void typedHandlersControlBroadcastAcceptanceQueryAreasAndCancellationStatus() throws Exception {
        AtomicInteger handled = new AtomicInteger();
        EndpointHandlers handlers = EndpointHandlers.builder()
                .on(BroadcastOperations.BROADCAST_SM, request -> {
                    assertEquals(sample(), request.pdu().command());
                    assertTrue(Thread.currentThread().getName().startsWith("smpp-handler-"));
                    handled.incrementAndGet();
                    return CompletableFuture.completedFuture(new HandlerResponse<>(
                            0,
                            new BroadcastSmResponse(new MessageResponse(
                                    Optional.of("ID"),
                                    new OptionalParameters(List.of(
                                            new Tlv(0x0606, new byte[] {0, 66}),
                                            new Tlv(0x0607, new byte[] {0, 0, 1, 68})))))));
                })
                .on(BroadcastOperations.QUERY_BROADCAST_SM, request -> {
                    handled.incrementAndGet();
                    return CompletableFuture.completedFuture(new HandlerResponse<>(
                            0,
                            new QueryBroadcastSmResponse(new MessageResponse(
                                    Optional.of(request.pdu().command().messageId()),
                                    new OptionalParameters(List.of(
                                            new Tlv(0x0427, new byte[] {9}),
                                            new Tlv(0x0606, new byte[] {0, 65}),
                                            new Tlv(0x0608, new byte[] {100}),
                                            new Tlv(0x0606, new byte[] {0, 66}),
                                            new Tlv(0x0608, new byte[] {0})))))));
                })
                .on(BroadcastOperations.CANCEL_BROADCAST_SM, request -> {
                    handled.incrementAndGet();
                    return CompletableFuture.completedFuture(
                            new HandlerResponse<>(0x144, new CancelBroadcastSmResponse(EMPTY)));
                })
                .build();
        CompletableFuture<BoundSession> bound = new CompletableFuture<>();
        SmppServer server = CommonOperationsEndpointTest.server(SmppVersion.V5_0, handlers, bound);
        SmppClient client = new SmppClient();
        try (server;
                client) {
            BoundSession session =
                    CommonOperationsEndpointTest.bind(client, server, SmppVersion.V5_0, BindMode.TRANSCEIVER);
            var submit = session.broadcast()
                    .orElseThrow()
                    .send(sample())
                    .result()
                    .toCompletableFuture()
                    .get(2, TimeUnit.SECONDS);
            assertEquals(0, submit.commandStatus());
            assertEquals(Optional.of("ID"), submit.command().fields().messageId());
            assertEquals(
                    2, submit.command().fields().optionalParameters().entries().size());
            var query = session.queryBroadcast()
                    .orElseThrow()
                    .send(new QueryBroadcastSm("ID", SOURCE, EMPTY))
                    .result()
                    .toCompletableFuture()
                    .get(2, TimeUnit.SECONDS);
            assertEquals(0, query.commandStatus());
            assertEquals(
                    5, query.command().fields().optionalParameters().entries().size());
            assertEquals(
                    0x144,
                    session.cancelBroadcast()
                            .orElseThrow()
                            .send(new CancelBroadcastSm("CBS", "ID", SOURCE, EMPTY))
                            .result()
                            .toCompletableFuture()
                            .get(2, TimeUnit.SECONDS)
                            .commandStatus());
            assertEquals(3, handled.get());
        }
        client.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
        server.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
    }
}
