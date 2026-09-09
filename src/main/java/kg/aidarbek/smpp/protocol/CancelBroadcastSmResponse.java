package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/** Immutable cancellation response; SMPP 5.0 permits congestion on successful responses.
 * @param optionalParameters ordered raw TLVs; empty for canonical failed responses */
public record CancelBroadcastSmResponse(OptionalParameters optionalParameters) implements Command {
    /** Requires immutable optional parameters. */
    public CancelBroadcastSmResponse {
        Objects.requireNonNull(optionalParameters, "optionalParameters");
    }

    @Override
    public long commandId() {
        return 0x80000113L;
    }
}
