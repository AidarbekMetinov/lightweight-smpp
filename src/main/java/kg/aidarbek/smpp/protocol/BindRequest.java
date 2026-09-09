package kg.aidarbek.smpp.protocol;

import java.util.List;
import java.util.Objects;

/**
 * Immutable credentials and address selection shared by the three bind request modes.
 * The raw interface-version octet does not select a profile or establish peer capabilities.
 *
 * @param mode bind request role
 * @param systemId ESME identifier, at most 15 ASCII characters
 * @param password password, at most 8 ASCII characters
 * @param systemType system classification, at most 12 ASCII characters
 * @param interfaceVersion unsigned requested interface-version octet, retained even if unknown
 * @param addrTon unsigned address type octet
 * @param addrNpi unsigned numbering plan octet
 * @param addressRange address selection, at most 40 ASCII characters
 * @param optionalParameters raw trailing incoming TLVs, ignored semantically for bind requests
 */
public record BindRequest(
        BindMode mode,
        String systemId,
        String password,
        String systemType,
        int interfaceVersion,
        int addrTon,
        int addrNpi,
        String addressRange,
        OptionalParameters optionalParameters)
        implements Command {
    /**
     * Validates field representation without interpreting the peer's raw numeric fields.
     * @throws IllegalArgumentException for overlong/non-ASCII text, embedded NUL or an invalid octet
     * @throws NullPointerException for a missing field
     */
    public BindRequest {
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(optionalParameters, "optionalParameters");
        requireText(systemId, 16);
        requireText(password, 9);
        requireText(systemType, 13);
        requireText(addressRange, 41);
        requireOctet(interfaceVersion);
        requireOctet(addrTon);
        requireOctet(addrNpi);
    }

    /**
     * Creates a standard bind request without trailing optional parameters.
     * @param mode bind role
     * @param systemId ESME identifier
     * @param password ESME password
     * @param systemType system classification
     * @param interfaceVersion raw requested version octet
     * @param addrTon raw address type octet
     * @param addrNpi raw numbering plan octet
     * @param addressRange address selection
     */
    public BindRequest(
            BindMode mode,
            String systemId,
            String password,
            String systemType,
            int interfaceVersion,
            int addrTon,
            int addrNpi,
            String addressRange) {
        this(
                mode,
                systemId,
                password,
                systemType,
                interfaceVersion,
                addrTon,
                addrNpi,
                addressRange,
                new OptionalParameters(List.of()));
    }

    @Override
    public long commandId() {
        return mode.requestCommandId();
    }

    @Override
    public String toString() {
        return "BindRequest[mode=" + mode + ", interfaceVersion=" + interfaceVersion + "]";
    }

    private static void requireText(String value, int maximumOctets) {
        Objects.requireNonNull(value, "bind field");
        if (value.length() >= maximumOctets) {
            throw new IllegalArgumentException("Bind C-octet field exceeds its length bound");
        }
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == 0 || value.charAt(index) > 0x7f) {
                throw new IllegalArgumentException("Bind C-octet field requires non-NUL ASCII");
            }
        }
    }

    private static void requireOctet(int value) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException("Bind numeric field must be an unsigned octet");
        }
    }
}
