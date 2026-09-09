package kg.aidarbek.smpp.profile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

class ProtocolProfileTest {
    @Test
    void immediateBroadcastOmitsRepetitionAndIgnoresItIfReceived() {
        TlvRules rules = ProtocolProfile.forVersion(SmppVersion.V5_0).broadcastRequestTlvRules(1);
        List<Tlv> parameters = new ArrayList<>(List.of(
                new Tlv(0x0601, new byte[] {0, 0, 0}),
                new Tlv(0x0605, new byte[] {8, 0, 1}),
                new Tlv(0x0606, new byte[] {0, 65})));
        assertDoesNotThrow(() -> rules.validateOutgoing(new OptionalParameters(parameters)));
        parameters.add(new Tlv(0x0604, new byte[] {0, 1}));
        parameters.add(new Tlv(0x0604, new byte[] {0, 2}));
        assertDoesNotThrow(() -> rules.validateIncoming(new OptionalParameters(parameters)));
        assertThrows(IllegalArgumentException.class, () -> rules.validateOutgoing(new OptionalParameters(parameters)));
    }

    @Test
    void broadcastRulesRequireDeclaredParametersAndRetainRepeatedAreas() {
        ProtocolProfile profile = ProtocolProfile.forVersion(SmppVersion.V5_0);
        TlvRules rules = profile.broadcastRequestTlvRules(0);
        List<Tlv> mandatory = List.of(
                new Tlv(0x0601, new byte[] {0, 0, 0}),
                new Tlv(0x0604, new byte[] {0, 1}),
                new Tlv(0x0605, new byte[] {8, 0, 1}),
                new Tlv(0x0606, new byte[] {0, 65}));
        List<Tlv> repeated = new ArrayList<>(mandatory);
        repeated.add(new Tlv(0x0606, new byte[] {0, 66}));
        assertDoesNotThrow(() -> rules.validateOutgoing(new OptionalParameters(repeated)));
        for (Tlv entry : mandatory) {
            List<Tlv> missing = new ArrayList<>(mandatory);
            missing.remove(entry);
            assertThrows(IllegalArgumentException.class, () -> rules.validateIncoming(new OptionalParameters(missing)));
            assertThrows(IllegalArgumentException.class, () -> rules.validateOutgoing(new OptionalParameters(missing)));
            if (entry.tag() != 0x0606) {
                List<Tlv> duplicate = new ArrayList<>(mandatory);
                duplicate.add(entry);
                assertThrows(
                        IllegalArgumentException.class,
                        () -> rules.validateOutgoing(new OptionalParameters(duplicate)));
            }
        }
        for (int callbackTag : new int[] {0x0381, 0x0302, 0x0303}) {
            repeated.add(new Tlv(callbackTag, new byte[] {0}));
            repeated.add(new Tlv(callbackTag, new byte[] {1}));
        }
        assertDoesNotThrow(() -> rules.validateOutgoing(new OptionalParameters(repeated)));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProtocolProfile.forVersion(SmppVersion.V3_4).broadcastRequestTlvRules(0));
        assertThrows(IllegalArgumentException.class, () -> profile.broadcastRequestTlvRules(-1));
        assertThrows(IllegalArgumentException.class, () -> profile.broadcastRequestTlvRules(256));
        assertTrue(profile.tlvRules(0x111).isEmpty());
    }

    @Test
    void appliesBindAndControlResponseOccurrenceRulesToBothProfiles() {
        OptionalParameters version = new OptionalParameters(List.of(new Tlv(0x0210, new byte[] {0x50})));
        OptionalParameters duplicatedVersion = new OptionalParameters(
                List.of(version.entries().get(0), version.entries().get(0)));
        OptionalParameters congestion = new OptionalParameters(List.of(new Tlv(0x0428, new byte[] {100})));
        OptionalParameters absent = new OptionalParameters(List.of());
        for (SmppVersion versionId : SmppVersion.values()) {
            ProtocolProfile profile = ProtocolProfile.forVersion(versionId);
            for (long bindResponse : new long[] {0x80000001L, 0x80000002L, 0x80000009L}) {
                TlvRules rules = profile.tlvRules(bindResponse).orElseThrow();
                assertDoesNotThrow(() -> rules.validateOutgoing(version));
                assertDoesNotThrow(() -> rules.validateIncoming(absent));
                assertThrows(IllegalArgumentException.class, () -> rules.validateOutgoing(duplicatedVersion));
                assertDoesNotThrow(() -> rules.validateIncoming(congestion));
                if (versionId == SmppVersion.V3_4) {
                    assertThrows(IllegalArgumentException.class, () -> rules.validateOutgoing(congestion));
                } else {
                    assertDoesNotThrow(() -> rules.validateOutgoing(congestion));
                }
            }
            for (long response : new long[] {0x80000000L, 0x80000006L, 0x80000015L}) {
                TlvRules rules = profile.tlvRules(response).orElseThrow();
                assertDoesNotThrow(() -> rules.validateOutgoing(absent));
                assertThrows(IllegalArgumentException.class, () -> rules.validateOutgoing(version));
                if (versionId == SmppVersion.V3_4) {
                    assertThrows(IllegalArgumentException.class, () -> rules.validateOutgoing(congestion));
                } else {
                    assertDoesNotThrow(() -> rules.validateOutgoing(congestion));
                }
            }
            assertTrue(profile.tlvRules(0x4).isEmpty());
            assertTrue(profile.tlvRules(0xffffffffL).isEmpty());
            assertTrue(profile.tlvRules(0x1_8000_0001L).isEmpty());
        }
    }

    @Test
    void definesExactTagCatalogueForEachVersionWhileUnknownTagsRemainRaw() {
        Set<Integer> common = Set.of(
                0x0005, 0x0006, 0x0007, 0x0008, 0x000d, 0x000e, 0x000f, 0x0010, 0x0017, 0x0019, 0x001d, 0x001e, 0x0030,
                0x0201, 0x0202, 0x0203, 0x0204, 0x0205, 0x020a, 0x020b, 0x020c, 0x020d, 0x020e, 0x020f, 0x0210, 0x0302,
                0x0303, 0x0304, 0x0381, 0x0420, 0x0421, 0x0422, 0x0423, 0x0424, 0x0425, 0x0426, 0x0427, 0x0501, 0x1201,
                0x1203, 0x1204, 0x130c, 0x1380, 0x1383);
        Set<Integer> version5 = new HashSet<>(common);
        version5.addAll(Set.of(
                0x0428, 0x0600, 0x0601, 0x0602, 0x0603, 0x0604, 0x0605, 0x0606, 0x0607, 0x0608, 0x0609, 0x060a, 0x060b,
                0x060d, 0x060e, 0x060f, 0x0610, 0x0611, 0x0612, 0x0613));
        ProtocolProfile version34 = ProtocolProfile.forVersion(SmppVersion.V3_4);
        ProtocolProfile version50 = ProtocolProfile.forVersion(SmppVersion.V5_0);
        assertEquals(common, version34.definedTags());
        assertEquals(version5, version50.definedTags());
        assertEquals(44, version34.definedTags().size());
        assertEquals(64, version50.definedTags().size());
        assertTrue(version34.definesTag(0x0210));
        assertFalse(version34.definesTag(0x0428));
        assertTrue(version50.definesTag(0x0428));
        assertFalse(version50.definesTag(0x1400));
        assertFalse(version50.definesTag(0xffff));
        assertFalse(version50.definesTag(-1));
        assertFalse(version50.definesTag(0x10210));
        assertThrows(
                UnsupportedOperationException.class,
                () -> version50.definedTags().clear());
    }

    @Test
    void definesExactVersionedCommandInventoryWithoutImplyingCodecSupport() {
        Set<Long> common = Set.of(
                0x1L,
                0x80000001L,
                0x2L,
                0x80000002L,
                0x9L,
                0x80000009L,
                0xbL,
                0x6L,
                0x80000006L,
                0x15L,
                0x80000015L,
                0x80000000L,
                0x4L,
                0x80000004L,
                0x5L,
                0x80000005L,
                0x103L,
                0x80000103L,
                0x3L,
                0x80000003L,
                0x8L,
                0x80000008L,
                0x7L,
                0x80000007L,
                0x21L,
                0x80000021L,
                0x102L);
        Set<Long> version5 = new HashSet<>(common);
        version5.addAll(Set.of(0x111L, 0x80000111L, 0x112L, 0x80000112L, 0x113L, 0x80000113L));
        ProtocolProfile version34 = ProtocolProfile.forVersion(SmppVersion.V3_4);
        ProtocolProfile version50 = ProtocolProfile.forVersion(SmppVersion.V5_0);
        assertEquals(common, version34.definedCommands());
        assertEquals(version5, version50.definedCommands());
        assertEquals(27, version34.definedCommands().size());
        assertEquals(33, version50.definedCommands().size());
        assertEquals(SmppVersion.V3_4, version34.version());
        assertEquals(0x34, version34.version().interfaceVersion());
        assertEquals(0x50, version50.version().interfaceVersion());
        assertFalse(version34.definesCommand(0x111));
        assertTrue(version50.definesCommand(0x111));
        assertFalse(version50.definesCommand(0xffffffffL));
        assertFalse(version50.definesCommand(-1));
        assertFalse(version50.definesCommand(0x1_0000_0001L));
        assertThrows(
                UnsupportedOperationException.class,
                () -> version34.definedCommands().clear());
        assertThrows(NullPointerException.class, () -> ProtocolProfile.forVersion(null));
    }
}
