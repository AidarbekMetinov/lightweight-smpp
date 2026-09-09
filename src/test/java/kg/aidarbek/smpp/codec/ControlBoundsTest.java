package kg.aidarbek.smpp.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.BindResponse;
import kg.aidarbek.smpp.protocol.ControlCommand;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class ControlBoundsTest {
    private static final PduLimits LIMITS = new PduLimits(1024, 128, 4);

    @ParameterizedTest
    @EnumSource(SmppVersion.class)
    void acceptsExactBindFieldLimitsAndRejectsEachOverlongOrNonAsciiWireField(SmppVersion version) {
        ProtocolProfile profile = ProtocolProfile.forVersion(version);
        PduCodec codec = new PduCodec(ControlCommandCodecs.all(), LIMITS);
        String versionHex = version == SmppVersion.V3_4 ? "34" : "50";
        String[] fields = {"69".repeat(15), "70".repeat(8), "74".repeat(12), "72".repeat(40)};
        byte[] maximum = frame(1, 0, bindBody(fields, versionHex));
        assertEquals(98, maximum.length);
        Pdu<BindRequest> pdu = new Pdu<>(
                0,
                1,
                new BindRequest(
                        BindMode.RECEIVER,
                        "i".repeat(15),
                        "p".repeat(8),
                        "t".repeat(12),
                        version.interfaceVersion(),
                        0,
                        0,
                        "r".repeat(40)));
        assertEquals(pdu, codec.decode(maximum, profile));
        assertArrayEquals(maximum, codec.encode(pdu, profile));
        PduCodec exact = new PduCodec(ControlCommandCodecs.all(), new PduLimits(98, 0, 0));
        assertEquals(pdu, exact.decode(maximum, profile));
        assertArrayEquals(maximum, exact.encode(pdu, profile));
        PduCodec shortBound = new PduCodec(ControlCommandCodecs.all(), new PduLimits(97, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> shortBound.decode(maximum, profile));
        assertThrows(IllegalArgumentException.class, () -> shortBound.encode(pdu, profile));
        for (int index = 0; index < fields.length; index++) {
            String[] overlong = fields.clone();
            overlong[index] += "61";
            assertThrows(
                    IllegalArgumentException.class,
                    () -> codec.decode(frame(1, 0, bindBody(overlong, versionHex)), profile));
            String[] invalid = fields.clone();
            invalid[index] = "80";
            assertThrows(
                    IllegalArgumentException.class,
                    () -> codec.decode(frame(1, 0, bindBody(invalid, versionHex)), profile));
        }
        String minimum = "000000" + versionHex + "000000";
        assertEquals(
                23,
                codec.encode(
                                new Pdu<>(
                                        0,
                                        1,
                                        new BindRequest(
                                                BindMode.RECEIVER, "", "", "", version.interfaceVersion(), 0, 0, "")),
                                profile)
                        .length);
        for (int length = 0; length < 7; length++) {
            byte[] truncated = frame(1, 0, minimum.substring(0, length * 2));
            assertThrows(IllegalArgumentException.class, () -> codec.decode(truncated, profile));
        }
        for (String invalid : List.of("69".repeat(16) + "00", "80" + "00", "69".repeat(15))) {
            assertThrows(IllegalArgumentException.class, () -> codec.decode(frame(0x80000001L, 0, invalid), profile));
        }
        BindResponse response =
                new BindResponse(BindMode.RECEIVER, Optional.of("i".repeat(15)), new OptionalParameters(List.of()));
        assertEquals(
                response,
                codec.decode(frame(0x80000001L, 0, "69".repeat(15) + "00"), profile)
                        .command());
    }

    @ParameterizedTest
    @EnumSource(SmppVersion.class)
    void enforcesTlvByteAndCountBoundsForEveryControlBodyContext(SmppVersion version) {
        ProtocolProfile profile = ProtocolProfile.forVersion(version);
        PduCodec noCount = new PduCodec(ControlCommandCodecs.all(), new PduLimits(128, 64, 0));
        PduCodec noBytes = new PduCodec(ControlCommandCodecs.all(), new PduLimits(128, 0, 4));
        for (long command : new long[] {
            1, 2, 9, 0x80000001L, 0x80000002L, 0x80000009L, 6, 0x80000006L, 0x15, 0x80000015L, 0x80000000L
        }) {
            String prefix = command == 1 || command == 2 || command == 9
                    ? "00000034000000"
                    : command == 0x80000001L || command == 0x80000002L || command == 0x80000009L ? "00" : "";
            long status = command == 0x80000000L ? 3 : 0;
            byte[] withUnknown = frame(command, status, prefix + "2fff000101");
            assertThrows(IllegalArgumentException.class, () -> noCount.decode(withUnknown, profile));
            assertThrows(IllegalArgumentException.class, () -> noBytes.decode(withUnknown, profile));
        }
        BindResponse response = new BindResponse(
                BindMode.RECEIVER, Optional.of(""), new OptionalParameters(List.of(new Tlv(0x0210, new byte[] {0x34
                }))));
        assertThrows(IllegalArgumentException.class, () -> noCount.encode(new Pdu<>(0, 1, response), profile));
        assertThrows(IllegalArgumentException.class, () -> noBytes.encode(new Pdu<>(0, 1, response), profile));
        PduCodec exact = new PduCodec(ControlCommandCodecs.all(), new PduLimits(22, 5, 1));
        assertArrayEquals(frame(0x80000001L, 0, "000210000134"), exact.encode(new Pdu<>(0, 1, response), profile));
        assertEquals(
                response,
                exact.decode(frame(0x80000001L, 0, "000210000134"), profile).command());
        PduCodec insufficientBody = new PduCodec(ControlCommandCodecs.all(), new PduLimits(21, 5, 1));
        assertThrows(IllegalArgumentException.class, () -> insufficientBody.encode(new Pdu<>(0, 1, response), profile));
        PduCodec largeConfiguredBound = new PduCodec(
                ControlCommandCodecs.all(),
                new PduLimits(Integer.MAX_VALUE, Integer.MAX_VALUE - 16, Integer.MAX_VALUE));
        assertEquals(
                16,
                largeConfiguredBound.encode(
                                new Pdu<>(
                                        0,
                                        1,
                                        new ControlCommand(
                                                ControlCommand.Type.UNBIND, new OptionalParameters(List.of()))),
                                profile)
                        .length);
        if (version == SmppVersion.V5_0) {
            byte[] ignoredErrorBody = frame(0x80000001L, 5, "0210ffff1234");
            BindResponse ignored =
                    (BindResponse) noBytes.decode(ignoredErrorBody, profile).command();
            assertEquals(Optional.empty(), ignored.systemId());
            assertEquals(List.of(), ignored.optionalParameters().entries());
        }
    }

    @ParameterizedTest
    @EnumSource(SmppVersion.class)
    void outgoingTlvBoundsAreCheckedBeforeTypedInterpretationCopiesValues(SmppVersion version) {
        ProtocolProfile profile = ProtocolProfile.forVersion(version);
        BindResponse oversized = new BindResponse(
                BindMode.RECEIVER, Optional.of(""), new OptionalParameters(List.of(new Tlv(0x0210, new byte[256]))));
        PduCodec byteBound = new PduCodec(ControlCommandCodecs.all(), new PduLimits(1024, 32, 1));
        IllegalArgumentException bytes = assertThrows(
                IllegalArgumentException.class, () -> byteBound.encode(new Pdu<>(0, 1, oversized), profile));
        assertTrue(bytes.getMessage().contains("byte bound"));
        PduCodec countBound = new PduCodec(ControlCommandCodecs.all(), new PduLimits(1024, 512, 0));
        IllegalArgumentException count = assertThrows(
                IllegalArgumentException.class, () -> countBound.encode(new Pdu<>(0, 1, oversized), profile));
        assertTrue(count.getMessage().contains("count"));
    }

    private static String bindBody(String[] fields, String versionHex) {
        return fields[0] + "00" + fields[1] + "00" + fields[2] + "00" + versionHex + "0000" + fields[3] + "00";
    }

    private static byte[] frame(long command, long status, String bodyHex) {
        return HexFormat.of()
                .parseHex(HexFormat.of().toHexDigits(16 + bodyHex.length() / 2)
                        + HexFormat.of().toHexDigits((int) command)
                        + HexFormat.of().toHexDigits((int) status)
                        + "00000001" + bodyHex);
    }
}
