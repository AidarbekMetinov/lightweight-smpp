package kg.aidarbek.smpp.protocol;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable bind response body, keeping absence separate from an empty system identifier.
 * Raw version advertisements remain in the ordered TLV block and do not establish capabilities.
 *
 * @param mode matching bind mode
 * @param systemId MC identifier; absent when the standard error body is omitted
 * @param optionalParameters raw ordered response parameters
 */
public record BindResponse(BindMode mode, Optional<String> systemId, OptionalParameters optionalParameters)
        implements Command {
    /**
     * Checks owned immutable references and the optional system identifier's wire representation.
     * @throws NullPointerException if a field is null
     * @throws IllegalArgumentException for overlong, non-ASCII or NUL-containing system identifiers
     */
    public BindResponse {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(optionalParameters, "optionalParameters");
        systemId.ifPresent(value -> {
            if (value.length() >= 16) {
                throw new IllegalArgumentException("Bind response system_id exceeds its field bound");
            }
            for (int index = 0; index < value.length(); index++) {
                if (value.charAt(index) == 0 || value.charAt(index) > 0x7f) {
                    throw new IllegalArgumentException("Bind response system_id requires non-NUL ASCII");
                }
            }
        });
    }

    @Override
    public long commandId() {
        return mode.responseCommandId();
    }

    @Override
    public String toString() {
        return "BindResponse[mode=" + mode + ", bodyPresent=" + systemId.isPresent() + ", optionalParameters="
                + optionalParameters + "]";
    }
}
