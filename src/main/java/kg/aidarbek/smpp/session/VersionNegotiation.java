package kg.aidarbek.smpp.session;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;

/**
 * Immutable, thread-safe version-policy result, separate from raw bind values and credentials.
 *
 * <p>Outcomes follow the explicit policy in docs/API.md. An effective profile describes implemented
 * version semantics; it does not advertise local command codecs or application services. Results
 * retain raw requested and advertised identities without silently upgrading or downgrading them.
 * This final snapshot has identity equality; callers compare its explicit fields when needed.
 */
public final class VersionNegotiation {
    /** Explicit version outcomes; failure outcomes never supply an effective profile. */
    public enum Outcome {
        /** Known advertisement meets the explicit requested version. */
        VERIFIED,
        /** Missing advertisement permits only common 3.4-compatible non-TLV operations and values. */
        COMMON_WITHOUT_TLVS,
        /** A caller required a recognized advertisement and none was present. */
        ADVERTISEMENT_REQUIRED,
        /** The received advertisement is not an implemented version. */
        UNSUPPORTED_PEER_VERSION,
        /** The known advertisement is older than the explicit requested version. */
        PEER_VERSION_TOO_OLD,
        /** The message center does not accept the requested version. */
        REQUESTED_VERSION_NOT_ACCEPTED
    }

    private final int requestedInterfaceVersion;
    private final OptionalInt advertisement;
    private final Outcome outcome;
    private final ProtocolProfile effectiveProfile;

    private VersionNegotiation(int requested, OptionalInt advertisement, Outcome outcome, SmppVersion effective) {
        this.requestedInterfaceVersion = requested;
        this.advertisement = advertisement;
        this.outcome = outcome;
        this.effectiveProfile = effective == null ? null : ProtocolProfile.forVersion(effective);
    }

    /**
     * Applies the documented ESME version policy without rebinding or implicit downgrade.
     *
     * @param requested explicit requested version
     * @param advertisement raw optional sc_interface_version octet
     * @param requireAdvertisement whether missing advertisement must fail binding
     * @return immutable version decision
     * @throws NullPointerException if requested or advertisement is null
     * @throws IllegalArgumentException if a present advertisement is outside 0..255
     */
    public static VersionNegotiation forEsme(
            SmppVersion requested, OptionalInt advertisement, boolean requireAdvertisement) {
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(advertisement, "advertisement");
        if (advertisement.isEmpty()) {
            return new VersionNegotiation(
                    requested.interfaceVersion(),
                    advertisement,
                    requireAdvertisement ? Outcome.ADVERTISEMENT_REQUIRED : Outcome.COMMON_WITHOUT_TLVS,
                    requireAdvertisement ? null : SmppVersion.V3_4);
        }
        int raw = advertisement.getAsInt();
        requireOctet(raw);
        if (knownVersion(raw).isEmpty()) {
            return new VersionNegotiation(
                    requested.interfaceVersion(), advertisement, Outcome.UNSUPPORTED_PEER_VERSION, null);
        }
        if (raw < requested.interfaceVersion()) {
            return new VersionNegotiation(
                    requested.interfaceVersion(), advertisement, Outcome.PEER_VERSION_TOO_OLD, null);
        }
        return new VersionNegotiation(requested.interfaceVersion(), advertisement, Outcome.VERIFIED, requested);
    }

    /**
     * Evaluates a received bind version against an explicit message-center acceptance policy.
     *
     * @param requestedInterfaceVersion received unsigned interface-version octet
     * @param acceptedVersions nonempty explicitly accepted version set
     * @param advertisedVersion implemented version placed in successful bind responses
     * @return result retaining the received request and the configured advertisement separately
     * @throws NullPointerException if acceptedVersions, any member or advertisedVersion is null
     * @throws IllegalArgumentException if the requested octet is out of range, the set is empty,
     *     or an accepted version is newer than the advertised version
     */
    public static VersionNegotiation forMessageCenter(
            int requestedInterfaceVersion, Set<SmppVersion> acceptedVersions, SmppVersion advertisedVersion) {
        requireOctet(requestedInterfaceVersion);
        Set<SmppVersion> accepted = validateAcceptedVersions(acceptedVersions, advertisedVersion);
        OptionalInt advertisement = OptionalInt.of(advertisedVersion.interfaceVersion());
        Optional<SmppVersion> requested = knownVersion(requestedInterfaceVersion);
        if (requested.isEmpty() || !accepted.contains(requested.orElseThrow())) {
            return new VersionNegotiation(
                    requestedInterfaceVersion, advertisement, Outcome.REQUESTED_VERSION_NOT_ACCEPTED, null);
        }
        return new VersionNegotiation(
                requestedInterfaceVersion, advertisement, Outcome.VERIFIED, requested.orElseThrow());
    }

    static Set<SmppVersion> validateAcceptedVersions(Set<SmppVersion> acceptedVersions, SmppVersion advertisedVersion) {
        Objects.requireNonNull(advertisedVersion, "advertisedVersion");
        Set<SmppVersion> accepted = Set.copyOf(acceptedVersions);
        if (accepted.isEmpty()
                || accepted.stream()
                        .anyMatch(version -> version.interfaceVersion() > advertisedVersion.interfaceVersion())) {
            throw new IllegalArgumentException(
                    "Accepted versions must be nonempty and covered by the advertised version");
        }
        return accepted;
    }

    /**
     * Returns the original requested interface-version octet.
     *
     * @return unsigned requested octet
     */
    public int requestedInterfaceVersion() {
        return requestedInterfaceVersion;
    }

    /**
     * Returns the original advertisement, including unknown values or absence.
     *
     * @return immutable raw optional octet
     */
    public OptionalInt advertisement() {
        return advertisement;
    }

    /**
     * Returns the explicit policy outcome.
     *
     * @return policy outcome
     */
    public Outcome outcome() {
        return outcome;
    }

    /**
     * Returns the usable profile, if the version policy permits binding.
     *
     * @return empty on any version failure
     */
    public Optional<ProtocolProfile> effectiveProfile() {
        return Optional.ofNullable(effectiveProfile);
    }

    /**
     * Tests feature requirements against the effective profile and advertisement confidence.
     *
     * @param requirements validated command field requirements
     * @return whether all requirements are supported by this version result
     * @throws NullPointerException if requirements is null
     */
    public boolean permits(SendRequirements requirements) {
        Objects.requireNonNull(requirements, "requirements");
        return effectiveProfile != null
                && requirements.minimumVersion().interfaceVersion()
                        <= effectiveProfile.version().interfaceVersion()
                && (!requirements.usesOptionalParameters() || outcome == Outcome.VERIFIED);
    }

    private static Optional<SmppVersion> knownVersion(int raw) {
        return switch (raw) {
            case 0x34 -> Optional.of(SmppVersion.V3_4);
            case 0x50 -> Optional.of(SmppVersion.V5_0);
            default -> Optional.empty();
        };
    }

    private static void requireOctet(int raw) {
        if (raw < 0 || raw > 0xff) {
            throw new IllegalArgumentException("Interface version must be an unsigned octet");
        }
    }
}
