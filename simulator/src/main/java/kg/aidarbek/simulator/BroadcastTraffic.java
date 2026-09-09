package kg.aidarbek.simulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import kg.aidarbek.smpp.endpoint.BroadcastOperations;
import kg.aidarbek.smpp.endpoint.EndpointHandlers;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BroadcastSm;
import kg.aidarbek.smpp.protocol.BroadcastSmResponse;
import kg.aidarbek.smpp.protocol.CancelBroadcastSm;
import kg.aidarbek.smpp.protocol.CancelBroadcastSmResponse;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.QueryBroadcastSm;
import kg.aidarbek.smpp.protocol.QueryBroadcastSmResponse;
import kg.aidarbek.smpp.protocol.Tlv;
import kg.aidarbek.smpp.request.RequestOptions;

/** Deterministic SMPP 5.0 broadcast service fixtures; no radio network or persistent storage is implied. */
final class BroadcastTraffic {
    private static final OptionalParameters EMPTY = new OptionalParameters(List.of());
    private static final Tlv AREA_A = new Tlv(0x0606, new byte[] {0, 's', 'i', 'm', '-', 'a'});
    private static final Tlv AREA_B = new Tlv(0x0606, new byte[] {0, 's', 'i', 'm', '-', 'b'});
    private static final Tlv CONGESTION = new Tlv(0x0428, new byte[] {80});

    private BroadcastTraffic() {}

    static Optional<TrafficOperation> find(String name, SimulatorConfig config) {
        if (!Set.of("broadcast", "query-broadcast", "cancel-broadcast").contains(name)) return Optional.empty();
        if (config.version() != SmppVersion.V5_0
                || config.mode() != SimulatorConfig.Mode.CLIENT
                || config.bindMode() == BindMode.RECEIVER)
            throw new IllegalArgumentException("Broadcast traffic requires an SMPP 5.0 ESME in TX or TRX mode");
        RequestOptions options = RequestOptions.timeout(config.load().requestTimeout());
        Address source = new Address(1, 1, config.source());
        return Optional.of(
                switch (name) {
                    case "broadcast" ->
                        (session, content, index) ->
                                session.broadcast().orElseThrow().send(broadcast(config, content), options);
                    case "query-broadcast" ->
                        (session, content, index) -> session.queryBroadcast()
                                .orElseThrow()
                                .send(new QueryBroadcastSm(Long.toHexString(index + 1), source, EMPTY), options);
                    case "cancel-broadcast" ->
                        (session, content, index) -> session.cancelBroadcast()
                                .orElseThrow()
                                .send(
                                        new CancelBroadcastSm("CBS", Long.toHexString(index + 1), source, EMPTY),
                                        options);
                    default -> throw new IllegalStateException("Unsupported broadcast traffic");
                });
    }

    static void register(EndpointHandlers.Builder handlers, ReplyController replies) {
        handlers.on(
                BroadcastOperations.BROADCAST_SM,
                request -> replies.reply(
                        request,
                        new BroadcastSmResponse(new MessageResponse(
                                Optional.of(Long.toHexString(request.pdu().sequenceNumber())),
                                new OptionalParameters(List.of(CONGESTION)))),
                        new BroadcastSmResponse(new MessageResponse(Optional.empty(), EMPTY)),
                        () -> content(request.pdu().command())));
        handlers.on(BroadcastOperations.QUERY_BROADCAST_SM, request -> {
            String id = request.pdu().command().messageId();
            if (id.isEmpty()) id = Long.toHexString(request.pdu().sequenceNumber());
            return replies.reply(
                    request,
                    new QueryBroadcastSmResponse(new MessageResponse(
                            Optional.of(id),
                            new OptionalParameters(List.of(
                                    new Tlv(0x0427, new byte[] {9}),
                                    AREA_A,
                                    new Tlv(0x0608, new byte[] {100}),
                                    AREA_B,
                                    new Tlv(0x0608, new byte[] {(byte) 255}),
                                    CONGESTION)))),
                    new QueryBroadcastSmResponse(new MessageResponse(Optional.empty(), EMPTY)));
        });
        handlers.on(
                BroadcastOperations.CANCEL_BROADCAST_SM,
                request -> replies.reply(
                        request,
                        new CancelBroadcastSmResponse(new OptionalParameters(List.of(CONGESTION))),
                        new CancelBroadcastSmResponse(EMPTY)));
    }

    static BroadcastSm broadcast(SimulatorConfig config, TrafficContent content) {
        if (content.esmClass() != 0)
            throw new IllegalArgumentException("Broadcast has no esm_class field for receipts or UDH flags");
        List<Tlv> tags = new ArrayList<>(content.parameters().entries());
        tags.add(AREA_A);
        tags.add(AREA_B);
        tags.add(new Tlv(0x0601, new byte[] {0, 0, 16}));
        tags.add(new Tlv(0x0604, new byte[] {0, 1}));
        tags.add(new Tlv(0x0605, new byte[] {9, 0, 1}));
        tags.add(new Tlv(0x0424, content.payload().value()));
        return new BroadcastSm(
                "CBS",
                new Address(1, 1, config.source()),
                "",
                0,
                "",
                "",
                0,
                content.dataCoding(),
                0,
                new OptionalParameters(tags));
    }

    private static TrafficContent content(BroadcastSm command) {
        OctetString payload = new OctetString(new byte[0]);
        List<Tlv> remaining = new ArrayList<>();
        boolean seen = false;
        for (Tlv entry : command.optionalParameters().entries()) {
            if (entry.tag() == 0x0424) {
                if (seen) throw new IllegalArgumentException("Duplicate broadcast payload");
                payload = new OctetString(entry.value());
                seen = true;
            } else if (!Set.of(0x0601, 0x0604, 0x0605, 0x0606).contains(entry.tag())) remaining.add(entry);
        }
        return new TrafficContent(0, command.dataCoding(), payload, new OptionalParameters(remaining));
    }
}
