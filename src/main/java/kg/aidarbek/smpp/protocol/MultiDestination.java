package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/** One explicit destination alternative; unknown discriminators cannot be structurally decoded. */
public sealed interface MultiDestination permits MultiDestination.Sme, MultiDestination.DistributionList {
    /** An individual SME destination, encoded with discriminator 1.
     * @param address immutable SME address, limited to 20 characters by the codec */
    record Sme(Address address) implements MultiDestination {
        /** Requires a non-null immutable address. */
        public Sme {
            Objects.requireNonNull(address, "address");
        }
    }

    /** An application-resolved distribution-list identity, encoded with discriminator 2.
     * @param name up to 20 ASCII characters */
    record DistributionList(String name) implements MultiDestination {
        /** Validates its C-octet representation without expanding the list. */
        public DistributionList {
            MessageValueChecks.ascii(name, 21);
        }

        @Override
        public String toString() {
            return "DistributionList[nameLength=" + name.length() + "]";
        }
    }
}
