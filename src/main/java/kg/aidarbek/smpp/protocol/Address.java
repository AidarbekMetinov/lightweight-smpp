package kg.aidarbek.smpp.protocol;
/**
 * Immutable raw SME address. TON/NPI widths and ASCII storage are validated here; command codecs
 * enforce supported values and their 21- or 65-octet address field bounds. Empty addresses are retained.
 * @param ton unsigned type-of-number octet
 * @param npi unsigned numbering-plan octet
 * @param value non-NUL ASCII address, at most 64 characters
 */
public record Address(int ton, int npi, String value) {
    /** Validates unsigned address octets and the maximum raw ASCII address representation. */
    public Address {
        MessageValueChecks.octet(ton);
        MessageValueChecks.octet(npi);
        MessageValueChecks.ascii(value, 65);
    }

    @Override
    public String toString() {
        return "Address[ton=" + ton + ", npi=" + npi + ", length=" + value.length() + "]";
    }
}
