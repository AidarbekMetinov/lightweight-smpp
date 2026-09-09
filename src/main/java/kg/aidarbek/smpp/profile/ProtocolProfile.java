package kg.aidarbek.smpp.profile;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable, thread-safe specification catalogue and the currently implemented TLV occurrence rules.
 *
 * <p>Catalogue membership identifies definitions in SMPP 3.4 or 5.0; it does not advertise an
 * implemented command codec, supported field semantics, endpoint permission or peer capability.
 * Request/response commands are separate unsigned identities. Version negotiation is outside this type.
 */
public final class ProtocolProfile {
    private static final Set<Long> COMMON_COMMANDS = Set.of(
            0x00000001L,
            0x80000001L,
            0x00000002L,
            0x80000002L,
            0x00000003L,
            0x80000003L,
            0x00000004L,
            0x80000004L,
            0x00000005L,
            0x80000005L,
            0x00000006L,
            0x80000006L,
            0x00000007L,
            0x80000007L,
            0x00000008L,
            0x80000008L,
            0x00000009L,
            0x80000009L,
            0x0000000bL,
            0x00000015L,
            0x80000015L,
            0x00000021L,
            0x80000021L,
            0x00000102L,
            0x00000103L,
            0x80000103L,
            0x80000000L);
    private static final Set<Long> VERSION_5_COMMANDS = withAdditional(
            COMMON_COMMANDS, Set.of(0x00000111L, 0x80000111L, 0x00000112L, 0x80000112L, 0x00000113L, 0x80000113L));
    private static final Set<Integer> COMMON_TAGS = Set.of(
            0x0005, 0x0006, 0x0007, 0x0008, 0x000d, 0x000e, 0x000f, 0x0010, 0x0017, 0x0019, 0x001d, 0x001e, 0x0030,
            0x0201, 0x0202, 0x0203, 0x0204, 0x0205, 0x020a, 0x020b, 0x020c, 0x020d, 0x020e, 0x020f, 0x0210, 0x0302,
            0x0303, 0x0304, 0x0381, 0x0420, 0x0421, 0x0422, 0x0423, 0x0424, 0x0425, 0x0426, 0x0427, 0x0501, 0x1201,
            0x1203, 0x1204, 0x130c, 0x1380, 0x1383);
    private static final Set<Integer> VERSION_5_TAGS = withAdditional(
            COMMON_TAGS,
            Set.of(
                    0x0428, 0x0600, 0x0601, 0x0602, 0x0603, 0x0604, 0x0605, 0x0606, 0x0607, 0x0608, 0x0609, 0x060a,
                    0x060b, 0x060d, 0x060e, 0x060f, 0x0610, 0x0611, 0x0612, 0x0613));
    private final SmppVersion version;

    private ProtocolProfile(SmppVersion version) {
        this.version = Objects.requireNonNull(version, "version");
    }

    /**
     * Selects a profile explicitly, without inferring or negotiating a peer version.
     *
     * @param version non-null specification identity
     * @return an immutable descriptor for the chosen version
     */
    public static ProtocolProfile forVersion(SmppVersion version) {
        return new ProtocolProfile(version);
    }

    /**
     * Returns the selected specification identity.
     *
     * @return the selected specification identity
     */
    public SmppVersion version() {
        return version;
    }

    /**
     * Returns unmodifiable unsigned command IDs: 27 in 3.4, 33 in 5.0.
     *
     * @return unmodifiable unsigned command IDs: 27 in 3.4, 33 in 5.0
     */
    public Set<Long> definedCommands() {
        return version == SmppVersion.V3_4 ? COMMON_COMMANDS : VERSION_5_COMMANDS;
    }

    /**
     * Returns unmodifiable distinct tag catalogue: 44 in 3.4, 64 in 5.0.
     *
     * @return unmodifiable distinct tag catalogue: 44 in 3.4, 64 in 5.0
     */
    public Set<Integer> definedTags() {
        return version == SmppVersion.V3_4 ? COMMON_TAGS : VERSION_5_TAGS;
    }

    /**
     * Tests exact numeric membership without truncating wider or signed inputs.
     *
     * @param commandId proposed unsigned command ID
     * @return whether this specification defines the command, independent of codec support
     */
    public boolean definesCommand(long commandId) {
        return definedCommands().contains(commandId);
    }

    /**
     * Tests exact tag membership; an unknown tag can still be preserved as a raw value.
     *
     * @param tag proposed unsigned tag
     * @return whether this specification defines the tag, independent of typed support
     */
    public boolean definesTag(int tag) {
        return definedTags().contains(tag);
    }

    /**
     * Returns occurrence rules currently provided for the three bind responses, unbind_resp,
     * enquire_link_resp and generic_nack. SMPP 5.0 adds congestion_state to these contexts.
     *
     * <p>Absence means rules are not implemented here and must not be treated as permission to send.
     * Broadcast request rules need explicit priority context and have a separate method. These rules
     * do not decide response body omission, value validity, role, state or successful-bind advertising policy.
     *
     * @param commandId exact unsigned command ID
     * @return current occurrence rules, or empty for unknown and other command contexts
     */
    public Optional<TlvRules> tlvRules(long commandId) {
        if (!definesCommand(commandId)) {
            return Optional.empty();
        }
        boolean bindResponse = commandId == 0x80000001L || commandId == 0x80000002L || commandId == 0x80000009L;
        boolean controlResponse = commandId == 0x80000000L || commandId == 0x80000006L || commandId == 0x80000015L;
        if (bindResponse || controlResponse) {
            Set<Integer> permitted = new HashSet<>();
            if (bindResponse) {
                permitted.add(0x0210);
            }
            if (version == SmppVersion.V5_0) {
                permitted.add(0x0428);
            }
            return Optional.of(new TlvRules(permitted, Set.of(), Set.of()));
        }
        return Optional.empty();
    }

    /**
     * Returns SMPP 5.0 broadcast_sm occurrence rules from sections 4.4.1.1, 4.4.2 and 4.8.4.13.
     * Area, content type and frequency are required; repetition is required unless priority is 1.
     * At priority 1, repetition is rejected on output and ignored on input. Areas and the three
     * callback tags can repeat.
     *
     * <p>Only the priority octet width is checked here. Network-specific priority legality, TLV
     * content and length, callback correlations and other cross-field conditions require command validation.
     *
     * @param priorityFlag raw unsigned priority octet from the broadcast request
     * @return immutable occurrence rules for that request context
     * @throws IllegalArgumentException for a 3.4 profile or priority outside 0..255
     */
    public TlvRules broadcastRequestTlvRules(int priorityFlag) {
        return BroadcastTlvRules.forCommand(version, 0x111, priorityFlag);
    }

    private static <T> Set<T> withAdditional(Set<T> common, Set<T> additions) {
        Set<T> values = new HashSet<>(common);
        values.addAll(additions);
        return Set.copyOf(values);
    }
}
