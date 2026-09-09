package kg.aidarbek.smpp.codec;

import java.util.Objects;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.PduHeader;

/** Shared preconditions for direct command-body calls that do not pass through full-PDU dispatch. */
final class CommandCodecChecks {
    private CommandCodecChecks() {}

    static void context(long commandId, long status, ProtocolProfile profile, PduLimits limits) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(limits, "limits");
        Pdu.validateHeader(new PduHeader(PduHeader.LENGTH, commandId, status, 1));
        if (!profile.definesCommand(commandId)) {
            throw new IllegalArgumentException("Command is not defined by the selected profile");
        }
    }

    static void command(Command command, long commandId) {
        Objects.requireNonNull(command, "command");
        if (command.commandId() != commandId) {
            throw new IllegalArgumentException("Command identity does not match this body codec");
        }
    }
}
