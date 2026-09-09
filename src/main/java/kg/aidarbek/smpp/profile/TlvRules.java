package kg.aidarbek.smpp.profile;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Tlv;

/**
 * Immutable, thread-safe declaration of permitted, required and repeatable TLV tags for one context.
 *
 * <p>These are occurrence rules only. They do not validate value lengths or content, related tag
 * counts, standard fields, response status, or endpoint roles. Raw codec bounds must be applied
 * before incoming validation. Singleton duplicates are rejected in recognized contexts.
 */
public final class TlvRules {
    private final Set<Integer> permittedTags;
    private final Set<Integer> requiredTags;
    private final Set<Integer> repeatableTags;

    /**
     * Copies the three rule sets. Required tags must appear at least once; permitted tags that are
     * not repeatable may appear at most once.
     *
     * @param permittedTags non-null set of unsigned 16-bit tags
     * @param requiredTags non-null subset of permitted tags
     * @param repeatableTags non-null subset of permitted tags
     * @throws IllegalArgumentException if a tag is out of range or a subset is inconsistent
     * @throws NullPointerException if a set or element is null
     */
    public TlvRules(Set<Integer> permittedTags, Set<Integer> requiredTags, Set<Integer> repeatableTags) {
        this.permittedTags = Set.copyOf(permittedTags);
        this.requiredTags = Set.copyOf(requiredTags);
        this.repeatableTags = Set.copyOf(repeatableTags);
        if (!this.permittedTags.containsAll(this.requiredTags)
                || !this.permittedTags.containsAll(this.repeatableTags)) {
            throw new IllegalArgumentException("Required and repeatable tags must be permitted");
        }
        for (int tag : this.permittedTags) {
            if (tag < 0 || tag > 0xffff) {
                throw new IllegalArgumentException("Rule tag must be an unsigned 16-bit value");
            }
        }
    }

    /**
     * Checks required and singleton occurrences of permitted tags. Unexpected tags are ignored
     * semantically, including their repetitions; the original raw entries remain unchanged.
     *
     * @param parameters non-null structurally decoded raw block
     * @throws IllegalArgumentException if a required tag is absent or a permitted singleton repeats
     */
    public void validateIncoming(OptionalParameters parameters) {
        validate(parameters, false);
    }

    /**
     * Checks all occurrence rules and rejects tags not explicitly permitted by this context.
     *
     * @param parameters non-null proposed raw block
     * @throws IllegalArgumentException if a tag is unexpected, required and absent, or an illegal duplicate
     */
    public void validateOutgoing(OptionalParameters parameters) {
        validate(parameters, true);
    }

    private void validate(OptionalParameters parameters, boolean outgoing) {
        Objects.requireNonNull(parameters, "parameters");
        Set<Integer> seen = new HashSet<>();
        for (Tlv entry : parameters.entries()) {
            int tag = entry.tag();
            if (!permittedTags.contains(tag)) {
                if (outgoing) {
                    throw new IllegalArgumentException(
                            "TLV not permitted in command context: 0x" + Integer.toHexString(tag));
                }
                continue;
            }
            if (!seen.add(tag) && !repeatableTags.contains(tag)) {
                throw new IllegalArgumentException("Duplicate singleton TLV: 0x" + Integer.toHexString(tag));
            }
        }
        if (!seen.containsAll(requiredTags)) {
            throw new IllegalArgumentException("Required command TLV is absent");
        }
    }
}
