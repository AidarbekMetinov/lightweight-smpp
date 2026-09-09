package kg.aidarbek.smpp.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.ControlCommand;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

final class ControlCommandCodecTest {
    private static final PduLimits LIMITS = new PduLimits(128, 32, 4);
    private static final PduCodec CODEC = new PduCodec(ControlCommandCodecs.all(), LIMITS);

    @ParameterizedTest
    @CsvSource({
        "UNBIND,00000006,00000000,00000001",
        "UNBIND_RESPONSE,80000006,00000000,00000001",
        "ENQUIRE_LINK,00000015,00000000,7fffffff",
        "ENQUIRE_LINK_RESPONSE,80000015,00000000,7fffffff",
        "GENERIC_NACK,80000000,00000003,00000000",
        "GENERIC_NACK,80000000,ffffffff,00000008",
        "UNBIND_RESPONSE,80000006,ffffffff,00000009",
        "ENQUIRE_LINK_RESPONSE,80000015,ffffffff,00000009"
    })
    void headerOnlyControlCommandsMatchIndependentBytes(
            ControlCommand.Type type, String id, String status, String sequence) {
        byte[] fixture = HexFormat.of().parseHex("00000010" + id + status + sequence);
        Pdu<ControlCommand> pdu = new Pdu<>(
                Long.parseLong(status, 16),
                Long.parseLong(sequence, 16),
                new ControlCommand(type, new OptionalParameters(List.of())));
        for (SmppVersion version : SmppVersion.values()) {
            ProtocolProfile profile = ProtocolProfile.forVersion(version);
            assertArrayEquals(fixture, CODEC.encode(pdu, profile));
            assertEquals(pdu, CODEC.decode(fixture, profile));
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = ControlCommand.Type.class,
            names = {"UNBIND_RESPONSE", "ENQUIRE_LINK_RESPONSE", "GENERIC_NACK"})
    void versionFiveControlResponsesCarryCongestionWithSuccessOrErrorStatus(ControlCommand.Type type) {
        ProtocolProfile profile = ProtocolProfile.forVersion(SmppVersion.V5_0);
        for (long status : new long[] {type == ControlCommand.Type.GENERIC_NACK ? 3 : 0, 0xffffffffL}) {
            Pdu<ControlCommand> pdu = new Pdu<>(
                    status, 1, new ControlCommand(type, new OptionalParameters(List.of(new Tlv(0x0428, new byte[] {100
                    })))));
            byte[] fixture = controlFrame(type, status, "0428000164");
            assertArrayEquals(fixture, CODEC.encode(pdu, profile));
            assertEquals(pdu, CODEC.decode(fixture, profile));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CODEC.encode(pdu, ProtocolProfile.forVersion(SmppVersion.V3_4)));
            assertEquals(pdu, CODEC.decode(fixture, ProtocolProfile.forVersion(SmppVersion.V3_4)));
        }
        ControlCommand reserved = (ControlCommand)
                CODEC.decode(controlFrame(type, 3, "04280001ff"), profile).command();
        assertEquals(
                new Tlv(0x0428, new byte[] {(byte) 255}),
                reserved.optionalParameters().entries().getFirst());
        assertEquals(
                Optional.empty(),
                TypedTlvRegistry.standard()
                        .decode(
                                SmppVersion.V5_0,
                                reserved.commandId(),
                                reserved.optionalParameters().entries().getFirst(),
                                Integer.class));
        assertThrows(IllegalArgumentException.class, () -> CODEC.encode(new Pdu<>(3, 1, reserved), profile));
        ControlCommand unknown = (ControlCommand) CODEC.decode(controlFrame(type, 3, "2fff0001012fff000102"), profile)
                .command();
        assertEquals(2, unknown.optionalParameters().entries().size());
        assertThrows(IllegalArgumentException.class, () -> CODEC.encode(new Pdu<>(3, 1, unknown), profile));
        for (String malformed : List.of("042800", "0428000264", "042800026400", "04280001640428000100")) {
            assertThrows(IllegalArgumentException.class, () -> CODEC.decode(controlFrame(type, 3, malformed), profile));
        }
    }

    @ParameterizedTest
    @EnumSource(SmppVersion.class)
    void genericNackRequiresAnErrorAndRequestsHaveNoBody(SmppVersion version) {
        ProtocolProfile profile = ProtocolProfile.forVersion(version);
        ControlCommand nack = new ControlCommand(ControlCommand.Type.GENERIC_NACK, new OptionalParameters(List.of()));
        assertThrows(IllegalArgumentException.class, () -> CODEC.encode(new Pdu<>(0, 0, nack), profile));
        assertThrows(
                IllegalArgumentException.class,
                () -> CODEC.decode(HexFormat.of().parseHex("00000010800000000000000000000000"), profile));
        for (ControlCommand.Type type : List.of(ControlCommand.Type.UNBIND, ControlCommand.Type.ENQUIRE_LINK)) {
            assertThrows(IllegalArgumentException.class, () -> CODEC.decode(controlFrame(type, 0, "00"), profile));
            ControlCommand request =
                    new ControlCommand(type, new OptionalParameters(List.of(new Tlv(0x0428, new byte[] {1}))));
            assertThrows(IllegalArgumentException.class, () -> CODEC.encode(new Pdu<>(0, 1, request), profile));
        }
    }

    @ParameterizedTest
    @EnumSource(ControlCommand.Type.class)
    void incomingUnexpectedControlTlvsArePreservedForForwardCompatibility(ControlCommand.Type type) {
        long status = type == ControlCommand.Type.GENERIC_NACK ? 3 : 0;
        OptionalParameters raw =
                new OptionalParameters(List.of(new Tlv(0x2fff, new byte[] {1}), new Tlv(0x2fff, new byte[] {2})));
        for (SmppVersion version : SmppVersion.values()) {
            ProtocolProfile profile = ProtocolProfile.forVersion(version);
            assertEquals(
                    new Pdu<>(status, 1, new ControlCommand(type, raw)),
                    CODEC.decode(controlFrame(type, status, "2fff0001012fff000102"), profile));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CODEC.encode(new Pdu<>(status, 1, new ControlCommand(type, raw)), profile));
        }
    }

    private static byte[] controlFrame(ControlCommand.Type type, long status, String bodyHex) {
        return HexFormat.of()
                .parseHex(HexFormat.of().toHexDigits(16 + bodyHex.length() / 2)
                        + HexFormat.of().toHexDigits((int) type.commandId())
                        + HexFormat.of().toHexDigits((int) status)
                        + "00000001" + bodyHex);
    }
}
