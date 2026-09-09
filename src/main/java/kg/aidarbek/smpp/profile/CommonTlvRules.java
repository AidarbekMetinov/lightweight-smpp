package kg.aidarbek.smpp.profile;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Exact optional-parameter contexts for the implemented common operations. */
public final class CommonTlvRules {
    private CommonTlvRules() {}

    /**
     * Returns the exact operation table, following the explicit submit_multi dest_subaddress exclusion.
     * Value semantics, repetitions/companions, status-specific omission and session permission are
     * separate contracts. No standard TLV is required by these operation layouts.
     * @param version explicit specification
     * @param commandId one of the ten implemented common request/response command IDs
     * @return immutable permitted tag set
     * @throws IllegalArgumentException for any other command identity
     */
    public static Set<Integer> permittedTags(SmppVersion version, long commandId) {
        Objects.requireNonNull(version, "version");
        if (commandId == 3 || commandId == 8 || commandId == 0x0b) return Set.of();
        if (commandId == 0x80000003L || commandId == 0x80000008L || commandId == 0x80000007L)
            return version == SmppVersion.V5_0 ? Set.of(0x0428) : Set.of();
        if (commandId == 0x102) return Set.of(0x0422);
        if (commandId == 7) return version == SmppVersion.V5_0 ? Set.of(0x0424) : Set.of();
        if (commandId == 0x80000021L)
            return version == SmppVersion.V5_0 ? Set.of(0x001d, 0x0420, 0x0423, 0x0425, 0x0428) : Set.of();
        if (commandId == 0x21) {
            if (version == SmppVersion.V3_4)
                return Set.of(
                        0x0204, 0x020a, 0x000d, 0x020b, 0x0005, 0x020c, 0x020e, 0x020f, 0x0019, 0x0424, 0x0201, 0x0381,
                        0x0302, 0x0303, 0x0202, 0x1201, 0x1203, 0x1204, 0x0030, 0x130c, 0x020d);
            Set<Integer> tags = new HashSet<>(MessageTlvRules.permittedTags(version, 4, MessageDirection.SUBMISSION));
            tags.remove(0x0203);
            return Set.copyOf(tags);
        }
        throw new IllegalArgumentException("Unsupported common command");
    }
}
