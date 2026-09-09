package kg.aidarbek.smpp.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kg.aidarbek.smpp.profile.BroadcastTlvRules;
import kg.aidarbek.smpp.profile.CommonTlvRules;
import kg.aidarbek.smpp.profile.MessageDirection;
import kg.aidarbek.smpp.profile.MessageTlvRules;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import org.junit.jupiter.api.Test;

class Smpp5InventoryTest {
    @Test
    void implementedCodecsAndContextTablesCoverEveryDeclaredCommandAndTag() {
        for (SmppVersion version : SmppVersion.values()) {
            ProtocolProfile profile = ProtocolProfile.forVersion(version);
            List<CommandCodec<?>> codecs = new ArrayList<>(ControlCommandCodecs.all());
            codecs.addAll(MessageCommandCodecs.all(MessageDirection.SUBMISSION));
            codecs.addAll(CommonCommandCodecs.all());
            if (version == SmppVersion.V5_0) codecs.addAll(BroadcastCommandCodecs.all());
            Set<Long> ids = new HashSet<>();
            for (CommandCodec<?> codec : codecs) assertTrue(ids.add(codec.commandId()));
            assertEquals(profile.definedCommands(), ids);
            assertEquals(version == SmppVersion.V5_0 ? 33 : 27, ids.size());
            Set<Integer> tags = new HashSet<>(Set.of(0x0210));
            for (long command : new long[] {4, 5, 0x103, 0x80000004L, 0x80000005L, 0x80000103L})
                for (MessageDirection direction : MessageDirection.values())
                    tags.addAll(MessageTlvRules.permittedTags(version, command, direction));
            for (long command :
                    new long[] {3, 7, 8, 0x21, 0x0b, 0x102, 0x80000003L, 0x80000007L, 0x80000008L, 0x80000021L})
                tags.addAll(CommonTlvRules.permittedTags(version, command));
            if (version == SmppVersion.V5_0)
                for (long command : new long[] {0x111, 0x112, 0x113, 0x80000111L, 0x80000112L, 0x80000113L})
                    tags.addAll(BroadcastTlvRules.permittedTags(version, command, 0));
            assertEquals(profile.definedTags(), tags);
            assertEquals(version == SmppVersion.V5_0 ? 64 : 44, tags.size());
        }
    }
}
