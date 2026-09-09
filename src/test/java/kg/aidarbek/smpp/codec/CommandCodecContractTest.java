package kg.aidarbek.smpp.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.BindResponse;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.ControlCommand;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class CommandCodecContractTest {
    private static final PduLimits LIMITS = new PduLimits(128, 64, 8);

    @ParameterizedTest
    @EnumSource(SmppVersion.class)
    void registeredBodyCodecsValidateTheirContextAndOwnAllByteArrays(SmppVersion version) {
        ProtocolProfile profile = ProtocolProfile.forVersion(version);
        for (CommandCodec<?> codec : ControlCommandCodecs.all()) {
            Command command = commands(version).stream()
                    .filter(value -> value.commandId() == codec.commandId())
                    .findFirst()
                    .orElseThrow();
            verify(codec, command, profile);
        }
    }

    private static <T extends Command> void verify(CommandCodec<T> codec, Command input, ProtocolProfile profile) {
        T command = codec.commandType().cast(input);
        long status = command.commandId() == 0x80000000L ? 3 : 0;
        byte[] encoded = codec.encode(command, status, profile, LIMITS);
        byte[] original = encoded.clone();
        T decoded = codec.decode(encoded, status, profile, LIMITS);
        assertEquals(command, decoded);
        assertArrayEquals(original, encoded);
        Arrays.fill(encoded, (byte) 0xff);
        assertEquals(command, decoded);
        assertArrayEquals(original, codec.encode(command, status, profile, LIMITS));
        assertNotSame(encoded, codec.encode(command, status, profile, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(command, -1, profile, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(original, 0x100000000L, profile, LIMITS));
        assertThrows(NullPointerException.class, () -> codec.encode(command, status, null, LIMITS));
        assertThrows(NullPointerException.class, () -> codec.encode(command, status, profile, null));
        assertThrows(NullPointerException.class, () -> codec.decode(null, status, profile, LIMITS));
        assertThrows(NullPointerException.class, () -> codec.encode(null, status, profile, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[113], status, profile, LIMITS));
        for (Command other : commands(profile.version())) {
            if (codec.commandType().isInstance(other) && other.commandId() != codec.commandId()) {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> codec.encode(codec.commandType().cast(other), status, profile, LIMITS));
            }
        }
        if ((command.commandId() & 0x80000000L) == 0) {
            assertThrows(IllegalArgumentException.class, () -> codec.encode(command, 1, profile, LIMITS));
            assertThrows(IllegalArgumentException.class, () -> codec.decode(original, 1, profile, LIMITS));
        }
    }

    private static List<Command> commands(SmppVersion version) {
        List<Command> commands = new ArrayList<>();
        for (BindMode mode : BindMode.values()) {
            commands.add(new BindRequest(mode, "client", "pwd", "type", version.interfaceVersion(), 1, 1, "123"));
            commands.add(new BindResponse(
                    mode, Optional.of("server"), new OptionalParameters(List.of(new Tlv(0x0210, new byte[] {
                        (byte) version.interfaceVersion()
                    })))));
        }
        for (ControlCommand.Type type : ControlCommand.Type.values()) {
            OptionalParameters parameters = version == SmppVersion.V5_0 && (type.commandId() & 0x80000000L) != 0
                    ? new OptionalParameters(List.of(new Tlv(0x0428, new byte[] {20})))
                    : new OptionalParameters(List.of());
            commands.add(new ControlCommand(type, parameters));
        }
        return List.copyOf(commands);
    }
}
