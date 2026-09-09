package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/** Immutable BroadcastSmResponse value; its codec validates body presence and TLV context.
 * @param fields present message identity and ordered TLVs, or an omitted error body */
public record BroadcastSmResponse(MessageResponse fields) implements Command {
    /** Requires an immutable response body. */
    public BroadcastSmResponse {
        Objects.requireNonNull(fields, "fields");
    }

    @Override
    public long commandId() {
        return 0x80000111L;
    }
}
