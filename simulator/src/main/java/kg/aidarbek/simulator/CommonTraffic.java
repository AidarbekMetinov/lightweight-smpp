package kg.aidarbek.simulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.endpoint.CommonOperations;
import kg.aidarbek.smpp.endpoint.EndpointHandlers;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.CancelSm;
import kg.aidarbek.smpp.protocol.CancelSmResponse;
import kg.aidarbek.smpp.protocol.MultiDestination;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.QuerySm;
import kg.aidarbek.smpp.protocol.QuerySmResponse;
import kg.aidarbek.smpp.protocol.ReplaceSm;
import kg.aidarbek.smpp.protocol.ReplaceSmResponse;
import kg.aidarbek.smpp.protocol.ShortMessage;
import kg.aidarbek.smpp.protocol.SubmitMulti;
import kg.aidarbek.smpp.protocol.SubmitMultiResponse;
import kg.aidarbek.smpp.protocol.Tlv;
import kg.aidarbek.smpp.request.RequestOptions;
import kg.aidarbek.smpp.session.EndpointRole;
import kg.aidarbek.smpp.session.SessionPermissions;
import kg.aidarbek.smpp.session.SessionState;

/** Typed common-operation adapters; storage behavior remains a deterministic simulator decision. */
final class CommonTraffic {
    private static final OptionalParameters EMPTY = new OptionalParameters(List.of());

    private CommonTraffic() {}

    static Optional<TrafficOperation> find(String name, SimulatorConfig config) {
        long command =
                switch (name) {
                    case "query" -> 3;
                    case "cancel" -> 8;
                    case "replace" -> 7;
                    case "multi" -> 0x21;
                    default -> 0;
                };
        if (command == 0) return Optional.empty();
        if (command == 7 && config.version() == SmppVersion.V3_4 && config.payloadBytes() > 254)
            throw new IllegalArgumentException("3.4 replacement exceeds short_message before endpoint creation");
        SessionState state =
                switch (config.bindMode()) {
                    case RECEIVER -> SessionState.BOUND_RX;
                    case TRANSMITTER -> SessionState.BOUND_TX;
                    case TRANSCEIVER -> SessionState.BOUND_TRX;
                };
        if (!SessionPermissions.permitsRequest(
                ProtocolProfile.forVersion(config.version()),
                config.mode() == SimulatorConfig.Mode.CLIENT ? EndpointRole.ESME : EndpointRole.MESSAGE_CENTER,
                state,
                command))
            throw new IllegalArgumentException("Common operation is unavailable for this role, mode and version");
        var options = RequestOptions.timeout(config.load().requestTimeout());
        Address source = new Address(1, 1, config.source()), destination = new Address(1, 1, config.destination());
        return Optional.of(
                switch (name) {
                    case "query" ->
                        (session, content, index) -> session.query()
                                .orElseThrow()
                                .send(new QuerySm(Long.toHexString(index + 1), source, EMPTY), options);
                    case "cancel" ->
                        (session, content, index) -> session.cancel()
                                .orElseThrow()
                                .send(
                                        new CancelSm("", Long.toHexString(index + 1), source, destination, EMPTY),
                                        options);
                    case "replace" ->
                        (session, content, index) ->
                                session.replace().orElseThrow().send(replacement(config, content, index), options);
                    case "multi" ->
                        (session, content, index) ->
                                session.multipleSubmission().orElseThrow().send(multiple(config, content), options);
                    default -> throw new IllegalStateException("Unsupported common operation");
                });
    }

