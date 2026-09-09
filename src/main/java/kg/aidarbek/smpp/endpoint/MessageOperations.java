package kg.aidarbek.smpp.endpoint;

import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.codec.MessageCommandCodecs;
import kg.aidarbek.smpp.codec.MessageResponseRules;
import kg.aidarbek.smpp.protocol.DataSm;
import kg.aidarbek.smpp.protocol.DataSmResponse;
import kg.aidarbek.smpp.protocol.DeliverSm;
import kg.aidarbek.smpp.protocol.DeliverSmResponse;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.SubmitSm;
import kg.aidarbek.smpp.protocol.SubmitSmResponse;

/** Typed implemented message operations; wire permission remains version, direction and bind-mode specific. */
public final class MessageOperations {
    /** ESME submission and its message-center acceptance response. */
    public static final Operation<SubmitSm, SubmitSmResponse> SUBMIT_SM = new Operation<>(
            4,
            SubmitSm.class,
            SubmitSmResponse.class,
            MessageCommandCodecs::all,
            (request, profile) -> new SubmitSmResponse(omitted()),
            request -> !request.fields().optionalParameters().entries().isEmpty(),
            response -> !response.fields().optionalParameters().entries().isEmpty(),
            MessageResponseRules::validate);
    /** Message-center delivery and its ESME acceptance response. */
    public static final Operation<DeliverSm, DeliverSmResponse> DELIVER_SM = new Operation<>(
            5,
            DeliverSm.class,
            DeliverSmResponse.class,
            MessageCommandCodecs::all,
            (request, profile) -> new DeliverSmResponse(omitted()),
            request -> !request.fields().optionalParameters().entries().isEmpty(),
            response -> !response.fields().optionalParameters().entries().isEmpty(),
            MessageResponseRules::validate);
    /** Bidirectional data_sm, subject to the exact negotiated request-direction matrix. */
    public static final Operation<DataSm, DataSmResponse> DATA_SM = new Operation<>(
            0x103,
            DataSm.class,
            DataSmResponse.class,
            MessageCommandCodecs::all,
            (request, profile) -> new DataSmResponse(omitted()),
            request -> !request.optionalParameters().entries().isEmpty(),
            response -> !response.fields().optionalParameters().entries().isEmpty(),
            MessageResponseRules::validate);

    private MessageOperations() {}

    static List<Operation<?, ?>> all() {
        return List.of(SUBMIT_SM, DELIVER_SM, DATA_SM);
    }

    private static MessageResponse omitted() {
        return new MessageResponse(Optional.empty(), new OptionalParameters(List.of()));
    }
}
