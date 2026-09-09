package kg.aidarbek.smpp.endpoint;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.codec.CommandCodec;
import kg.aidarbek.smpp.codec.ControlCommandCodecs;
import kg.aidarbek.smpp.codec.MessageCommandCodecs;
import kg.aidarbek.smpp.codec.PduCodec;
import kg.aidarbek.smpp.codec.PduHeaderCodec;
import kg.aidarbek.smpp.codec.PduLimits;
import kg.aidarbek.smpp.profile.MessageDirection;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindResponse;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.ControlCommand;
import kg.aidarbek.smpp.protocol.DataSmResponse;
import kg.aidarbek.smpp.protocol.DeliverSmResponse;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.PduHeader;
import kg.aidarbek.smpp.protocol.SubmitSmResponse;
import kg.aidarbek.smpp.session.EndpointRole;

/** Composes bounded wire codecs and the endpoint's explicit negative-response shapes. */
final class EndpointPdus {
    static final OptionalParameters NO_PARAMETERS = new OptionalParameters(List.of());
    private final EndpointRole localRole;
    private final PduCodec submission;
    private final PduCodec delivery;

    EndpointPdus(EndpointRole localRole, PduLimits limits) {
        this.localRole = localRole;
        submission = codec(MessageDirection.SUBMISSION, limits);
        delivery = codec(MessageDirection.DELIVERY, limits);
    }

    private static PduCodec codec(MessageDirection direction, PduLimits limits) {
        List<CommandCodec<?>> codecs = new ArrayList<>(ControlCommandCodecs.all());
        codecs.addAll(MessageCommandCodecs.all(direction));
        return new PduCodec(codecs, limits);
    }

    byte[] negative(PduHeader offending, long status, ProtocolProfile profile) {
        if ((offending.commandId() & 0x8000_0000L) != 0 || status == 0) {
            throw new IllegalArgumentException("A negative response requires an offending request and failure status");
        }
        long sequence = offending.sequenceNumber();
        Command response = control(ControlCommand.Type.GENERIC_NACK);
        if (sequence == 0 || sequence > 0x7fff_ffffL) {
            sequence = 0;
        } else {
            for (BindMode mode : BindMode.values()) {
                if (offending.commandId() == mode.requestCommandId()) {
                    response = new BindResponse(mode, Optional.empty(), NO_PARAMETERS);
                }
            }
            MessageResponse omitted = new MessageResponse(Optional.empty(), NO_PARAMETERS);
            if (offending.commandId() == 4) response = new SubmitSmResponse(omitted);
            if (offending.commandId() == 5) response = new DeliverSmResponse(omitted);
            if (offending.commandId() == 0x103) response = new DataSmResponse(omitted);
            if (offending.commandId() == 6) response = control(ControlCommand.Type.UNBIND_RESPONSE);
            if (offending.commandId() == 0x15) response = control(ControlCommand.Type.ENQUIRE_LINK_RESPONSE);
        }
        return encode(new Pdu<>(status, sequence, response), profile);
    }

    Pdu<Command> decode(byte[] frame, ProtocolProfile profile) {
        long command = header(frame).commandId();
        return select(command, false).decode(frame, profile);
    }

    byte[] encode(Pdu<? extends Command> pdu, ProtocolProfile profile) {
        return select(pdu.command().commandId(), true).encode(pdu, profile);
    }

    static PduHeader header(byte[] frame) {
        return PduHeaderCodec.decode(ByteBuffer.wrap(frame));
    }

    static PduHeader header(Pdu<? extends Command> pdu) {
        return new PduHeader(16, pdu.command().commandId(), pdu.commandStatus(), pdu.sequenceNumber());
    }

    static ControlCommand control(ControlCommand.Type type) {
        return new ControlCommand(type, NO_PARAMETERS);
    }

    private PduCodec select(long commandId, boolean outgoing) {
        boolean requestOutgoing = (commandId & 0x8000_0000L) == 0 ? outgoing : !outgoing;
        boolean fromEsme = requestOutgoing == (localRole == EndpointRole.ESME);
        return fromEsme ? submission : delivery;
    }
}