    static void register(EndpointHandlers.Builder handlers, ReplyController replies) {
        handlers.on(CommonOperations.QUERY_SM, request -> {
            String id = request.pdu().command().messageId();
            QuerySmResponse success = new QuerySmResponse(Optional.of(new QuerySmResponse.Result(id, "", 2, 0)), EMPTY);
            QuerySmResponse negative = new QuerySmResponse(
                    version34(request.session().negotiation().effectiveProfile().orElseThrow())
                            ? Optional.of(new QuerySmResponse.Result(id, "", 7, 0))
                            : Optional.empty(),
                    EMPTY);
            return replies.reply(request, success, negative);
        });
        handlers.on(
                CommonOperations.CANCEL_SM,
                request -> replies.reply(request, new CancelSmResponse(EMPTY), new CancelSmResponse(EMPTY)));
        handlers.on(
                CommonOperations.REPLACE_SM,
                request -> replies.reply(
                        request,
                        new ReplaceSmResponse(EMPTY),
                        new ReplaceSmResponse(EMPTY),
                        () -> content(request.pdu().command())));
        handlers.on(CommonOperations.SUBMIT_MULTI, request -> {
            SubmitMultiResponse success = new SubmitMultiResponse(
                    Optional.of(new SubmitMultiResponse.Result(
                            Long.toHexString(request.pdu().sequenceNumber()), List.of())),
                    EMPTY);
            SubmitMultiResponse negative = new SubmitMultiResponse(
                    version34(request.session().negotiation().effectiveProfile().orElseThrow())
                            ? Optional.of(new SubmitMultiResponse.Result("", List.of()))
                            : Optional.empty(),
                    EMPTY);
            return replies.reply(
                    request, success, negative, () -> content(request.pdu().command()));
        });
    }

    private static boolean version34(ProtocolProfile profile) {
        return profile.version() == SmppVersion.V3_4;
    }

    static SubmitMulti multiple(SimulatorConfig config, TrafficContent content) {
        ShortMessage message = MessageTraffic.message(config, content);
        return new SubmitMulti(
                message.serviceType(),
                message.source(),
                List.of(
                        new MultiDestination.Sme(message.destination()),
                        new MultiDestination.DistributionList("sim-list")),
                message.esmClass(),
                message.protocolId(),
                message.priorityFlag(),
                message.scheduleDeliveryTime(),
                message.validityPeriod(),
                message.registeredDelivery(),
                message.replaceIfPresentFlag(),
                message.dataCoding(),
                message.defaultMessageId(),
                message.shortMessage(),
                message.optionalParameters());
    }

    static ReplaceSm replacement(SimulatorConfig config, TrafficContent content, long index) {
        if (content.esmClass() != 0
                || content.dataCoding() != 4
                || !content.parameters().entries().isEmpty())
            throw new IllegalArgumentException(
                    "Replacement simulator assumes an original unsegmented binary message (DCS4/esm0)");
        int maximum = config.version() == SmppVersion.V3_4 ? 254 : 255;
        boolean shortField = content.payload().length() <= maximum;
        if (!shortField && config.version() == SmppVersion.V3_4)
            throw new IllegalArgumentException("3.4 replacement exceeds short_message");
        return new ReplaceSm(
                Long.toHexString(index + 1),
                new Address(1, 1, config.source()),
                "",
                "",
                0,
                0,
                shortField ? content.payload() : new OctetString(new byte[0]),
                shortField
                        ? EMPTY
                        : new OptionalParameters(
                                List.of(new Tlv(0x0424, content.payload().value()))));
    }

    private static TrafficContent content(SubmitMulti command) {
        return MessageTraffic.content(new ShortMessage(
                command.serviceType(),
                command.source(),
                new Address(0, 0, ""),
                command.esmClass(),
                command.protocolId(),
                command.priorityFlag(),
                command.scheduleDeliveryTime(),
                command.validityPeriod(),
                command.registeredDelivery(),
                command.replaceIfPresentFlag(),
                command.dataCoding(),
                command.defaultMessageId(),
                command.shortMessage(),
                command.optionalParameters()));
    }

    private static TrafficContent content(ReplaceSm command) {
        OctetString payload = command.shortMessage();
        List<Tlv> parameters = new ArrayList<>();
        boolean seen = false;
        for (Tlv tag : command.optionalParameters().entries()) {
            if (tag.tag() == 0x0424) {
                if (seen || payload.length() != 0) throw new IllegalArgumentException("Multiple replacement payloads");
                payload = new OctetString(tag.value());
                seen = true;
            } else parameters.add(tag);
        }
        return new TrafficContent(0, 4, payload, new OptionalParameters(parameters));
    }
}
