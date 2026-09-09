package kg.aidarbek.smpp.protocol;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable query response, with explicit distinction between present and omitted standard body.
 * @param result present standard fields, or absent for canonical failed 5.0 responses
 * @param optionalParameters ordered raw extensions; absent bodies cannot carry parameters
 */
public record QuerySmResponse(Optional<Result> result, OptionalParameters optionalParameters) implements Command {
    /** Requires non-null values and forbids optional bytes without a standard body. */
    public QuerySmResponse {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(optionalParameters, "optionalParameters");
        if (result.isEmpty() && !optionalParameters.entries().isEmpty())
            throw new IllegalArgumentException("Omitted query response cannot contain TLVs");
    }

    @Override
    public long commandId() {
        return 0x80000003L;
    }

    /**
     * Raw query status fields; the codec validates profile-specific semantics.
     * @param messageId echoed queried identity of up to 64 ASCII characters
     * @param finalDate empty or absolute 16-character SMPP completion time; no timezone/century inference
     * @param messageState raw unsigned state octet, with supported values selected by the codec
     * @param errorCode raw unsigned network-specific error octet
     */
    public record Result(String messageId, String finalDate, int messageState, int errorCode) {
        /** Checks ASCII/octet representation without inferring a message lifecycle. */
        public Result {
            MessageValueChecks.ascii(messageId, 65);
            MessageValueChecks.ascii(finalDate, 17);
            MessageValueChecks.octet(messageState);
            MessageValueChecks.octet(errorCode);
        }

        @Override
        public String toString() {
            return "QueryResult[messageState=" + messageState + ", errorCode=" + errorCode + "]";
        }
    }

    @Override
    public String toString() {
        return "QuerySmResponse[optionalParameterCount="
                + optionalParameters.entries().size() + "]";
    }
}
