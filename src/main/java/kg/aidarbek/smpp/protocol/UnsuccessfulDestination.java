package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/**
 * One ordered per-destination result, without inferring distribution-list membership.
 * @param destination immutable expanded SME address, limited to 20 characters by the codec
 * @param status raw unsigned 32-bit SMPP status value
 */
public record UnsuccessfulDestination(Address destination, long status) {
    /** Requires an immutable address and unsigned status representation. */
    public UnsuccessfulDestination {
        Objects.requireNonNull(destination, "destination");
        if (status < 0 || status > 0xffff_ffffL)
            throw new IllegalArgumentException("Destination status must be unsigned 32-bit");
    }
}
