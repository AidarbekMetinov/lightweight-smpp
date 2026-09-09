package kg.aidarbek.smpp.codec;

import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.EMPTY;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.LIMITS;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.REQUIRED;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.SOURCE;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.V5;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.broadcast;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.codec;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.frame;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.parameters;
import static kg.aidarbek.smpp.codec.BroadcastOptionalParametersTest.append;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BroadcastSm;
import kg.aidarbek.smpp.protocol.BroadcastSmResponse;
import kg.aidarbek.smpp.protocol.CancelBroadcastSm;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.QueryBroadcastSm;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

class BroadcastBodyContractTest {
    private static final Map<Long, String> BODIES = Map.of(
            0x111L,
            "434253000101313233000000000000040006060004004e595c060100030000100604000200020605000309001f",
            0x80000111L,
            "696400",
            0x112L,
            "696400010131323300",
            0x80000112L,
            "6964000427000100060600010006080001ff",
            0x113L,
            "00696400010131323300",
            0x80000113L,
            "");

    @Test
    void allBodyImplementationsSatisfyOwnershipContextAndDirectCallBounds() {
        for (CommandCodec<?> codec : BroadcastCommandCodecs.all()) verify(codec);
    }

    private static <T extends Command> void verify(CommandCodec<T> codec) {
        byte[] body = HexFormat.of().parseHex(BODIES.get(codec.commandId())), original = body.clone();
        T command = codec.decode(body, 0, V5, LIMITS);
        assertEquals(codec.commandId(), command.commandId());
        assertTrue(codec.commandType().isInstance(command));
        Arrays.fill(body, (byte) 255);
        assertArrayEquals(original, codec.encode(command, 0, V5, LIMITS));
        byte[] encoded = codec.encode(command, 0, V5, LIMITS);
        Arrays.fill(encoded, (byte) 255);
        assertArrayEquals(original, codec.encode(command, 0, V5, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(command, -1, V5, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(original, 0x100000000L, V5, LIMITS));
        assertThrows(NullPointerException.class, () -> codec.encode(null, 0, V5, LIMITS));
        assertThrows(NullPointerException.class, () -> codec.decode(null, 0, V5, LIMITS));
        assertThrows(NullPointerException.class, () -> codec.decode(original, 0, null, LIMITS));
        assertThrows(NullPointerException.class, () -> codec.decode(original, 0, V5, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode(new byte[LIMITS.maximumBodyLength() + 1], 0, V5, LIMITS));
        ProtocolProfile v34 = ProtocolProfile.forVersion(SmppVersion.V3_4);
        assertThrows(IllegalArgumentException.class, () -> codec.decode(original, 0, v34, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(command, 0, v34, LIMITS));
        if ((codec.commandId() & 0x80000000L) == 0) {
            assertThrows(IllegalArgumentException.class, () -> codec.encode(command, 1, V5, LIMITS));
            assertThrows(IllegalArgumentException.class, () -> codec.decode(original, 1, V5, LIMITS));
        }
        for (int length = 0; length < original.length; length++) {
            byte[] truncated = Arrays.copyOf(original, length);
            assertThrows(IllegalArgumentException.class, () -> codec.decode(truncated, 0, V5, LIMITS));
        }
        byte[] unknown = HexFormat.of().parseHex("15000001ff");
        byte[] tail = Arrays.copyOf(original, original.length + unknown.length);
        System.arraycopy(unknown, 0, tail, original.length, unknown.length);
        T extended = codec.decode(tail, 0, V5, LIMITS);
        // TLV bounds precede unsupported vendor interpretation on every command.
        assertTrue(assertThrows(
                        IllegalArgumentException.class,
                        () -> codec.encode(extended, 0, V5, new PduLimits(70000, 4, 64)))
                .getMessage()
                .contains("byte bound"));
        assertTrue(assertThrows(
                        IllegalArgumentException.class,
                        () -> codec.encode(extended, 0, V5, new PduLimits(70000, 66000, 0)))
                .getMessage()
                .contains("count"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(tail, 0, V5, new PduLimits(70000, 4, 64)));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(tail, 0, V5, new PduLimits(70000, 66000, 0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode(Arrays.copyOf(tail, tail.length - 1), 0, V5, LIMITS));
    }

    @Test
    void payloadOctetLengthsAndOwnershipDoNotIntroduceShortMessageFields() {
        for (int length : new int[] {0, 1, 254, 255, 256, 65535}) {
            byte[] supplied = new byte[length];
            Arrays.fill(supplied, (byte) 255);
            OptionalParameters parameters = append(REQUIRED, new Tlv(0x0424, supplied));
            BroadcastSm command = broadcast(parameters);
            Arrays.fill(supplied, (byte) 0);
            byte[] encoded = codec().encode(new Pdu<>(0, 1, command), V5);
            BroadcastSm decoded = (BroadcastSm) codec().decode(encoded, V5).command();
            assertEquals(command, decoded);
            byte[] received = decoded.optionalParameters().entries().getLast().value();
            assertEquals(length, received.length);
            if (length > 0) assertEquals(255, received[0] & 255);
            Arrays.fill(encoded, (byte) 0);
            Arrays.fill(received, (byte) 0);
            assertEquals(command, decoded);
        }
    }

    @Test
    void malformedStringsAndWidthsFailWithoutDiagnosticPayloadExposure() {
        assertThrows(IllegalArgumentException.class, () -> new QueryBroadcastSm("x".repeat(65), SOURCE, EMPTY));
        assertThrows(IllegalArgumentException.class, () -> new CancelBroadcastSm("x".repeat(6), "id", SOURCE, EMPTY));
        assertThrows(IllegalArgumentException.class, () -> new QueryBroadcastSm("bad\0id", SOURCE, EMPTY));
        assertThrows(
                IllegalArgumentException.class, () -> new BroadcastSm("", SOURCE, "", 256, "", "", 0, 0, 0, REQUIRED));
        assertThrows(IllegalArgumentException.class, () -> codec().decode(frame(0x112, 0, "ff00000000"), V5));
        assertThrows(IllegalArgumentException.class, () -> codec().decode(frame(0x112, 0, "6964000000"), V5));
        assertFalse(broadcast(REQUIRED).toString().contains("123"));
        assertFalse(new QueryBroadcastSm("secret-id", SOURCE, EMPTY).toString().contains("secret-id"));
        assertFalse(new BroadcastSmResponse(new MessageResponse(Optional.of("secret-id"), EMPTY))
                .toString()
                .contains("secret-id"));
    }
}
