package kg.aidarbek.smpp.codec;

import static kg.aidarbek.smpp.codec.CommonCommandCodecsTest.EMPTY;
import static kg.aidarbek.smpp.codec.CommonCommandCodecsTest.LIMITS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.QuerySm;
import kg.aidarbek.smpp.protocol.QuerySmResponse;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

class CommonValidationTest {
    private final PduCodec codec = new PduCodec(CommonCommandCodecs.all(), LIMITS);
    private static final ProtocolProfile V34 = ProtocolProfile.forVersion(SmppVersion.V3_4);
    private static final ProtocolProfile V50 = ProtocolProfile.forVersion(SmppVersion.V5_0);

    @Test
    void queryResponseErrorOmissionIsProfileSpecific() {
        byte[] failedWithGarbage = HexFormat.of().parseHex("00000013800000030000000b00000007ffff80");
        QuerySmResponse omitted = new QuerySmResponse(Optional.empty(), EMPTY);
        assertEquals(new Pdu<>(11, 7, omitted), codec.decode(failedWithGarbage, V50));
        assertEquals(16, codec.encode(new Pdu<>(11, 7, omitted), V50).length);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(failedWithGarbage, V34));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(new Pdu<>(0, 7, omitted), V50));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(new Pdu<>(11, 7, omitted), V34));
        QuerySmResponse present = response("", 7);
        assertEquals(new Pdu<>(11, 7, present), codec.decode(codec.encode(new Pdu<>(11, 7, present), V34), V34));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(new Pdu<>(11, 7, present), V50));
    }

    @Test
    void queryStateDateAndAddressSemanticsAreExplicitWithoutRejectingReservedIncomingValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.encode(new Pdu<>(0, 7, response("000001000000000R", 2)), V50));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.encode(new Pdu<>(0, 7, response("260909120000000+", 1)), V50));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(new Pdu<>(0, 7, response("", 0)), V34));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(new Pdu<>(0, 7, response("", 10)), V50));
        byte[] reserved = HexFormat.of().parseHex("000000178000000300000000000000074162430000ff00");
        assertEquals(response("", 255), codec.decode(reserved, V50).command());
        QuerySm unknownAddress = new QuerySm("id", new Address(255, 255, ""), EMPTY);
        assertThrows(IllegalArgumentException.class, () -> codec.encode(new Pdu<>(0, 7, unknownAddress), V34));
        byte[] incoming = HexFormat.of().parseHex("00000016000000030000000000000007696400ffff00");
        assertEquals(unknownAddress, codec.decode(incoming, V34).command());
    }

    @Test
    void queryResponseCongestionIsFiveOnlyAndIncomingExtensionsRetainRawBytes() {
        OptionalParameters congestion = new OptionalParameters(List.of(new Tlv(0x0428, new byte[] {50})));
        QuerySmResponse response =
                new QuerySmResponse(Optional.of(new QuerySmResponse.Result("id", "", 1, 0)), congestion);
        assertEquals(new Pdu<>(0, 7, response), codec.decode(codec.encode(new Pdu<>(0, 7, response), V50), V50));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(new Pdu<>(0, 7, response), V34));
        byte[] unknown = HexFormat.of().parseHex("0000001b000000030000000000000007696400000000140100010a");
        QuerySm value = new QuerySm(
                "id", new Address(0, 0, ""), new OptionalParameters(List.of(new Tlv(0x1401, new byte[] {10}))));
        assertEquals(value, codec.decode(unknown, V34).command());
        assertThrows(IllegalArgumentException.class, () -> codec.encode(new Pdu<>(0, 7, value), V34));
    }

    private static QuerySmResponse response(String date, int state) {
        return new QuerySmResponse(Optional.of(new QuerySmResponse.Result("AbC", date, state, 0)), EMPTY);
    }
}
