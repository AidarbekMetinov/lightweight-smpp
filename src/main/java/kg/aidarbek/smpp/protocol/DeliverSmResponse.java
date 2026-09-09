package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/**
 * Immutable DeliverSmResponse command value; its codec applies profile and status-specific body rules.
 * @param fields immutable fields, never null
 */
public record DeliverSmResponse(MessageResponse fields) implements Command {
    /** Requires a non-null immutable delivery response body. */
    public DeliverSmResponse {
        Objects.requireNonNull(fields, "fields");
    }

    @Override
    public long commandId() {
        return 0x80000005L;
    }
}
