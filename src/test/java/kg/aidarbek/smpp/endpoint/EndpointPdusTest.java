package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.HexFormat;
import kg.aidarbek.smpp.codec.PduLimits;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.PduHeader;
import kg.aidarbek.smpp.session.EndpointRole;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EndpointPdusTest {
    @ParameterizedTest
    @EnumSource(SmppVersion.class)
    void unavailableMessageHasPairedNegativeAndUnknownOrInvalidSequenceHasGenericNack(SmppVersion version) {
        EndpointPdus pdus = new EndpointPdus(EndpointRole.MESSAGE_CENTER, new PduLimits(1024, 128, 8));
        ProtocolProfile profile = ProtocolProfile.forVersion(version);
        assertArrayEquals(
                hex("00000010800000040000000800000017"), pdus.negative(new PduHeader(16, 4, 0, 23), 8, profile));
        assertArrayEquals(
                hex("00000010800000000000000300000017"),
                pdus.negative(new PduHeader(16, 0x1020304, 0, 23), 3, profile));
        assertArrayEquals(
                hex("00000010800000000000000200000000"),
                pdus.negative(new PduHeader(16, 4, 0, 0xffff_ffffL), 2, profile));
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }
}
