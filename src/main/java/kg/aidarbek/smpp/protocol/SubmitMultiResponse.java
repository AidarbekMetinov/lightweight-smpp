package kg.aidarbek.smpp.protocol;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable multiple-submission response with explicit standard-body omission.
 * @param result present standard fields, or absent for canonical failed 5.0 responses
 * @param optionalParameters ordered raw diagnostics; absent bodies cannot carry parameters
 */
public record SubmitMultiResponse(Optional<Result> result, OptionalParameters optionalParameters) implements Command {
    /** Requires non-null values and forbids optional bytes without a standard body. */
    public SubmitMultiResponse {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(optionalParameters, "optionalParameters");
        if (result.isEmpty() && !optionalParameters.entries().isEmpty())
            throw new IllegalArgumentException("Omitted multiple-submission response cannot contain TLVs");
    }

    @Override
    public long commandId() {
        return 0x80000021L;
    }

    /**
     * The MC message identity and unsuccessful expanded SME destinations.
     * @param messageId identity of up to 64 ASCII characters
     * @param unsuccessful copied ordered list of at most 255 per-SME results
     */
    public record Result(String messageId, List<UnsuccessfulDestination> unsuccessful) {
        /** Bounds the list before copying; distribution-list expansion relationships remain external. */
        public Result {
            MessageValueChecks.ascii(messageId, 65);
            Objects.requireNonNull(unsuccessful, "unsuccessful");
            if (unsuccessful.size() > 255) throw new IllegalArgumentException("Unsuccessful count exceeds its octet");
            unsuccessful = List.copyOf(unsuccessful);
        }

        @Override
        public String toString() {
            return "MultipleResult[unsuccessfulCount=" + unsuccessful.size() + "]";
        }
    }

    @Override
    public String toString() {
        return "SubmitMultiResponse[optionalParameterCount="
                + optionalParameters.entries().size() + "]";
    }
}
