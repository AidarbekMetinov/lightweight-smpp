package kg.aidarbek.smpp.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.BroadcastSm;
import kg.aidarbek.smpp.protocol.BroadcastSmResponse;
import kg.aidarbek.smpp.protocol.CancelBroadcastSm;
import kg.aidarbek.smpp.protocol.CancelBroadcastSmResponse;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.QueryBroadcastSm;
import kg.aidarbek.smpp.protocol.QueryBroadcastSmResponse;
import org.junit.jupiter.api.Test;

class BroadcastCommandCodecsTest {
    static final ProtocolProfile V5 = ProtocolProfile.forVersion(SmppVersion.V5_0);
    static final OptionalParameters EMPTY = new OptionalParameters(List.of());
    static final PduLimits LIMITS = new PduLimits(70000, 66000, 64);
    static final Address SOURCE = new Address(1, 1, "123");
    static final OptionalParameters REQUIRED = parameters("06060004004e595c060100030000100604000200020605000309001f");

    static BroadcastSm broadcast(OptionalParameters parameters) {
        return new BroadcastSm("CBS", SOURCE, "", 0, "", "", 0, 4, 0, parameters);
    }

    static OptionalParameters parameters(String hex) {
        return TlvCodec.decode(HexFormat.of().parseHex(hex), 66000, 64);
    }

    static PduCodec codec() {
        return new PduCodec(BroadcastCommandCodecs.all(), LIMITS);
    }

    static byte[] frame(long id, long status, String hex) {
        byte[] body = HexFormat.of().parseHex(hex);
        return ByteBuffer.allocate(16 + body.length)
                .putInt(16 + body.length)
                .putInt((int) id)
                .putInt((int) status)
                .putInt(0x10203)
                .put(body)
                .array();
    }

    @Test
    void allSixCommandsMatchIndependentSmpP50FramesAndReject34() {
        Command[] commands = {
            broadcast(REQUIRED),
            new BroadcastSmResponse(new MessageResponse(Optional.of("Id-A"), EMPTY)),
            new QueryBroadcastSm("Id-A", SOURCE, EMPTY),
            new QueryBroadcastSmResponse(
                    new MessageResponse(Optional.of("Id-A"), parameters("042700010106060004004e595c0608000150"))),
            new CancelBroadcastSm("CBS", "Id-A", SOURCE, EMPTY),
            new CancelBroadcastSmResponse(parameters("0428000164"))
        };
        String[] bodies = {
            "434253000101313233000000000000040006060004004e595c060100030000100604000200020605000309001f",
            "49642d4100",
            "49642d4100010131323300",
            "49642d4100042700010106060004004e595c0608000150",
            "4342530049642d4100010131323300",
            "0428000164"
        };
        for (int i = 0; i < commands.length; i++) {
            Command command = commands[i];
            Pdu<Command> pdu = new Pdu<>(0, 0x10203, command);
            byte[] expected = frame(command.commandId(), 0, bodies[i]);
            assertArrayEquals(expected, codec().encode(pdu, V5));
            assertEquals(pdu, codec().decode(expected, V5));
            ProtocolProfile v34 = ProtocolProfile.forVersion(SmppVersion.V3_4);
            assertThrows(IllegalArgumentException.class, () -> codec().encode(pdu, v34));
            assertThrows(IllegalArgumentException.class, () -> codec().decode(expected, v34));
        }
    }
}
