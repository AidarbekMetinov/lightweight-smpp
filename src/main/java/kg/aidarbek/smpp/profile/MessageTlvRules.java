package kg.aidarbek.smpp.profile;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Specification occurrence tables for the six message commands, including explicit data direction. */
public final class MessageTlvRules {
    private static final Set<Integer> SUBMIT_34 = Set.of(
            0x0005, 0x000d, 0x0019, 0x0030, 0x0201, 0x0202, 0x0203, 0x0204, 0x0205, 0x020a, 0x020b, 0x020c, 0x020d,
            0x020e, 0x020f, 0x0302, 0x0303, 0x0304, 0x0381, 0x0424, 0x0426, 0x0501, 0x1201, 0x1203, 0x1204, 0x130c,
            0x1380, 0x1383);
    private static final Set<Integer> DELIVER_34 = Set.of(
            0x0019, 0x001e, 0x0201, 0x0202, 0x0203, 0x0204, 0x0205, 0x020a, 0x020b, 0x020c, 0x020d, 0x020e, 0x020f,
            0x0381, 0x0423, 0x0424, 0x0427, 0x1383);
    private static final Set<Integer> DATA_34 = Set.of(
            0x0005, 0x0006, 0x0007, 0x0008, 0x000d, 0x000e, 0x000f, 0x0010, 0x0017, 0x0019, 0x001e, 0x0030, 0x0201,
            0x0202, 0x0203, 0x0204, 0x0205, 0x020a, 0x020b, 0x020c, 0x020d, 0x020e, 0x020f, 0x0302, 0x0303, 0x0304,
            0x0381, 0x0421, 0x0423, 0x0424, 0x0426, 0x0427, 0x1201, 0x1203, 0x1204, 0x130c, 0x1380, 0x1383);
    private static final Set<Integer> SUBMIT_50 = union(
            SUBMIT_34,
            Set.of(
                    0x0006, 0x0007, 0x0008, 0x000e, 0x000f, 0x0010, 0x0017, 0x0421, 0x060b, 0x060d, 0x060e, 0x060f,
                    0x0610, 0x0611, 0x0612, 0x0613));
    private static final Set<Integer> DELIVER_50 = union(
            DELIVER_34,
            Set.of(
                    0x0005, 0x000d, 0x0302, 0x0303, 0x0420, 0x0501, 0x060d, 0x060e, 0x060f, 0x0610, 0x0611, 0x0612,
                    0x0613, 0x1380));
    private static final Set<Integer> RESPONSE = Set.of(0x001d, 0x0420, 0x0423, 0x0425);

    private MessageTlvRules() {}

    /**
     * Selects the operation table independently of value interpretation and session permission.
     *
     * @param version explicit specification
     * @param commandId one of the six message command IDs
     * @param dataDirection direction of data_sm requests; responses travel oppositely
     * @return immutable set of tags permitted by that table, independent of value/companion rules
     * @throws IllegalArgumentException for an unsupported command
     */
    public static Set<Integer> permittedTags(SmppVersion version, long commandId, MessageDirection dataDirection) {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(dataDirection, "dataDirection");
        if (commandId == 4) return version == SmppVersion.V3_4 ? SUBMIT_34 : SUBMIT_50;
        if (commandId == 5) return version == SmppVersion.V3_4 ? DELIVER_34 : DELIVER_50;
        if (commandId == 0x103)
            return version == SmppVersion.V3_4
                    ? DATA_34
                    : dataDirection == MessageDirection.SUBMISSION ? SUBMIT_50 : DELIVER_50;
        if (commandId != 0x80000004L && commandId != 0x80000005L && commandId != 0x80000103L)
            throw new IllegalArgumentException("Unsupported message command");
        if (version == SmppVersion.V3_4) return commandId == 0x80000103L ? RESPONSE : Set.of();
        return commandId == 0x80000005L || (commandId == 0x80000103L && dataDirection == MessageDirection.DELIVERY)
                ? Set.of(0x001d, 0x0423, 0x0425, 0x0428)
                : union(RESPONSE, Set.of(0x0428));
    }

    /**
     * Selects occurrence rules while preserving ordered raw storage separately.
     *
     * @param version explicit specification
     * @param commandId one of the six message command IDs
     * @param dataDirection direction of data_sm requests
     * @return required/singleton/repeatable occurrence policy; none of these layouts requires a TLV
     */
    public static TlvRules forCommand(SmppVersion version, long commandId, MessageDirection dataDirection) {
        Set<Integer> permitted = permittedTags(version, commandId, dataDirection);
        Set<Integer> repeatable = new HashSet<>(Set.of(0x0381, 0x0302, 0x0303));
        repeatable.retainAll(permitted);
        return new TlvRules(permitted, Set.of(), repeatable);
    }

    private static Set<Integer> union(Set<Integer> first, Set<Integer> second) {
        Set<Integer> result = new HashSet<>(first);
        result.addAll(second);
        return Set.copyOf(result);
    }
}
