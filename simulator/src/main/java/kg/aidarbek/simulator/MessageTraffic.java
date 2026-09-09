package kg.aidarbek.simulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.endpoint.EndpointHandlers;
import kg.aidarbek.smpp.endpoint.MessageOperations;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.BindMode;
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
import kg.aidarbek.smpp.request.RequestOptions;

/** Initial message-operation builders and their early role checks. */
final class MessageTraffic {
    private MessageTraffic() {}

    public static void register(EndpointHandlers.Builder handlers, ReplyController replies) {
        handlers.on(
                MessageOperations.SUBMIT_SM,
                request -> replies.reply(
                        request,
                        new SubmitSmResponse(response(
                                Optional.of(Long.toHexString(request.pdu().sequenceNumber())))),
                        new SubmitSmResponse(response(Optional.empty())),
                        () -> content(request.pdu().command().fields())));
        handlers.on(
                MessageOperations.DELIVER_SM,
                request -> replies.reply(
                        request,
                        new DeliverSmResponse(response(Optional.of(""))),
                        new DeliverSmResponse(response(Optional.empty())),
                        () -> content(request.pdu().command().fields())));
        handlers.on(
                MessageOperations.DATA_SM,
                request -> replies.reply(
                        request,
                        new DataSmResponse(response(
                                Optional.of(Long.toHexString(request.pdu().sequenceNumber())))),
                        new DataSmResponse(response(Optional.empty())),
                        () -> content(request.pdu().command())));
    }

    private static MessageResponse response(Optional<String> id) {
        return new MessageResponse(id, new OptionalParameters(List.of()));
    }

    public static Optional<TrafficOperation> find(String name, SimulatorConfig config) {
        boolean client = config.mode() == SimulatorConfig.Mode.CLIENT;
        boolean transmitter = config.bindMode() != BindMode.RECEIVER;
        boolean receiver = config.bindMode() != BindMode.TRANSMITTER;
        boolean permitted =
                switch (name) {
                    case "submit" -> client && transmitter;
                    case "deliver" -> !client && receiver;
                    case "data" -> config.version() == SmppVersion.V3_4 || (client ? transmitter : receiver);
                    default -> true;
                };
        if (!permitted)
            throw new IllegalArgumentException("Operation is not permitted for this role, bind mode and version");
        var options = new RequestOptions(config.load().requestTimeout());
        return switch (name) {
            case "submit" ->
                Optional.of((session, content, index) ->
                        session.submission().orElseThrow().send(new SubmitSm(message(config, content)), options));
            case "deliver" ->
                Optional.of((session, content, index) ->
                        session.delivery().orElseThrow().send(new DeliverSm(message(config, content)), options));
            case "data" ->
                Optional.of((session, content, index) ->
                        session.dataMessages().orElseThrow().send(data(config, content), options));
            default -> Optional.empty();
        };
    }

    public static ShortMessage message(SimulatorConfig config, TrafficContent content) {
        boolean shortField = content.payload().length() <= 254;
        return new ShortMessage(
                "",
                new Address(1, 1, config.source()),
                new Address(1, 1, config.destination()),
                content.esmClass(),
                0,
                0,
                "",
                "",
                0,
                0,
                content.dataCoding(),
                0,
                shortField ? content.payload() : new OctetString(new byte[0]),
                shortField ? content.parameters() : payloadParameters(content));
    }

    public static DataSm data(SimulatorConfig config, TrafficContent content) {
        return new DataSm(
                "",
                new Address(1, 1, config.source()),
                new Address(1, 1, config.destination()),
                content.esmClass(),
                0,
                content.dataCoding(),
                payloadParameters(content));
    }

    private static OptionalParameters payloadParameters(TrafficContent content) {
        List<Tlv> tags = new ArrayList<>(content.parameters().entries());
        tags.add(new Tlv(0x0424, content.payload().value()));
        return new OptionalParameters(tags);
    }

    public static TrafficContent content(ShortMessage fields) {
        return extract(fields.esmClass(), fields.dataCoding(), fields.shortMessage(), fields.optionalParameters());
    }

    public static TrafficContent content(DataSm fields) {
        return extract(
                fields.esmClass(), fields.dataCoding(), new OctetString(new byte[0]), fields.optionalParameters());
    }

    private static TrafficContent extract(
            int esmClass, int coding, OctetString shortMessage, OptionalParameters parameters) {
        List<Tlv> tags = new ArrayList<>();
        OctetString payload = shortMessage;
        boolean seen = false;
        for (Tlv tag : parameters.entries()) {
            if (tag.tag() == 0x0424) {
                if (seen || shortMessage.length() != 0) throw new IllegalArgumentException("Multiple message payloads");
                payload = new OctetString(tag.value());
                seen = true;
            } else tags.add(tag);
        }
        return new TrafficContent(esmClass, coding, payload, new OptionalParameters(tags));
    }
}
