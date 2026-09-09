package kg.aidarbek.smpp.codec;

import static kg.aidarbek.smpp.codec.CommonCommandCodecsTest.LIMITS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.AlertNotification;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.QuerySm;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

class CommonExtensionTest {
    @Test
    void vendorPermissionIsAnExplicitImmutableCommandAndProfileComposition() {
        var extension = new CommonTlvExtension(
                SmppVersion.V5_0, 0x102, 0x1500, new MessageTlvValueCodec(SmppVersion.V5_0, 0x0424), true);
        java.util.ArrayList<CommonTlvExtension> supplied = new java.util.ArrayList<>(List.of(extension));
        PduCodec configured = new PduCodec(CommonCommandCodecs.all(supplied), LIMITS);
        supplied.clear();
        OptionalParameters parameters =
                new OptionalParameters(List.of(new Tlv(0x1500, new byte[] {1}), new Tlv(0x1500, new byte[] {2})));
        AlertNotification alert = new AlertNotification(new Address(0, 0, ""), new Address(0, 0, ""), parameters);
        ProtocolProfile profile = ProtocolProfile.forVersion(SmppVersion.V5_0);
        Pdu<AlertNotification> pdu = new Pdu<>(0, 1, alert);
        byte[] frame = configured.encode(pdu, profile);
        assertEquals(pdu, configured.decode(frame, profile));
        assertEquals(pdu, new PduCodec(CommonCommandCodecs.all(), LIMITS).decode(frame, profile));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PduCodec(CommonCommandCodecs.all(), LIMITS).encode(pdu, profile));
        assertThrows(
                IllegalArgumentException.class,
                () -> configured.encode(pdu, ProtocolProfile.forVersion(SmppVersion.V3_4)));
        assertThrows(
                IllegalArgumentException.class,
                () -> configured.encode(
                        new Pdu<>(0, 1, new QuerySm("id", new Address(0, 0, ""), parameters)), profile));
        assertThrows(IllegalArgumentException.class, () -> CommonCommandCodecs.all(List.of(extension, extension)));
    }

    @Test
    void standardReservedAndUnimplementedContextsCannotBeReplacedByVendorDeclarations() {
        var valueCodec = new MessageTlvValueCodec(SmppVersion.V5_0, 0x0424);
        for (int tag : new int[] {0x0422, 0x1300, 0x4000, -1})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new CommonTlvExtension(SmppVersion.V5_0, 0x102, tag, valueCodec, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CommonTlvExtension(SmppVersion.V5_0, 4, 0x1500, valueCodec, false));
        assertThrows(NullPointerException.class, () -> new CommonTlvExtension(null, 0x102, 0x1500, valueCodec, false));
        assertThrows(
                NullPointerException.class, () -> new CommonTlvExtension(SmppVersion.V5_0, 0x102, 0x1500, null, false));
    }
}
