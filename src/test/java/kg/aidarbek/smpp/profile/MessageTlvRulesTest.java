package kg.aidarbek.smpp.profile;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

class MessageTlvRulesTest {
    @Test
    void exactMessageTablesMatchBothSpecificationProfiles() {
        String submit34 =
                "0204 020a 000d 020b 0005 020c 020e 020f 0426 0019 0424 0201 0381 0302 0303 0202 0203 0205 1201 1203 1204 0030 0304 130c 020d 1380 1383 0501";
        String deliver34 = "0204 020a 020b 020c 020e 020f 0205 0201 0019 0424 0381 0202 0203 020d 1383 0423 0427 001e";
        String data34 =
                "020a 000d 000e 000f 0010 020b 0005 0006 0007 0008 020c 020e 020f 0426 0017 0019 0424 0421 001e 0427 0423 0204 0201 0381 0302 0303 0202 0203 0205 1201 1203 1204 0030 0304 130c 020d 1380 1383";
        String submit50 =
                "130c 060b 0381 0303 0302 0613 0612 0611 0005 0007 060e 0006 0610 0203 0008 020b 1201 1380 1383 020d 0424 0426 0030 1204 0304 0019 0201 0017 020c 020f 020e 0421 1203 000d 000f 060d 000e 060f 020a 0202 0010 0204 0205 0501";
        String deliver50 =
                "0381 0303 0302 0613 0612 0611 0005 060e 0610 0203 020b 0420 1380 1383 020d 0424 0427 0423 0019 0201 001e 020c 020f 020e 000d 060d 060f 020a 0202 0204 0205 0501";
        for (MessageDirection direction : MessageDirection.values()) {
            assertTable(SmppVersion.V3_4, 4, direction, submit34);
            assertTable(SmppVersion.V3_4, 5, direction, deliver34);
            assertTable(SmppVersion.V3_4, 0x103, direction, data34);
            assertTable(SmppVersion.V3_4, 0x80000004L, direction, "");
            assertTable(SmppVersion.V3_4, 0x80000005L, direction, "");
            assertTable(SmppVersion.V3_4, 0x80000103L, direction, "0425 0423 001d 0420");
            assertTable(SmppVersion.V5_0, 4, direction, submit50);
            assertTable(SmppVersion.V5_0, 5, direction, deliver50);
            assertTable(
                    SmppVersion.V5_0,
                    0x103,
                    direction,
                    direction == MessageDirection.SUBMISSION ? submit50 : deliver50);
            assertTable(SmppVersion.V5_0, 0x80000004L, direction, "0425 0423 001d 0420 0428");
            assertTable(SmppVersion.V5_0, 0x80000005L, direction, "0425 0423 001d 0428");
            assertTable(
                    SmppVersion.V5_0,
                    0x80000103L,
                    direction,
                    direction == MessageDirection.SUBMISSION ? "0425 0423 001d 0420 0428" : "0425 0423 001d 0428");
        }
    }

    @Test
    void callbacksRepeatButOrdinaryKnownTagsAreSingletonsAndUnexpectedIncomingTagsAreRaw() {
        Tlv callback = new Tlv(0x0381, new byte[] {1, 1, 1, 49});
        OptionalParameters callbacks = new OptionalParameters(List.of(callback, callback));
        TlvRules rules = MessageTlvRules.forCommand(SmppVersion.V5_0, 5, MessageDirection.DELIVERY);
        assertDoesNotThrow(() -> rules.validateOutgoing(callbacks));
        Tlv payload = new Tlv(0x0424, new byte[] {1});
        assertThrows(
                IllegalArgumentException.class,
                () -> rules.validateIncoming(new OptionalParameters(List.of(payload, payload))));
        Tlv unknown = new Tlv(0x7fff, new byte[0]);
        assertDoesNotThrow(() -> rules.validateIncoming(new OptionalParameters(List.of(unknown, unknown))));
        assertThrows(
                IllegalArgumentException.class, () -> rules.validateOutgoing(new OptionalParameters(List.of(unknown))));
    }

    private static void assertTable(SmppVersion version, long id, MessageDirection direction, String tags) {
        Set<Integer> expected = tags.isEmpty()
                ? Set.of()
                : Arrays.stream(tags.split(" "))
                        .map(v -> Integer.parseInt(v, 16))
                        .collect(Collectors.toSet());
        assertEquals(
                expected,
                MessageTlvRules.permittedTags(version, id, direction),
                version + " " + Long.toHexString(id) + " " + direction);
    }
}
