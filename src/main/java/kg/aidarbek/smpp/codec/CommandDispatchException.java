package kg.aidarbek.smpp.codec;

import java.util.Objects;
import kg.aidarbek.smpp.protocol.PduHeader;

/** A complete PDU whose command is undefined in the chosen profile or has no local body codec. */
public final class CommandDispatchException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    /** Header length retained as a primitive so exception serialization preserves the raw header. */
    private final long commandLength;
    /** Original unsigned command identifier. */
    private final long commandId;
    /** Original unsigned command status. */
    private final long commandStatus;
    /** Original unsigned correlation sequence. */
    private final long sequenceNumber;
    /** Whether the selected specification defined the unavailable command. */
    private final boolean definedByProfile;

    /**
     * Retains the raw header for later session error policy, without retaining body contents.
     * @param header raw received header, or a provisional header of length 16 before encoding a body
     * @param definedByProfile true for a defined command with no local codec; false for an undefined command
     */
    public CommandDispatchException(PduHeader header, boolean definedByProfile) {
        super(definedByProfile ? "No codec registered for command" : "Command is not defined by the selected profile");
        Objects.requireNonNull(header, "header");
        commandLength = header.commandLength();
        commandId = header.commandId();
        commandStatus = header.commandStatus();
        sequenceNumber = header.sequenceNumber();
        this.definedByProfile = definedByProfile;
    }

    /**
     * Returns the original immutable header, including command, sequence and numeric status.
     * @return raw header for the rejected command
     */
    public PduHeader header() {
        return new PduHeader(commandLength, commandId, commandStatus, sequenceNumber);
    }

    /**
     * Distinguishes an unavailable local codec from an undefined wire command.
     * @return whether the selected specification defines this command
     */
    public boolean definedByProfile() {
        return definedByProfile;
    }
}
