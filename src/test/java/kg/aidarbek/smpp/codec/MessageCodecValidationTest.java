package kg.aidarbek.smpp.codec;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
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
import org.junit.jupiter.api.Test;

class MessageCodecValidationTest {
    private static final OptionalParameters EMPTY = new OptionalParameters(List.of());
    private static final PduLimits LIMITS = new PduLimits(70000, 66000, 128);

    @Test
    void messageIdBoundsAndEmptyDataRequestsRemainExplicit() {
        for (SmppVersion version : SmppVersion.values()) {
            for (String id : List.of("", "x".repeat(64))) {
                for (Command command : List.of(
                        new SubmitSmResponse(new MessageResponse(Optional.of(id), EMPTY)),
                        new DataSmResponse(new MessageResponse(Optional.of(id), EMPTY))))
                    assertEquals(
                            command, decode(encode(command, version), version).command());
            }
            DataSm emptyData = new DataSm("", new Address(0, 0, ""), new Address(0, 0, ""), 0, 0, 0, EMPTY);
            assertArrayEquals(
                    HexFormat.of().parseHex("0000001a00000103000000000000000700000000000000000000"),
                    encode(emptyData, version));
            byte[] badResponse = HexFormat.of().parseHex("000000128000000400000000000000076161");
            assertThrows(FieldCodecException.class, () -> decode(badResponse, version));
            byte[] valid = encode(new SubmitSm(MessageCommandCodecsTest.message(new byte[] {1})), version);
            for (int size : new int[] {17, 20, 25, 31, 36, 40, 42}) {
                byte[] cut = Arrays.copyOf(valid, size);
                java.nio.ByteBuffer.wrap(cut).putInt(size);
                assertThrows(IllegalArgumentException.class, () -> decode(cut, version));
            }
            byte[] unterminated = encode(new SubmitSm(fields(20, 0, 0, 0, 0, "", "", 0, 0, 0, EMPTY)), version);
            unterminated[39] = 'a';
            assertThrows(FieldCodecException.class, () -> decode(unterminated, version));
        }
    }

