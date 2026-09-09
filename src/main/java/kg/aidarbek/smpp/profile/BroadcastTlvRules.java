package kg.aidarbek.smpp.profile;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Exact SMPP 5.0 broadcast optional-parameter occurrence rules. */
public final class BroadcastTlvRules {
    private BroadcastTlvRules() {}
    /** Returns the supported tag set for this exact command and priority context.
     * @param version explicit SMPP profile
     * @param commandId one of the six broadcast command identities
     * @param priorityFlag unsigned priority; significant only for broadcast_sm
     * @return immutable permitted tags
     * @throws IllegalArgumentException for unsupported profile, identity or priority */
    public static Set<Integer> permittedTags(SmppVersion version, long commandId, int priorityFlag) {
        Objects.requireNonNull(version, "version");
        if (version != SmppVersion.V5_0 || priorityFlag < 0 || priorityFlag > 255)
            throw new IllegalArgumentException("Broadcast context requires SMPP 5.0 and an unsigned priority");
        if (commandId == 0x111) {
            Set<Integer> tags = new HashSet<>(Set.of(
                    0x0601, 0x0604, 0x0605, 0x0606, 0x130c, 0x0600, 0x0602, 0x0603, 0x060a, 0x0381, 0x0303, 0x0302,
                    0x0005, 0x0203, 0x020b, 0x1201, 0x020d, 0x0424, 0x1204, 0x0019, 0x0201, 0x1203, 0x000d, 0x020a,
                    0x0202, 0x0204));
            if (priorityFlag == 1) tags.remove(0x0604);
            return Set.copyOf(tags);
        }
        if (commandId == 0x80000111L) return Set.of(0x0606, 0x0607, 0x0428);
        if (commandId == 0x112) return Set.of(0x0204);
        if (commandId == 0x80000112L) return Set.of(0x0427, 0x0606, 0x0608, 0x0609, 0x0204, 0x0428);
        if (commandId == 0x113) return Set.of(0x0601, 0x0204);
        if (commandId == 0x80000113L) return Set.of(0x0428);
        throw new IllegalArgumentException("Unsupported broadcast command");
    }
    /** Returns required, singleton and repeated occurrence rules.
     * @param version explicit SMPP profile
     * @param commandId exact broadcast identity
     * @param priorityFlag broadcast priority
     * @return immutable rules; status-specific body omission is separate */
    public static TlvRules forCommand(SmppVersion version, long commandId, int priorityFlag) {
        Set<Integer> permitted = permittedTags(version, commandId, priorityFlag);
        Set<Integer> required = commandId == 0x111
                ? (priorityFlag == 1 ? Set.of(0x0601, 0x0605, 0x0606) : Set.of(0x0601, 0x0604, 0x0605, 0x0606))
                : commandId == 0x80000112L ? Set.of(0x0427, 0x0606, 0x0608) : Set.of();
        Set<Integer> repeated = commandId == 0x111
                ? Set.of(0x0606, 0x0381, 0x0302, 0x0303)
                : commandId == 0x80000111L
                        ? Set.of(0x0606, 0x0607)
                        : commandId == 0x80000112L ? Set.of(0x0606, 0x0608) : Set.of();
        return new TlvRules(permitted, required, repeated);
    }
}
