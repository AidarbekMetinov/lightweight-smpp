package kg.aidarbek.smpp.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;
import kg.aidarbek.smpp.profile.SmppVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class VersionNegotiationTest {
    @ParameterizedTest
    @EnumSource(SmppVersion.class)
    void matchingAdvertisementEnablesOnlyTheRequestedProfile(SmppVersion requested) {
        VersionNegotiation result =
                VersionNegotiation.forEsme(requested, OptionalInt.of(requested.interfaceVersion()), false);
        assertEquals(requested.interfaceVersion(), result.requestedInterfaceVersion());
        assertEquals(OptionalInt.of(requested.interfaceVersion()), result.advertisement());
        assertEquals(VersionNegotiation.Outcome.VERIFIED, result.outcome());
        assertEquals(requested, result.effectiveProfile().orElseThrow().version());
        assertTrue(result.permits(new SendRequirements(requested, true)));
        assertEquals(requested == SmppVersion.V5_0, result.permits(new SendRequirements(SmppVersion.V5_0, false)));
    }

    @ParameterizedTest
    @EnumSource(SmppVersion.class)
    void missingAdvertisementRestrictsBothTlvsAndVersionSpecificFields(SmppVersion requested) {
        VersionNegotiation result = VersionNegotiation.forEsme(requested, OptionalInt.empty(), false);
        assertEquals(requested.interfaceVersion(), result.requestedInterfaceVersion());
        assertEquals(OptionalInt.empty(), result.advertisement());
        assertEquals(VersionNegotiation.Outcome.COMMON_WITHOUT_TLVS, result.outcome());
        assertEquals(SmppVersion.V3_4, result.effectiveProfile().orElseThrow().version());
        assertTrue(result.permits(SendRequirements.COMMON));
        assertFalse(result.permits(new SendRequirements(SmppVersion.V3_4, true)));
        assertFalse(result.permits(new SendRequirements(SmppVersion.V5_0, false)));
        assertFalse(result.effectiveProfile().orElseThrow().definesCommand(0x111));
        VersionNegotiation strict = VersionNegotiation.forEsme(requested, OptionalInt.empty(), true);
        assertEquals(VersionNegotiation.Outcome.ADVERTISEMENT_REQUIRED, strict.outcome());
        assertTrue(strict.effectiveProfile().isEmpty());
        assertFalse(strict.permits(SendRequirements.COMMON));
    }

    @Test
    void aNewerAdvertisementDoesNotUpgradeAndAnOlderOneDoesNotDowngrade() {
        VersionNegotiation olderRequest = VersionNegotiation.forEsme(SmppVersion.V3_4, OptionalInt.of(0x50), false);
        assertEquals(VersionNegotiation.Outcome.VERIFIED, olderRequest.outcome());
        assertEquals(
                SmppVersion.V3_4, olderRequest.effectiveProfile().orElseThrow().version());
        assertFalse(olderRequest.permits(new SendRequirements(SmppVersion.V5_0, false)));
        VersionNegotiation newerRequest = VersionNegotiation.forEsme(SmppVersion.V5_0, OptionalInt.of(0x34), false);
        assertEquals(VersionNegotiation.Outcome.PEER_VERSION_TOO_OLD, newerRequest.outcome());
        assertEquals(OptionalInt.of(0x34), newerRequest.advertisement());
        assertTrue(newerRequest.effectiveProfile().isEmpty());
        assertFalse(newerRequest.permits(SendRequirements.COMMON));
    }

    @ParameterizedTest
    @EnumSource(SmppVersion.class)
    void unknownAdvertisementIsRetainedAndRefused(SmppVersion requested) {
        for (int raw : new int[] {0, 0x33, 0x35, 0x51, 0xff}) {
            VersionNegotiation result = VersionNegotiation.forEsme(requested, OptionalInt.of(raw), false);
            assertEquals(VersionNegotiation.Outcome.UNSUPPORTED_PEER_VERSION, result.outcome());
            assertEquals(OptionalInt.of(raw), result.advertisement());
            assertTrue(result.effectiveProfile().isEmpty());
            assertFalse(result.permits(SendRequirements.COMMON));
        }
    }

    @Test
    void versionAndRequirementInputsHaveExplicitBounds() {
        assertThrows(NullPointerException.class, () -> new SendRequirements(null, false));
        assertEquals(SendRequirements.COMMON, new SendRequirements(SmppVersion.V3_4, false));
        assertEquals(SendRequirements.COMMON.hashCode(), new SendRequirements(SmppVersion.V3_4, false).hashCode());
        assertThrows(NullPointerException.class, () -> VersionNegotiation.forEsme(null, OptionalInt.empty(), false));
        assertThrows(NullPointerException.class, () -> VersionNegotiation.forEsme(SmppVersion.V3_4, null, false));
        for (int raw : new int[] {-1, 256}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> VersionNegotiation.forEsme(SmppVersion.V3_4, OptionalInt.of(raw), false));
        }
        assertThrows(
                NullPointerException.class,
                () -> VersionNegotiation.forEsme(SmppVersion.V3_4, OptionalInt.empty(), false)
                        .permits(null));
    }

    @ParameterizedTest
    @EnumSource(SmppVersion.class)
    void serverAcceptanceAndAdvertisementDoNotReplaceTheReceivedRequest(SmppVersion requested) {
        Set<SmppVersion> accepted = new HashSet<>(Set.of(requested));
        VersionNegotiation result =
                VersionNegotiation.forMessageCenter(requested.interfaceVersion(), accepted, SmppVersion.V5_0);
        accepted.clear();
        assertEquals(requested.interfaceVersion(), result.requestedInterfaceVersion());
        assertEquals(OptionalInt.of(0x50), result.advertisement());
        assertEquals(VersionNegotiation.Outcome.VERIFIED, result.outcome());
        assertEquals(requested, result.effectiveProfile().orElseThrow().version());
        assertTrue(result.permits(new SendRequirements(requested, true)));
        for (int rejected : new int[] {0, 0xff, requested == SmppVersion.V3_4 ? 0x50 : 0x34}) {
            VersionNegotiation refusal =
                    VersionNegotiation.forMessageCenter(rejected, Set.of(requested), SmppVersion.V5_0);
            assertEquals(rejected, refusal.requestedInterfaceVersion());
            assertEquals(OptionalInt.of(0x50), refusal.advertisement());
            assertEquals(VersionNegotiation.Outcome.REQUESTED_VERSION_NOT_ACCEPTED, refusal.outcome());
            assertTrue(refusal.effectiveProfile().isEmpty());
            assertFalse(refusal.permits(SendRequirements.COMMON));
        }
    }

    @Test
    void serverPolicyRejectsInconsistentOrEmptyConfiguration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> VersionNegotiation.forMessageCenter(0x34, Set.of(), SmppVersion.V3_4));
        assertThrows(
                IllegalArgumentException.class,
                () -> VersionNegotiation.forMessageCenter(0x50, Set.of(SmppVersion.V5_0), SmppVersion.V3_4));
        for (int raw : new int[] {-1, 256}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> VersionNegotiation.forMessageCenter(raw, Set.of(SmppVersion.V3_4), SmppVersion.V3_4));
        }
        assertThrows(
                NullPointerException.class, () -> VersionNegotiation.forMessageCenter(0x34, null, SmppVersion.V3_4));
        assertThrows(
                NullPointerException.class,
                () -> VersionNegotiation.forMessageCenter(0x34, Set.of(SmppVersion.V3_4), null));
    }
}
