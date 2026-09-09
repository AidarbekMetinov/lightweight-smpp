package kg.aidarbek.smpp.endpoint;

import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.codec.CommonCommandCodecs;
import kg.aidarbek.smpp.codec.CommonResponseRules;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.CancelSm;
import kg.aidarbek.smpp.protocol.CancelSmResponse;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.QuerySm;
import kg.aidarbek.smpp.protocol.QuerySmResponse;
import kg.aidarbek.smpp.protocol.ReplaceSm;
import kg.aidarbek.smpp.protocol.ReplaceSmResponse;
import kg.aidarbek.smpp.protocol.SubmitMulti;
import kg.aidarbek.smpp.protocol.SubmitMultiResponse;

/** Typed common management/submission operations; one-way commands have separate sending paths. */
public final class CommonOperations {
    private static final OptionalParameters EMPTY = new OptionalParameters(List.of());
    /** Queries an MC message identity and its known state. */
    public static final Operation<QuerySm, QuerySmResponse> QUERY_SM = new Operation<>(
            3,
            QuerySm.class,
            QuerySmResponse.class,
            direction -> CommonCommandCodecs.all(),
            (request, profile) -> new QuerySmResponse(
                    profile.version() == SmppVersion.V3_4
                            ? Optional.of(new QuerySmResponse.Result(request.messageId(), "", 7, 0))
                            : Optional.empty(),
                    EMPTY),
            request -> !request.optionalParameters().entries().isEmpty(),
            response -> !response.optionalParameters().entries().isEmpty(),
            (request, response, profile, direction) -> CommonResponseRules.validate(request, response, profile));
    /** Cancels an application-selected stored message or matching set. */
    public static final Operation<CancelSm, CancelSmResponse> CANCEL_SM = new Operation<>(
            8,
            CancelSm.class,
            CancelSmResponse.class,
            direction -> CommonCommandCodecs.all(),
            (request, profile) -> new CancelSmResponse(EMPTY),
            request -> !request.optionalParameters().entries().isEmpty(),
            response -> !response.optionalParameters().entries().isEmpty(),
            (request, response, profile, direction) -> CommonResponseRules.validate(request, response, profile));
    /** Replaces a stored message, with the profile-specific transmitter/transceiver permission. */
    public static final Operation<ReplaceSm, ReplaceSmResponse> REPLACE_SM = new Operation<>(
            7,
            ReplaceSm.class,
            ReplaceSmResponse.class,
            direction -> CommonCommandCodecs.all(),
            (request, profile) -> new ReplaceSmResponse(EMPTY),
            request -> !request.optionalParameters().entries().isEmpty(),
            response -> !response.optionalParameters().entries().isEmpty(),
            (request, response, profile, direction) -> CommonResponseRules.validate(request, response, profile));
    /** Submits an ordered SME/distribution list and receives per-destination outcomes. */
    public static final Operation<SubmitMulti, SubmitMultiResponse> SUBMIT_MULTI = new Operation<>(
            0x21,
            SubmitMulti.class,
            SubmitMultiResponse.class,
            direction -> CommonCommandCodecs.all(),
            (request, profile) -> new SubmitMultiResponse(
                    profile.version() == SmppVersion.V3_4
                            ? Optional.of(new SubmitMultiResponse.Result("", List.of()))
                            : Optional.empty(),
                    EMPTY),
            request -> !request.optionalParameters().entries().isEmpty(),
            response -> !response.optionalParameters().entries().isEmpty(),
            (request, response, profile, direction) -> CommonResponseRules.validate(request, response, profile));

    private CommonOperations() {}

    static List<Operation<?, ?>> all() {
        return List.of(QUERY_SM, CANCEL_SM, REPLACE_SM, SUBMIT_MULTI);
    }
}
