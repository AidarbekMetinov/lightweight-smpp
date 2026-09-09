package kg.aidarbek.smpp.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.AlertNotification;
import kg.aidarbek.smpp.protocol.CancelSm;
import kg.aidarbek.smpp.protocol.CancelSmResponse;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.MultiDestination;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Outbind;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.QuerySm;
import kg.aidarbek.smpp.protocol.QuerySmResponse;
import kg.aidarbek.smpp.protocol.ReplaceSm;
import kg.aidarbek.smpp.protocol.ReplaceSmResponse;
import kg.aidarbek.smpp.protocol.SubmitMulti;
import kg.aidarbek.smpp.protocol.SubmitMultiResponse;
import kg.aidarbek.smpp.protocol.UnsuccessfulDestination;
import org.junit.jupiter.api.Test;

class CommonCommandCodecsTest {
    static final PduLimits LIMITS = new PduLimits(65536, 60000, 100);
    static final OptionalParameters EMPTY = new OptionalParameters(List.of());

    @Test
    void queryRequestAndResponseMatchIndependentFullBytesInBothProfiles() {
        QuerySm query = new QuerySm("AbC", new Address(1, 1, "123"), EMPTY);
        QuerySmResponse response =
                new QuerySmResponse(Optional.of(new QuerySmResponse.Result("AbC", "260909120000000+", 2, 5)), EMPTY);
        fixture(query, "0000001a00000003000000000000000741624300010131323300");
        fixture(response, "00000027800000030000000000000007416243003236303930393132303030303030302b000205");
    }

    @Test
    void cancellationAndOneWayCommandsMatchIndependentFullBytesInBothProfiles() {
        fixture(
                new CancelSm("CMT", "AbC", new Address(1, 1, "123"), new Address(1, 1, "456"), EMPTY),
                "00000024000000080000000000000007434d540041624300010131323300010134353600");
        fixture(new CancelSmResponse(EMPTY), "00000010800000080000000000000007");
        fixture(new Outbind("mc", "pw", EMPTY), "000000160000000b00000000000000076d6300707700");
        fixture(
                new AlertNotification(new Address(1, 1, "123"), new Address(0, 0, "esme"), EMPTY),
                "0000001d000001020000000000000007010131323300000065736d6500");
    }

    @Test
    void replacementAndMultipleDestinationsMatchIndependentFullBytesInBothProfiles() {
        fixture(
                new ReplaceSm(
                        "AbC",
                        new Address(1, 1, "123"),
                        "",
                        "",
                        1,
                        0,
                        new OctetString(new byte[] {0, (byte) 128}),
                        EMPTY),
                "000000210000000700000000000000074162430001013132330000000100020080");
        fixture(new ReplaceSmResponse(EMPTY), "00000010800000070000000000000007");
        fixture(
                new SubmitMulti(
                        "",
                        new Address(1, 1, "123"),
                        List.of(
                                new MultiDestination.Sme(new Address(1, 1, "456")),
                                new MultiDestination.DistributionList("DL")),
                        0,
                        0,
                        1,
                        "",
                        "",
                        1,
                        0,
                        4,
                        0,
                        new OctetString(new byte[] {0, (byte) 128}),
                        EMPTY),
                "0000002f00000021000000000000000700010131323300020101013435360002444c00000001000001000400020080");
        fixture(
                new SubmitMultiResponse(
                        Optional.of(new SubmitMultiResponse.Result(
                                "AbC", List.of(new UnsuccessfulDestination(new Address(1, 1, "456"), 11)))),
                        EMPTY),
                "0000001f80000021000000000000000741624300010101343536000000000b");
    }

    @Test
    void failedResponsesMatchIndependentProfileSpecificFullFrames() {
        PduCodec codec = new PduCodec(CommonCommandCodecs.all(), LIMITS);
        for (SmppVersion version : SmppVersion.values()) {
            ProtocolProfile profile = ProtocolProfile.forVersion(version);
            QuerySmResponse query = new QuerySmResponse(
                    version == SmppVersion.V3_4
                            ? Optional.of(new QuerySmResponse.Result("A", "", 7, 0))
                            : Optional.empty(),
                    EMPTY);
            SubmitMultiResponse multi = new SubmitMultiResponse(
                    version == SmppVersion.V3_4
                            ? Optional.of(new SubmitMultiResponse.Result("A", List.of()))
                            : Optional.empty(),
                    EMPTY);
            List<Command> commands = List.of(query, multi, new CancelSmResponse(EMPTY), new ReplaceSmResponse(EMPTY));
            List<String> frames = List.of(
                    version == SmppVersion.V3_4
                            ? "00000015800000030000000b000000074100000700"
                            : "00000010800000030000000b00000007",
                    version == SmppVersion.V3_4
                            ? "00000013800000210000000b00000007410000"
                            : "00000010800000210000000b00000007",
                    "00000010800000080000000b00000007",
                    "00000010800000070000000b00000007");
            for (int i = 0; i < commands.size(); i++) {
                Pdu<Command> expected = new Pdu<>(11, 7, commands.get(i));
                byte[] frame = HexFormat.of().parseHex(frames.get(i));
                assertArrayEquals(frame, codec.encode(expected, profile));
                assertEquals(expected, codec.decode(frame, profile));
            }
        }
    }

    static void fixture(Command command, String hex) {
        byte[] expected = HexFormat.of().parseHex(hex);
        PduCodec codec = new PduCodec(CommonCommandCodecs.all(), LIMITS);
        for (SmppVersion version : SmppVersion.values()) {
            ProtocolProfile profile = ProtocolProfile.forVersion(version);
            Pdu<Command> pdu = new Pdu<>(0, 7, command);
            assertArrayEquals(expected, codec.encode(pdu, profile));
            assertEquals(pdu, codec.decode(expected, profile));
        }
    }
}
