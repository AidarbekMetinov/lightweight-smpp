package kg.aidarbek.smpp.codec;

import java.util.Objects;
import java.util.Set;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.protocol.CancelSm;
import kg.aidarbek.smpp.protocol.CancelSmResponse;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.QuerySm;
import kg.aidarbek.smpp.protocol.QuerySmResponse;
import kg.aidarbek.smpp.protocol.ReplaceSm;
import kg.aidarbek.smpp.protocol.ReplaceSmResponse;
import kg.aidarbek.smpp.protocol.SubmitMulti;
import kg.aidarbek.smpp.protocol.SubmitMultiResponse;
import kg.aidarbek.smpp.protocol.Tlv;

/** Common-operation response conditions that require the decoded original request. */
public final class CommonResponseRules {
    private CommonResponseRules() {}
    /**
     * Validates query identity and transaction diagnostics after the body codecs have succeeded.
     * Sequence matching and request deadlines remain the caller's responsibility. An unsuccessful
     * distribution-list expansion can yield more destinations than the original list, so no count
     * or destination-membership rule is inferred without the application's expansion context.
     * @param request original query, cancellation, replacement, or multiple-destination request
     * @param response corresponding concrete response; generic_nack follows the error path separately
     * @param profile explicit specification used to interpret optional parameters
     * @throws IllegalArgumentException if types, echoed query identity, or diagnostic context conflict
     */
    public static void validate(
            Pdu<? extends Command> request, Pdu<? extends Command> response, ProtocolProfile profile) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(profile, "profile");
        boolean paired =
                switch (request.command()) {
                    case QuerySm query -> response.command() instanceof QuerySmResponse;
                    case CancelSm cancel -> response.command() instanceof CancelSmResponse;
                    case ReplaceSm replace -> response.command() instanceof ReplaceSmResponse;
                    case SubmitMulti multi -> response.command() instanceof SubmitMultiResponse;
                    default -> false;
                };
        if (!paired) throw new IllegalArgumentException("Common response does not match original operation");
        if (request.command() instanceof QuerySm query) {
            QuerySmResponse result = (QuerySmResponse) response.command();
            if (result.result().isPresent()
                    && !query.messageId().equals(result.result().orElseThrow().messageId()))
                throw new IllegalArgumentException("Query response must identify the queried message");
        }
        if (request.command() instanceof SubmitMulti multi) {
            SubmitMultiResponse result = (SubmitMultiResponse) response.command();
            for (Tlv parameter : result.optionalParameters().entries()) {
                if (Set.of(0x001d, 0x0420, 0x0423, 0x0425).contains(parameter.tag())
                        && CommonCommandCodecs.tlvRegistry()
                                .decode(profile.version(), result.commandId(), parameter, OctetString.class)
                                .isPresent()
                        && (multi.esmClass() & 3) != 2)
                    throw new IllegalArgumentException(
                            "Multiple-destination response diagnostic requires transaction mode");
            }
        }
    }
}
