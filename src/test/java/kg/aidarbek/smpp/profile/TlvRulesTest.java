package kg.aidarbek.smpp.profile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

class TlvRulesTest {
    @Test
    void rejectsInconsistentOrInvalidRuleDeclarations() {
        assertThrows(IllegalArgumentException.class, () -> new TlvRules(Set.of(1), Set.of(2), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new TlvRules(Set.of(1), Set.of(), Set.of(2)));
        assertThrows(IllegalArgumentException.class, () -> new TlvRules(Set.of(-1), Set.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new TlvRules(Set.of(65536), Set.of(), Set.of()));
        assertThrows(NullPointerException.class, () -> new TlvRules(null, Set.of(), Set.of()));
        assertDoesNotThrow(
                () -> new TlvRules(Set.of(), Set.of(), Set.of()).validateOutgoing(new OptionalParameters(List.of())));
    }

    @Test
    void enforcesRequiredAndSingletonOccurrencesButRetainsIncomingExtensions() {
        Set<Integer> permitted = new HashSet<>(Set.of(0x0601, 0x0606));
        TlvRules rules = new TlvRules(permitted, Set.of(0x0601, 0x0606), Set.of(0x0606));
        permitted.clear();
        Tlv content = new Tlv(0x0601, new byte[0]);
        Tlv area = new Tlv(0x0606, new byte[0]);
        OptionalParameters valid = new OptionalParameters(List.of(content, area, area));
        assertDoesNotThrow(() -> rules.validateOutgoing(valid));
        assertDoesNotThrow(() -> rules.validateIncoming(valid));
        assertThrows(
                IllegalArgumentException.class, () -> rules.validateOutgoing(new OptionalParameters(List.of(area))));
        assertThrows(
                IllegalArgumentException.class, () -> rules.validateIncoming(new OptionalParameters(List.of(content))));
        OptionalParameters duplicate = new OptionalParameters(List.of(content, content, area));
        assertThrows(IllegalArgumentException.class, () -> rules.validateOutgoing(duplicate));
        assertThrows(IllegalArgumentException.class, () -> rules.validateIncoming(duplicate));
        OptionalParameters extended = new OptionalParameters(List.of(
                content,
                area,
                new Tlv(0x1400, new byte[] {1}),
                new Tlv(0x1400, new byte[] {2}),
                new Tlv(0x0210, new byte[] {0x50})));
        assertDoesNotThrow(() -> rules.validateIncoming(extended));
        assertThrows(IllegalArgumentException.class, () -> rules.validateOutgoing(extended));
    }
}
