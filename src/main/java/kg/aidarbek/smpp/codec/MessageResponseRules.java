package kg.aidarbek.smpp.codec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import kg.aidarbek.smpp.profile.MessageDirection;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.DataSm;
import kg.aidarbek.smpp.protocol.DeliverSm;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.SubmitSm;
import kg.aidarbek.smpp.protocol.Tlv;

/** Response conditions requiring the original request, without correlation or session state. */
public final class MessageResponseRules {
    private MessageResponseRules() {}
    /**
     * Checks transaction-mode applicability after both PDUs have passed their body codecs.
     * Sequence matching and deadlines belong to request tracking; delivery outcome is not inferred.
     * @param request original immutable message request
     * @param response immutable corresponding message response
     * @param profile explicit specification
     * @param dataDirection original data request direction
     * @throws IllegalArgumentException for mismatched command identities or invalid context
     */
    public static void validate(
            Pdu<? extends Command> request,
            Pdu<? extends Command> response,
            ProtocolProfile profile,
            MessageDirection dataDirection) {
        validatedParameters(request, response, profile, dataDirection);
    }

    /**
     * Checks the same conditions plus an independently known delivery-attempt result.
     * @param request original message request
     * @param response corresponding message response
     * @param profile explicit specification
     * @param dataDirection original data request direction
     * @param deliverySucceeded whether that delivery attempt is known to have succeeded
     * @throws IllegalArgumentException if failure-only diagnostics contradict known success
     */
    public static void validate(
            Pdu<? extends Command> request,
            Pdu<? extends Command> response,
            ProtocolProfile profile,
            MessageDirection dataDirection,
            boolean deliverySucceeded) {
        OptionalParameters parameters = validatedParameters(request, response, profile, dataDirection);
        if (deliverySucceeded
                && (MessageTlvSupport.count(parameters, 0x0425) > 0
                        || MessageTlvSupport.count(parameters, 0x0423) > 0
                        || MessageTlvSupport.octet(parameters, 0x0420) == 1))
            throw new IllegalArgumentException("Failure diagnostics contradict a known successful delivery attempt");
    }

    private static OptionalParameters validatedParameters(
            Pdu<? extends Command> request,
            Pdu<? extends Command> response,
            ProtocolProfile profile,
            MessageDirection dataDirection) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(dataDirection, "dataDirection");
        Command original = request.command();
        int esm;
        if (original instanceof SubmitSm submit) esm = submit.fields().esmClass();
        else if (original instanceof DeliverSm deliver) esm = deliver.fields().esmClass();
        else if (original instanceof DataSm data) esm = data.esmClass();
        else throw new IllegalArgumentException("Expected original message request");
        if (response.command().commandId() != (original.commandId() | 0x80000000L))
            throw new IllegalArgumentException("Message response does not match original operation");
        MessageDirection direction = MessageTlvSupport.direction(original.commandId(), dataDirection);
        boolean transaction =
                (esm & 3) == 2 && !(profile.version() == SmppVersion.V3_4 && direction == MessageDirection.DELIVERY);
        TypedTlvRegistry registry = MessageCommandCodecs.tlvRegistry(dataDirection);
        List<Tlv> supported = new ArrayList<>();
        for (Tlv parameter : MessageCommandCodec.responseFields(response.command())
                .optionalParameters()
                .entries()) {
            if (registry.decode(profile.version(), response.command().commandId(), parameter, OctetString.class)
                    .isEmpty()) continue;
            int tag = parameter.tag();
            boolean requiresTransaction = tag == 0x0425
                    || tag == 0x0420
                    || (tag == 0x0423
                            && (profile.version() == SmppVersion.V3_4 || direction == MessageDirection.SUBMISSION))
                    || (tag == 0x001d
                            && profile.version() == SmppVersion.V5_0
                            && direction == MessageDirection.SUBMISSION);
            if (requiresTransaction && !transaction)
                throw new IllegalArgumentException("Response diagnostic requires transaction message mode");
            supported.add(parameter);
        }
        return new OptionalParameters(supported);
    }
}
