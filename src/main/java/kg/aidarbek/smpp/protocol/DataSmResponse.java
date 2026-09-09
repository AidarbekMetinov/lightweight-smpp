package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/**
 * Immutable DataSmResponse command value; its codec applies profile and status-specific body rules.
 * @param fields immutable fields, never null
 */
public record DataSmResponse(MessageResponse fields) implements Command {
    /** Requires a non-null immutable data response body. */
    public DataSmResponse {
        Objects.requireNonNull(fields, "fields");
    }

    @Override
    public long commandId() {
        return 0x80000103L;
    }
}
