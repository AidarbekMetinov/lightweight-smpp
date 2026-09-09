package kg.aidarbek.smpp.codec;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.profile.MessageDirection;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.DataSm;
import kg.aidarbek.smpp.protocol.DataSmResponse;
import kg.aidarbek.smpp.protocol.DeliverSm;
import kg.aidarbek.smpp.protocol.DeliverSmResponse;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.ShortMessage;
import kg.aidarbek.smpp.protocol.SubmitSm;
import kg.aidarbek.smpp.protocol.SubmitSmResponse;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

class MessageCommandCodecsTest {
    @Test
    void directBodyCallsEnforceTheSameNullStatusBoundsAndOwnershipContract() {
        for (SmppVersion version : SmppVersion.values()) {
            ProtocolProfile profile = ProtocolProfile.forVersion(version);
            for (CommandCodec<?> codec : MessageCommandCodecs.all(MessageDirection.SUBMISSION)) {
                byte[] body = new byte[codec.commandId() == 0x103 ? 10 : codec.commandId() < 0x80000000L ? 17 : 1];
                Command value = codec.decode(body, 0, profile, LIMITS);
                assertThrows(NullPointerException.class, () -> codec.decode(null, 0, profile, LIMITS));
                assertThrows(NullPointerException.class, () -> codec.decode(body, 0, null, LIMITS));
                assertThrows(NullPointerException.class, () -> codec.decode(body, 0, profile, null));
                assertThrows(NullPointerException.class, () -> codec.encode(null, 0, profile, LIMITS));
                assertThrows(IllegalArgumentException.class, () -> codec.decode(body, -1, profile, LIMITS));
                assertThrows(IllegalArgumentException.class, () -> codec.decode(body, 0x100000000L, profile, LIMITS));
                if (codec.commandId() < 0x80000000L)
                    assertThrows(IllegalArgumentException.class, () -> codec.decode(body, 1, profile, LIMITS));
                directEncodeContract(codec, value, profile, body);
                if (codec.commandId() >= 0x80000000L) {
                    Command omitted = codec.decode(new byte[0], 1, profile, LIMITS);
                    assertThrows(NullPointerException.class, () -> encodeBody(codec, omitted, 1, profile, null));
                }
            }
        }
    }

    private static <T extends Command> byte[] encodeBody(
            CommandCodec<T> codec, Command value, long status, ProtocolProfile profile, PduLimits limits) {
        return codec.encode(codec.commandType().cast(value), status, profile, limits);
    }

    private static void directEncodeContract(
            CommandCodec<?> codec, Command value, ProtocolProfile profile, byte[] body) {
        assertThrows(NullPointerException.class, () -> encodeBody(codec, value, 0, null, LIMITS));
        assertThrows(NullPointerException.class, () -> encodeBody(codec, value, 0, profile, null));
        assertThrows(IllegalArgumentException.class, () -> encodeBody(codec, value, -1, profile, LIMITS));
        assertThrows(
                IllegalArgumentException.class, () -> encodeBody(codec, value, 0, profile, new PduLimits(16, 0, 0)));
        byte[] output = encodeBody(codec, value, 0, profile, LIMITS);
        assertArrayEquals(body, output);
        output[0] = 1;
        assertArrayEquals(body, encodeBody(codec, value, 0, profile, LIMITS));
    }

    private static final OptionalParameters EMPTY = new OptionalParameters(List.of());
    private static final PduLimits LIMITS = new PduLimits(70000, 66000, 128);

    @Test
    void sixCommandsMatchIndependentCompleteWireFixturesInBothProfilesAndDataDirections() {
        Command[] commands = {
            new SubmitSm(message(new byte[] {0, (byte) 0xff, 65})),
            new DeliverSm(message(new byte[] {0, (byte) 0xff, 65})),
            new DataSm(
                    "CMT",
                    new Address(1, 1, "123"),
                    new Address(1, 1, "456"),
                    0,
                    1,
                    4,
                    new OptionalParameters(List.of(new Tlv(0x0424, new byte[] {0, (byte) 0xff, 65})))),
            new SubmitSmResponse(new MessageResponse(Optional.of("A1"), EMPTY)),
            new DeliverSmResponse(new MessageResponse(Optional.of(""), EMPTY)),
            new DataSmResponse(new MessageResponse(Optional.of("A1"), EMPTY))
        };
        String[] fixtures = {
            "0000002d000000040000000000000007434d54000101313233000101343536000000010000010004000300ff41",
            "0000002d000000050000000000000007434d54000101313233000101343536000000010000010004000300ff41",
            "0000002a000001030000000000000007434d54000101313233000101343536000001040424000300ff41",
            "00000013800000040000000000000007413100",
            "0000001180000005000000000000000700",
            "00000013800001030000000000000007413100"
        };
        for (SmppVersion version : SmppVersion.values()) {
            for (MessageDirection direction : MessageDirection.values()) {
                PduCodec codec = new PduCodec(MessageCommandCodecs.all(direction), LIMITS);
                ProtocolProfile profile = ProtocolProfile.forVersion(version);
                for (int i = 0; i < commands.length; i++) {
                    byte[] bytes = HexFormat.of().parseHex(fixtures[i]);
                    Pdu<Command> pdu = new Pdu<>(0, 7, commands[i]);
                    assertArrayEquals(bytes, codec.encode(pdu, profile), version + " " + commands[i].getClass());
                    assertEquals(pdu, codec.decode(bytes, profile));
                }
            }
        }
    }

    static ShortMessage message(byte[] payload) {
        return new ShortMessage(
                "CMT",
                new Address(1, 1, "123"),
                new Address(1, 1, "456"),
                0,
                0,
                1,
                "",
                "",
                1,
                0,
                4,
                0,
                new OctetString(payload),
                EMPTY);
    }
}
