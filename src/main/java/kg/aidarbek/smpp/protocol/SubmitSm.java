package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/**
 * Immutable SubmitSm command value; its codec applies profile and status-specific body rules.
 * @param fields immutable fields, never null
 */
public record SubmitSm(ShortMessage fields) implements Command {
    /** Requires a non-null immutable submission body. */
    public SubmitSm {
        Objects.requireNonNull(fields, "fields");
    }

    @Override
    public long commandId() {
        return 0x4L;
    }
}
