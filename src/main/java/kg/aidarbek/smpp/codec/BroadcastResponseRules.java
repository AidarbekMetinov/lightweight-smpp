package kg.aidarbek.smpp.codec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BroadcastSm;
import kg.aidarbek.smpp.protocol.BroadcastSmResponse;
import kg.aidarbek.smpp.protocol.CancelBroadcastSm;
import kg.aidarbek.smpp.protocol.CancelBroadcastSmResponse;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.QueryBroadcastSm;
import kg.aidarbek.smpp.protocol.QueryBroadcastSmResponse;
import kg.aidarbek.smpp.protocol.Tlv;

/** Response checks requiring explicit original broadcast context, separate from request tracking. */
public final class BroadcastResponseRules {
    private BroadcastResponseRules() {}
    /** Checks a concrete paired response after both body codecs passed; generic_nack is separate.
     * @param request original broadcast, query or cancellation
     * @param response corresponding concrete response
     * @param profile explicit SMPP 5.0 profile
     * @throws IllegalArgumentException for incompatible identities or area results */
    public static void validate(
            Pdu<? extends Command> request, Pdu<? extends Command> response, ProtocolProfile profile) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(response, "response");
        if (Objects.requireNonNull(profile, "profile").version() != SmppVersion.V5_0)
            throw new IllegalArgumentException("Broadcast response context requires SMPP 5.0");
        boolean paired =
                switch (request.command()) {
                    case BroadcastSm original -> response.command() instanceof BroadcastSmResponse;
                    case QueryBroadcastSm query -> response.command() instanceof QueryBroadcastSmResponse;
                    case CancelBroadcastSm cancel -> response.command() instanceof CancelBroadcastSmResponse;
                    default -> false;
                };
        if (!paired) throw new IllegalArgumentException("Response does not match broadcast operation");
        if (response.commandStatus() != 0) return;
        if (request.command() instanceof QueryBroadcastSm query) {
            QueryBroadcastSmResponse result = (QueryBroadcastSmResponse) response.command();
            if (!query.messageId().isEmpty() && !result.fields().messageId().equals(Optional.of(query.messageId())))
                throw new IllegalArgumentException("Broadcast query response has a different MC identity");
            Optional<Tlv> reference = reference(query.optionalParameters());
            Optional<Tlv> echo = reference(result.fields().optionalParameters());
            if (reference.isPresent() && echo.isPresent() && !reference.equals(echo))
                throw new IllegalArgumentException("Broadcast query response has a different ESME reference");
        } else if (request.command() instanceof BroadcastSm original) {
            compareAreas(
                    original.optionalParameters(),
                    0x111,
                    ((BroadcastSmResponse) response.command()).fields().optionalParameters(),
                    0x80000111L,
                    false);
        }
    }
    /** Checks queried area multiplicity against the separately stored original submission.
     * No original-submission storage or lookup is performed by this helper.
     * @param original original immutable broadcast submission
     * @param response decoded query result, with order-preserved parallel area/success TLVs
     * @throws IllegalArgumentException if supported result areas differ from the original targets */
    public static void validateQueryResult(BroadcastSm original, QueryBroadcastSmResponse response) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(response, "response");
        if (response.fields().messageId().isEmpty()) return;
        compareAreas(original.optionalParameters(), 0x111, response.fields().optionalParameters(), 0x80000112L, true);
    }

    private static Optional<Tlv> reference(OptionalParameters parameters) {
        return parameters.entries().stream().filter(t -> t.tag() == 0x0204).findFirst();
    }

    private static void compareAreas(
            OptionalParameters original, long requestId, OptionalParameters result, long responseId, boolean complete) {
        Optional<List<Tlv>> submitted = areas(original, requestId), returned = areas(result, responseId);
        if (submitted.isEmpty() || returned.isEmpty()) return;
        List<Tlv> remaining = new ArrayList<>(submitted.orElseThrow());
        for (Tlv area : returned.orElseThrow())
            if (!remaining.remove(area))
                throw new IllegalArgumentException("Broadcast result area was not requested with this multiplicity");
        if (complete && !remaining.isEmpty())
            throw new IllegalArgumentException("Broadcast query result omits original target areas");
    }

    private static Optional<List<Tlv>> areas(OptionalParameters parameters, long commandId) {
        List<Tlv> areas = new ArrayList<>();
        for (Tlv parameter : parameters.entries())
            if (parameter.tag() == 0x0606) {
                if (BroadcastCommandCodecs.tlvRegistry()
                        .decode(SmppVersion.V5_0, commandId, parameter, OctetString.class)
                        .isEmpty()) return Optional.empty();
                areas.add(parameter);
            }
        return Optional.of(List.copyOf(areas));
    }
}
