package kg.aidarbek.smpp.endpoint;

import java.util.Optional;
import kg.aidarbek.smpp.codec.TypedTlvRegistry;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindResponse;
import kg.aidarbek.smpp.protocol.BroadcastSmResponse;
import kg.aidarbek.smpp.protocol.CancelBroadcastSmResponse;
import kg.aidarbek.smpp.protocol.CancelSmResponse;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.ControlCommand;
import kg.aidarbek.smpp.protocol.DataSmResponse;
import kg.aidarbek.smpp.protocol.DeliverSmResponse;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.QueryBroadcastSmResponse;
import kg.aidarbek.smpp.protocol.QuerySmResponse;
import kg.aidarbek.smpp.protocol.ReplaceSmResponse;
import kg.aidarbek.smpp.protocol.SubmitMultiResponse;
import kg.aidarbek.smpp.protocol.SubmitSmResponse;
import kg.aidarbek.smpp.protocol.Tlv;

/** Connection-confined last-sample observation; its owner validates and correlates responses first. */
final class CongestionMonitor {
    private static final TypedTlvRegistry VALUES = TypedTlvRegistry.standard();
    private CongestionObservation latest;

    Optional<CongestionObservation> snapshot() {
        return Optional.ofNullable(latest);
    }

    void accepted(Pdu<? extends Command> response, ProtocolProfile profile, long now) {
        if (profile.version() != SmppVersion.V5_0) return;
        OptionalParameters parameters =
                switch (response.command()) {
                    case BindResponse value -> value.optionalParameters();
                    case ControlCommand value -> value.optionalParameters();
                    case SubmitSmResponse value -> value.fields().optionalParameters();
                    case DeliverSmResponse value -> value.fields().optionalParameters();
                    case DataSmResponse value -> value.fields().optionalParameters();
                    case QuerySmResponse value -> value.optionalParameters();
                    case CancelSmResponse value -> value.optionalParameters();
                    case ReplaceSmResponse value -> value.optionalParameters();
                    case SubmitMultiResponse value -> value.optionalParameters();
                    case BroadcastSmResponse value -> value.fields().optionalParameters();
                    case QueryBroadcastSmResponse value -> value.fields().optionalParameters();
                    case CancelBroadcastSmResponse value -> value.optionalParameters();
                    default -> EndpointPdus.NO_PARAMETERS;
                };
        for (Tlv entry : parameters.entries())
            if (entry.tag() == 0x0428) {
                VALUES.decode(profile.version(), response.command().commandId(), entry, Integer.class)
                        .ifPresent(level -> latest = new CongestionObservation(
                                level, response.command().commandId(), response.sequenceNumber(), now));
            }
    }
}
