package kg.aidarbek.smpp.codec;

import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.LIMITS;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.REQUIRED;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.SOURCE;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.V5;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.broadcast;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.codec;
import static kg.aidarbek.smpp.codec.BroadcastOptionalParametersTest.append;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BroadcastSm;
import kg.aidarbek.smpp.protocol.CancelBroadcastSm;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

class BroadcastExtensionTest {
    @Test
    void explicitVendorContextsAreImmutableAndCannotReplaceStandardsOrLeakPermission() {
        BroadcastTlvExtension extension = new BroadcastTlvExtension(
                SmppVersion.V5_0, 0x111, 0x1500, new MessageTlvValueCodec(SmppVersion.V5_0, 0x0424), true);
        ArrayList<BroadcastTlvExtension> supplied = new ArrayList<>(List.of(extension));
        PduCodec configured = new PduCodec(BroadcastCommandCodecs.all(supplied), LIMITS);
        supplied.clear();
        OptionalParameters data = append(REQUIRED, new Tlv(0x1500, new byte[] {1}), new Tlv(0x1500, new byte[] {2}));
        Pdu<BroadcastSm> pdu = new Pdu<>(0, 1, broadcast(data));
        byte[] frame = configured.encode(pdu, V5);
        assertEquals(pdu, configured.decode(frame, V5));
        assertEquals(pdu, codec().decode(frame, V5));
        assertThrows(IllegalArgumentException.class, () -> codec().encode(pdu, V5));
        assertThrows(
                IllegalArgumentException.class,
                () -> configured.encode(
                        new Pdu<>(
                                0,
                                1,
                                new CancelBroadcastSm(
                                        "", "id", SOURCE, new OptionalParameters(List.of(new Tlv(0x1500, new byte[] {1
                                        }))))),
                        V5));
        assertEquals(
                Optional.of(new OctetString(new byte[] {1})),
                BroadcastCommandCodecs.tlvRegistry(List.of(extension))
                        .decode(SmppVersion.V5_0, 0x111, new Tlv(0x1500, new byte[] {1}), OctetString.class));
        assertThrows(IllegalArgumentException.class, () -> BroadcastCommandCodecs.all(List.of(extension, extension)));
        BroadcastTlvExtension singleton =
                new BroadcastTlvExtension(SmppVersion.V5_0, 0x111, 0x1500, extension.codec(), false);
        PduCodec single = new PduCodec(BroadcastCommandCodecs.all(List.of(singleton)), LIMITS);
        assertThrows(IllegalArgumentException.class, () -> single.encode(pdu, V5));
        assertThrows(IllegalArgumentException.class, () -> single.decode(frame, V5));
        for (int tag : new int[] {0x0600, 0x13ff, 0x4000, -1})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new BroadcastTlvExtension(SmppVersion.V5_0, 0x111, tag, extension.codec(), false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BroadcastTlvExtension(SmppVersion.V3_4, 0x111, 0x1500, extension.codec(), false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new BroadcastTlvExtension(SmppVersion.V5_0, 4, 0x1500, extension.codec(), false));
    }
}
