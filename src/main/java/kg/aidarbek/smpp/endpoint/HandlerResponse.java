package kg.aidarbek.smpp.endpoint;

import java.util.Objects;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.session.SendRequirements;

/**
 * An application response body/status; the library supplies correlation and validates actual fields.
 * Status zero accepts this protocol PDU; it does not establish handset delivery. A nonzero status must
 * use the operation/profile's valid negative body. Invalid responses become the operation's system-error response.
 * @param <R> immutable operation-specific response representation
 * @param commandStatus raw unsigned SMPP status, including application-defined nonzero statuses
 * @param command corresponding typed response body
 * @param requirements optional stricter capability requirements; actual fields and TLVs are always checked
 */
public record HandlerResponse<R extends Command>(long commandStatus, R command, SendRequirements requirements) {
    /** Preserves unsigned status and non-null typed data/requirements. */
    public HandlerResponse {
        if (commandStatus < 0 || commandStatus > 0xffff_ffffL)
            throw new IllegalArgumentException("Handler status must be uint32");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(requirements, "requirements");
    }
    /** Uses derived command requirements without an extra application minimum.
     * @param commandStatus raw unsigned response status
     * @param command corresponding typed response body */
    public HandlerResponse(long commandStatus, R command) {
        this(commandStatus, command, SendRequirements.COMMON);
    }
}
