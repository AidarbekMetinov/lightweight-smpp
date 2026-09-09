package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/**
 * Immutable DeliverSm command value; its codec applies profile and status-specific body rules.
 * @param fields immutable fields, never null
 */
public record DeliverSm(ShortMessage fields) implements Command {
    /** Requires a non-null immutable delivery body. */
    public DeliverSm {
        Objects.requireNonNull(fields, "fields");
    }

    @Override
    public long commandId() {
        return 0x5L;
    }
}
