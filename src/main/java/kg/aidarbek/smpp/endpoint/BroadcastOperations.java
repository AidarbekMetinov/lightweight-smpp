package kg.aidarbek.smpp.endpoint;

import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.codec.BroadcastCommandCodecs;
import kg.aidarbek.smpp.codec.BroadcastResponseRules;
import kg.aidarbek.smpp.protocol.BroadcastSm;
import kg.aidarbek.smpp.protocol.BroadcastSmResponse;
import kg.aidarbek.smpp.protocol.CancelBroadcastSm;
import kg.aidarbek.smpp.protocol.CancelBroadcastSmResponse;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.QueryBroadcastSm;
import kg.aidarbek.smpp.protocol.QueryBroadcastSmResponse;

/** Optional typed SMPP 5.0 broadcast services; all requests use the ordinary bounded request owner. */
public final class BroadcastOperations {
    private static final OptionalParameters EMPTY = new OptionalParameters(List.of());
    /** Submits or explicitly replaces a message for application-selected broadcast areas. */
    public static final Operation<BroadcastSm, BroadcastSmResponse> BROADCAST_SM = new Operation<>(
            0x111,
            BroadcastSm.class,
            BroadcastSmResponse.class,
            direction -> BroadcastCommandCodecs.all(),
            (request, profile) -> new BroadcastSmResponse(new MessageResponse(Optional.empty(), EMPTY)),
            request -> !request.optionalParameters().entries().isEmpty(),
            response -> !response.fields().optionalParameters().entries().isEmpty(),
            (request, response, profile, direction) -> BroadcastResponseRules.validate(request, response, profile));
    /** Queries stored broadcast state and ordered per-area results. */
    public static final Operation<QueryBroadcastSm, QueryBroadcastSmResponse> QUERY_BROADCAST_SM = new Operation<>(
            0x112,
            QueryBroadcastSm.class,
            QueryBroadcastSmResponse.class,
            direction -> BroadcastCommandCodecs.all(),
            (request, profile) -> new QueryBroadcastSmResponse(new MessageResponse(Optional.empty(), EMPTY)),
            request -> !request.optionalParameters().entries().isEmpty(),
            response -> !response.fields().optionalParameters().entries().isEmpty(),
            (request, response, profile, direction) -> BroadcastResponseRules.validate(request, response, profile));
    /** Cancels an application-selected stored broadcast or matching group. */
    public static final Operation<CancelBroadcastSm, CancelBroadcastSmResponse> CANCEL_BROADCAST_SM = new Operation<>(
            0x113,
            CancelBroadcastSm.class,
            CancelBroadcastSmResponse.class,
            direction -> BroadcastCommandCodecs.all(),
            (request, profile) -> new CancelBroadcastSmResponse(EMPTY),
            request -> !request.optionalParameters().entries().isEmpty(),
            response -> !response.optionalParameters().entries().isEmpty(),
            (request, response, profile, direction) -> BroadcastResponseRules.validate(request, response, profile));

    private BroadcastOperations() {}

    static List<Operation<?, ?>> all() {
        return List.of(BROADCAST_SM, QUERY_BROADCAST_SM, CANCEL_BROADCAST_SM);
    }
}
