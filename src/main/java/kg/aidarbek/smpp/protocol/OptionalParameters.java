package kg.aidarbek.smpp.protocol;

import java.util.List;

/**
 * Immutable, thread-safe ordered TLV block preserving repeated and unknown entries.
 *
 * <p>A command may require some contained TLVs despite this conventional name. This value makes
 * no claim about applicability or supported semantics. Equality and hashing are order-sensitive;
 * diagnostics expose only the count. Collection size is bounded by the codec at the wire boundary.
 */
public final class OptionalParameters {
    private final List<Tlv> entries;

    /**
     * Copies the entry list; each {@link Tlv} already owns its immutable value.
     *
     * @param entries non-null list with no null elements
     * @throws NullPointerException if the list or an element is null
     */
    public OptionalParameters(List<Tlv> entries) {
        this.entries = List.copyOf(entries);
    }

    /**
     * Returns an unmodifiable list in original wire order, including repeated tags.
     *
     * @return an unmodifiable list in original wire order, including repeated tags
     */
    public List<Tlv> entries() {
        return entries;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OptionalParameters that && entries.equals(that.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public String toString() {
        return "OptionalParameters[count=" + entries.size() + "]";
    }
}