    @Test
    void shortMessageBoundaryIs254In34And255In50AndAddressesAreCommandSpecific() {
        for (SmppVersion version : SmppVersion.values()) {
            for (int length : new int[] {0, 254, 255}) {
                for (boolean deliver : new boolean[] {false, true}) {
                    Command command = deliver
                            ? new DeliverSm(fields(20, 20, length, 0, 0, "", "", 0, 0, 0, EMPTY))
                            : new SubmitSm(fields(20, 20, length, 0, 0, "", "", 0, 0, 0, EMPTY));
                    if (version == SmppVersion.V3_4 && length == 255) {
                        assertThrows(IllegalArgumentException.class, () -> encode(command, version));
                        byte[] peer = encode(command, SmppVersion.V5_0);
                        assertThrows(IllegalArgumentException.class, () -> decode(peer, version));
                    } else {
                        byte[] bytes = encode(command, version);
                        assertEquals(73 + length, bytes.length);
                        assertEquals(command, decode(bytes, version).command());
                    }
                }
            }
            for (int[] sizes : new int[][] {{21, 0}, {0, 21}}) {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> encode(
                                new SubmitSm(fields(sizes[0], sizes[1], 0, 0, 0, "", "", 0, 0, 0, EMPTY)), version));
                assertThrows(
                        IllegalArgumentException.class,
                        () -> encode(
                                new DeliverSm(fields(sizes[0], sizes[1], 0, 0, 0, "", "", 0, 0, 0, EMPTY)), version));
            }
            DataSm data = new DataSm(
                    "", new Address(0, 0, "a".repeat(64)), new Address(0, 0, "b".repeat(64)), 0, 0, 255, EMPTY);
            assertEquals(data, decode(encode(data, version), version).command());
        }
    }

    @Test
    void requestFlagsAndDeliveryOnlyFieldsFollowTheChosenProfile() {
        List<ShortMessage> invalid = List.of(
                fields(0, 0, 0, 0x0c, 0, "", "", 0, 0, 0, EMPTY),
                fields(0, 0, 0, 0, 4, "", "", 0, 0, 0, EMPTY),
                fields(0, 0, 0, 0, 0, "", "", 32, 0, 0, EMPTY),
                fields(0, 0, 0, 0, 0, "", "", 0, 2, 0, EMPTY));
        for (SmppVersion version : SmppVersion.values()) {
            for (ShortMessage value : invalid) {
                assertThrows(IllegalArgumentException.class, () -> encode(new SubmitSm(value), version));
                assertThrows(IllegalArgumentException.class, () -> encode(new DeliverSm(value), version));
            }
            for (int[] field : new int[][] {{32, 12}, {34, 4}, {37, 32}, {38, 2}, {20, 7}, {21, 2}, {26, 7}, {27, 2}}) {
                byte[] peer = encode(new SubmitSm(MessageCommandCodecsTest.message(new byte[] {1, 2, 3})), version);
                peer[field[0]] = (byte) field[1];
                Pdu<Command> decoded = assertDoesNotThrow(() -> decode(peer, version));
                assertThrows(
                        IllegalArgumentException.class,
                        () -> codec().encode(decoded, ProtocolProfile.forVersion(version)));
            }
        }
        ShortMessage newer = fields(0, 0, 0, 4, 0, "260909120000000+", "000007000000000R", 3, 1, 255, EMPTY);
        assertDoesNotThrow(() -> encode(new DeliverSm(newer), SmppVersion.V5_0));
        assertThrows(IllegalArgumentException.class, () -> encode(new DeliverSm(newer), SmppVersion.V3_4));
        assertThrows(IllegalArgumentException.class, () -> encode(new SubmitSm(newer), SmppVersion.V3_4));
    }

    @Test
    void timesValidateComponentsAndPreserveIgnoredIncomingRelativeSuffix() {
        for (String time : List.of("260909120000000+", "991231235959948-", "000000000000000R", "001231235959000R")) {
            for (SmppVersion version : SmppVersion.values()) {
                Command command = new SubmitSm(fields(0, 0, 0, 0, 0, time, time, 0, 0, 0, EMPTY));
                assertEquals(command, decode(encode(command, version), version).command());
            }
        }
        for (String time : List.of(
                "1",
                "260009120000000+",
                "261309120000000+",
                "260900120000000+",
                "260932120000000+",
                "260909240000000+",
                "260909126000000+",
                "260909120060000+",
                "260909120000049+",
                "260909120000000X",
                "000000000000100R",
                "001300000000000R")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> encode(new SubmitSm(fields(0, 0, 0, 0, 0, time, "", 0, 0, 0, EMPTY)), SmppVersion.V5_0),
                    time);
        }
        byte[] peer =
                encode(new SubmitSm(fields(0, 0, 0, 0, 0, "000007000000000R", "", 0, 0, 0, EMPTY)), SmppVersion.V5_0);
        peer[16 + 10 + 12] = '7';
        peer[16 + 10 + 13] = '9';
        peer[16 + 10 + 14] = '9';
        SubmitSm decoded = (SubmitSm) decode(peer, SmppVersion.V5_0).command();
        assertEquals("000007000000799R", decoded.fields().scheduleDeliveryTime());
        assertThrows(IllegalArgumentException.class, () -> encode(decoded, SmppVersion.V5_0));
    }

    @Test
    void errorResponsesDistinguishOmittedAndPresentBodiesByProfile() {
        for (long id : new long[] {0x80000004L, 0x80000005L, 0x80000103L}) {
            byte[] error = HexFormat.of().parseHex(String.format("00000010%08x1234567800000007", id));
            for (SmppVersion version : SmppVersion.values()) {
                Pdu<Command> decoded = decode(error, version);
                assertEquals(0x12345678L, decoded.commandStatus());
                assertTrue(MessageCommandCodec.responseFields(decoded.command())
                        .messageId()
                        .isEmpty());
                assertArrayEquals(error, codec().encode(decoded, ProtocolProfile.forVersion(version)));
                error[8] = 0;
                error[9] = 0;
                error[10] = 0;
                error[11] = 0;
                assertThrows(IllegalArgumentException.class, () -> decode(error, version));
                error[8] = 0x12;
                error[9] = 0x34;
                error[10] = 0x56;
                error[11] = 0x78;
            }
            byte[] malformedError = Arrays.copyOf(error, 19);
            malformedError[3] = 19;
            malformedError[16] = (byte) 0xff;
            malformedError[17] = 1;
            malformedError[18] = 2;
            assertTrue(MessageCommandCodec.responseFields(
                            decode(malformedError, SmppVersion.V5_0).command())
                    .messageId()
                    .isEmpty());
            assertThrows(IllegalArgumentException.class, () -> decode(malformedError, SmppVersion.V3_4));
        }
        MessageResponse present = new MessageResponse(Optional.of(""), EMPTY);
        for (Command command :
                List.of(new SubmitSmResponse(present), new DeliverSmResponse(present), new DataSmResponse(present))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> codec().encode(new Pdu<>(1, 1, command), ProtocolProfile.forVersion(SmppVersion.V5_0)));
        }
    }

    @Test
    void malformedStringsTruncatedPayloadAndBodyBoundsFail() {
        byte[] valid = encode(
                new SubmitSm(MessageCommandCodecsTest.message(new byte[] {0, (byte) 255, 65})), SmppVersion.V5_0);
        for (int offset : new int[] {16, 22, 28}) {
            byte[] peer = valid.clone();
            peer[offset] = (byte) 255;
            assertThrows(IllegalArgumentException.class, () -> decode(peer, SmppVersion.V5_0));
        }
        byte[] peer = valid.clone();
        peer[41] = 4;
        assertThrows(IllegalArgumentException.class, () -> decode(peer, SmppVersion.V5_0));
        CommandCodec<?> bodyCodec =
                MessageCommandCodecs.all(MessageDirection.SUBMISSION).getFirst();
        byte[] body = Arrays.copyOfRange(valid, 16, valid.length);
        assertThrows(
                IllegalArgumentException.class,
                () -> bodyCodec.decode(
                        body, 0, ProtocolProfile.forVersion(SmppVersion.V5_0), new PduLimits(44, 20, 2)));
        byte[] original = valid.clone();
        SubmitSm decoded = (SubmitSm) decode(valid, SmppVersion.V5_0).command();
        Arrays.fill(valid, (byte) 0);
        assertArrayEquals(original, encode(decoded, SmppVersion.V5_0));
    }

    static ShortMessage fields(
            int sourceLength,
            int destinationLength,
            int payloadLength,
            int esm,
            int priority,
            String schedule,
            String validity,
            int registered,
            int replace,
            int defaultId,
            OptionalParameters parameters) {
        return new ShortMessage(
                "",
                new Address(0, 0, "a".repeat(sourceLength)),
                new Address(0, 0, "b".repeat(destinationLength)),
                esm,
                0,
                priority,
                schedule,
                validity,
                registered,
                replace,
                255,
                defaultId,
                new OctetString(new byte[payloadLength]),
                parameters);
    }

    static PduCodec codec() {
        return new PduCodec(MessageCommandCodecs.all(MessageDirection.SUBMISSION), LIMITS);
    }

    static byte[] encode(Command command, SmppVersion version) {
        return codec().encode(new Pdu<>(0, 7, command), ProtocolProfile.forVersion(version));
    }

    static Pdu<Command> decode(byte[] bytes, SmppVersion version) {
        return codec().decode(bytes, ProtocolProfile.forVersion(version));
    }
}
