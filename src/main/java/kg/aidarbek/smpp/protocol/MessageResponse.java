package kg.aidarbek.smpp.protocol;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable message response body, distinguishing omission from a present empty message_id.
 * The command codec owns status/profile omission rules. No message ID or TLV value is printed.
 * @param messageId absent for an omitted body, otherwise a non-NUL ASCII ID of at most 64 characters
 * @param optionalParameters immutable ordered TLVs; empty when the body is omitted
 */
public record MessageResponse(Optional<String> messageId, OptionalParameters optionalParameters) {
    /** Validates the message ID representation and absence-versus-present-body invariant. */
    public MessageResponse {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(optionalParameters, "optionalParameters");
        messageId.ifPresent(value -> MessageValueChecks.ascii(value, 65));
        if (messageId.isEmpty() && !optionalParameters.entries().isEmpty())
            throw new IllegalArgumentException("An omitted message response body cannot contain TLVs");
    }

    @Override
    public String toString() {
        return "MessageResponse[bodyPresent=" + messageId.isPresent() + ", optionalParameterCount="
                + optionalParameters.entries().size() + "]";
    }
}
