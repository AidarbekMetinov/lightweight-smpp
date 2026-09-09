package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/**
 * Immutable SubmitSmResponse command value; its codec applies profile and status-specific body rules.
 * @param fields immutable fields, never null
 */
public record SubmitSmResponse(MessageResponse fields) implements Command {
    /** Requires a non-null immutable submission response body. */
    public SubmitSmResponse {
        Objects.requireNonNull(fields, "fields");
    }

    @Override
    public long commandId() {
        return 0x80000004L;
    }
}
