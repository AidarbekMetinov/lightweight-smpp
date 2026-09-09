package kg.aidarbek.smpp.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.PduHeader;
import org.junit.jupiter.api.Test;

final class PduCodecTest {
    private static final ProtocolProfile PROFILE = ProtocolProfile.forVersion(SmppVersion.V3_4);
    private static final PduLimits LIMITS = new PduLimits(256, 64, 4);

    @Test
    void encodesRegisteredCommandWithNetworkHeader() {
        PduCodec codec = new PduCodec(List.of(new OctetCodec()), LIMITS);
        assertArrayEquals(
                HexFormat.of().parseHex("000000110000000300000000123456782a"),
                codec.encode(new Pdu<>(0, 0x12345678L, new OctetCommand(42)), PROFILE));
    }

    @Test
    void decodesRegisteredCommandFromIndependentBytes() {
        PduCodec codec = new PduCodec(List.of(new OctetCodec()), LIMITS);
        assertEquals(
                new Pdu<>(0, 0x12345678L, new OctetCommand(42)),
                codec.decode(HexFormat.of().parseHex("000000110000000300000000123456782a"), PROFILE));
    }

    @Test
    void boundsAndMatchesCompleteFrameBeforeBodyDispatch() {
        PduCodec codec = new PduCodec(List.of(new OctetCodec()), new PduLimits(17, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode(HexFormat.of().parseHex("000000120000000300000000000000012a"), PROFILE));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode(HexFormat.of().parseHex("000000100000000300000000000000012a"), PROFILE));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode(HexFormat.of().parseHex("ffffffff0000000300000000000000012a"), PROFILE));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[15], PROFILE));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[18], PROFILE));
    }

    @Test
    void rejectsInvalidSequenceAndRequestStatusBeforeBodyDecoding() {
        PduCodec codec = new PduCodec(List.of(new OctetCodec()), LIMITS);
        for (String header : List.of(
                "00000011000000030000000100000001",
                "00000011000000030000000000000000",
                "00000011000000030000000080000000",
                "000000110000000300000000ffffffff")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> codec.decode(HexFormat.of().parseHex(header + "2a"), PROFILE));
        }
        assertThrows(IllegalArgumentException.class, () -> new Pdu<>(1, 1, new OctetCommand(42)));
        assertThrows(IllegalArgumentException.class, () -> new Pdu<>(0, 0, new OctetCommand(42)));
        assertThrows(IllegalArgumentException.class, () -> new Pdu<>(0, 0x80000000L, new OctetCommand(42)));
        assertThrows(NullPointerException.class, () -> new Pdu<>(0, 1, null));
    }

    @Test
    void rejectsInvalidLimitsAndAmbiguousRegistration() {
        assertThrows(IllegalArgumentException.class, () -> new PduLimits(15, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PduLimits(16, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new PduLimits(16, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new PduLimits(16, 0, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PduCodec(List.of(new OctetCodec(), new OctetCodec()), LIMITS));
        assertThrows(NullPointerException.class, () -> new PduCodec(List.of(new OctetCodec()), null));
        assertThrows(NullPointerException.class, () -> new PduCodec(null, LIMITS));
    }

    @Test
    void distinguishesUnknownCommandFromUnavailableCodecAndRetainsHeader() {
        PduCodec codec = new PduCodec(List.of(), LIMITS);
        CommandDispatchException unknown = assertThrows(
                CommandDispatchException.class,
                () -> codec.decode(HexFormat.of().parseHex("00000010f000aaaaffffffff00000007"), PROFILE));
        assertFalse(unknown.definedByProfile());
        assertEquals(new PduHeader(16, 0xf000aaaaL, 0xffffffffL, 7), unknown.header());
        CommandDispatchException unavailable = assertThrows(
                CommandDispatchException.class,
                () -> codec.decode(HexFormat.of().parseHex("00000010000000030000000000000007"), PROFILE));
        assertTrue(unavailable.definedByProfile());
        assertEquals(3, unavailable.header().commandId());
        assertThrows(
                CommandDispatchException.class, () -> codec.encode(new Pdu<>(0, 1, new OctetCommand(42)), PROFILE));
    }

    @Test
    void checksBodyCodecResultsBeforeAllocatingOrAcceptingAnEnvelope() {
        Pdu<OctetCommand> pdu = new Pdu<>(0, 1, new OctetCommand(42));
        PduCodec oversized =
                new PduCodec(List.of(new AdversarialCodec(3, Command.class, new OctetCommand(42), 241)), LIMITS);
        assertThrows(IllegalArgumentException.class, () -> oversized.encode(pdu, PROFILE));
        PduCodec missingBody =
                new PduCodec(List.of(new AdversarialCodec(3, Command.class, new OctetCommand(42), -1)), LIMITS);
        assertThrows(IllegalArgumentException.class, () -> missingBody.encode(pdu, PROFILE));
        byte[] frame = HexFormat.of().parseHex("000000110000000300000000000000012a");
        for (Command wrong : new Command[] {null, new OtherCommand(4), new OtherCommand(3)}) {
            PduCodec wrongResult = new PduCodec(List.of(new AdversarialCodec(3, OctetCommand.class, wrong, 1)), LIMITS);
            assertThrows(IllegalArgumentException.class, () -> wrongResult.decode(frame, PROFILE));
        }
        PduCodec wrongRepresentation = new PduCodec(List.of(new OctetCodec()), LIMITS);
        assertThrows(
                IllegalArgumentException.class,
                () -> wrongRepresentation.encode(new Pdu<>(0, 1, new OtherCommand(3)), PROFILE));
    }

    @Test
    void rejectsInvalidCommandCodecMetadataBeforeRegistration() {
        for (long id : new long[] {-1, 0x100000000L}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new PduCodec(
                            List.of(new AdversarialCodec(id, Command.class, new OctetCommand(42), 1)), LIMITS));
        }
        for (Class<?> type : new Class<?>[] {null, String.class, void.class}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new PduCodec(List.of(new AdversarialCodec(3, type, new OctetCommand(42), 1)), LIMITS));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new PduCodec(
                        List.of(new OctetCodec(), new AdversarialCodec(3, Command.class, new OctetCommand(42), 1)),
                        LIMITS));
    }

    @Test
    void registrationCopiesCallerCollectionAndCannotEnableAnotherProfilesCommand() {
        List<CommandCodec<?>> registrations = new ArrayList<>(List.of(new OctetCodec()));
        PduCodec codec = new PduCodec(registrations, LIMITS);
        registrations.clear();
        assertArrayEquals(
                HexFormat.of().parseHex("000000110000000300000000000000012a"),
                codec.encode(new Pdu<>(0, 1, new OctetCommand(42)), PROFILE));
        assertThrows(
                UnsupportedOperationException.class,
                () -> ControlCommandCodecs.all().clear());
        PduCodec versionFive = new PduCodec(
                List.of(new AdversarialCodec(0x111, OtherCommand.class, new OtherCommand(0x111), 0)), LIMITS);
        byte[] fixture = HexFormat.of().parseHex("00000010000001110000000000000001");
        CommandDispatchException exception =
                assertThrows(CommandDispatchException.class, () -> versionFive.decode(fixture, PROFILE));
        assertFalse(exception.definedByProfile());
        assertEquals(0x111, exception.header().commandId());
        assertEquals(
                new OtherCommand(0x111),
                versionFive
                        .decode(fixture, ProtocolProfile.forVersion(SmppVersion.V5_0))
                        .command());
    }

    private record OtherCommand(long commandId) implements Command {}

    /** Deliberately violates one extension postcondition at a time to verify dispatch defenses. */
    private static final class AdversarialCodec implements CommandCodec<Command> {
        private final long id;
        private final Class<?> type;
        private final Command decoded;
        private final int outputLength;

        private AdversarialCodec(long id, Class<?> type, Command decoded, int outputLength) {
            this.id = id;
            this.type = type;
            this.decoded = decoded;
            this.outputLength = outputLength;
        }

        @Override
        public long commandId() {
            return id;
        }

        @Override
        @SuppressWarnings(
                "unchecked") // Intentionally dishonest generic metadata exercises a faulty extension boundary.
        public Class<Command> commandType() {
            return (Class<Command>) type;
        }

        @Override
        public Command decode(byte[] body, long status, ProtocolProfile profile, PduLimits limits) {
            return decoded;
        }

        @Override
        public byte[] encode(Command command, long status, ProtocolProfile profile, PduLimits limits) {
            return outputLength < 0 ? null : new byte[outputLength];
        }
    }

    private record OctetCommand(int value) implements Command {
        @Override
        public long commandId() {
            return 3;
        }
    }

    private static final class OctetCodec implements CommandCodec<OctetCommand> {
        @Override
        public long commandId() {
            return 3;
        }

        @Override
        public Class<OctetCommand> commandType() {
            return OctetCommand.class;
        }

        @Override
        public OctetCommand decode(byte[] body, long status, ProtocolProfile profile, PduLimits limits) {
            CommandCodecChecks.context(commandId(), status, profile, limits);
            FieldReader reader = new FieldReader(body, limits.maximumBodyLength());
            int value = reader.readUnsignedByte();
            if (reader.remaining() != 0) {
                throw new FieldCodecException("Expected exactly one fixture octet");
            }
            return new OctetCommand(value);
        }

        @Override
        public byte[] encode(OctetCommand command, long status, ProtocolProfile profile, PduLimits limits) {
            CommandCodecChecks.context(commandId(), status, profile, limits);
            CommandCodecChecks.command(command, commandId());
            FieldWriter writer = new FieldWriter(Math.min(1, limits.maximumBodyLength()));
            writer.writeUnsignedByte(command.value());
            return writer.toByteArray();
        }
    }
}
