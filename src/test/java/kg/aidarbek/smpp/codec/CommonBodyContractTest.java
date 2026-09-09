package kg.aidarbek.smpp.codec;

import static kg.aidarbek.smpp.codec.CommonCommandCodecsTest.EMPTY;
import static kg.aidarbek.smpp.codec.CommonCommandCodecsTest.LIMITS;
import static kg.aidarbek.smpp.codec.CommonMessageValidationTest.raw;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.AlertNotification;
import kg.aidarbek.smpp.protocol.CancelSm;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.QuerySm;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

class CommonBodyContractTest {
    private static final Map<Long, String> BODIES = Map.of(
            3L, "4100010131323300",
            0x80000003L, "4100000700",
            7L, "4100010131323300000000000280ff",
            0x80000007L, "",
            8L, "004100010131323300010134353600",
            0x80000008L, "",
            0x21L, "00010131323300020101013435360002444c000000000000000000000280ff",
            0x80000021L, "410001010134353600fedcba98",
            0x0bL, "6d6300707700",
            0x102L, "010131323300000065736d6500");

    @Test
    void everyBodyCodecOwnsArraysAndValidatesDirectStatusNullAndBoundsContracts() {
        for (SmppVersion version : SmppVersion.values())
            for (CommandCodec<?> codec : CommonCommandCodecs.all()) verify(codec, ProtocolProfile.forVersion(version));
    }

    private static <T extends Command> void verify(CommandCodec<T> codec, ProtocolProfile profile) {
        byte[] body = HexFormat.of().parseHex(BODIES.get(codec.commandId()));
        byte[] original = body.clone();
        T command = codec.decode(body, 0, profile, LIMITS);
        assertEquals(codec.commandId(), command.commandId());
        assertTrue(codec.commandType().isInstance(command));
        Arrays.fill(body, (byte) 255);
        assertArrayEquals(original, codec.encode(command, 0, profile, LIMITS));
        byte[] encoded = codec.encode(command, 0, profile, LIMITS);
        assertNotSame(encoded, codec.encode(command, 0, profile, LIMITS));
        Arrays.fill(encoded, (byte) 0);
        assertArrayEquals(original, codec.encode(command, 0, profile, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(command, -1, profile, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(original, 0x100000000L, profile, LIMITS));
        assertThrows(NullPointerException.class, () -> codec.encode(null, 0, profile, LIMITS));
        assertThrows(NullPointerException.class, () -> codec.decode(null, 0, profile, LIMITS));
        assertThrows(NullPointerException.class, () -> codec.decode(original, 0, null, LIMITS));
        assertThrows(NullPointerException.class, () -> codec.decode(original, 0, profile, null));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(new byte[65521], 0, profile, LIMITS));
        if ((codec.commandId() & 0x80000000L) == 0) {
            assertThrows(IllegalArgumentException.class, () -> codec.encode(command, 1, profile, LIMITS));
            assertThrows(IllegalArgumentException.class, () -> codec.decode(original, 1, profile, LIMITS));
        }
        for (int length = 0; length < original.length; length++) {
            byte[] truncated = Arrays.copyOf(original, length);
            assertThrows(IllegalArgumentException.class, () -> codec.decode(truncated, 0, profile, LIMITS));
        }
        if (original.length > 0) {
            PduLimits small = new PduLimits(15 + original.length, 0, 0);
            assertThrows(IllegalArgumentException.class, () -> codec.encode(command, 0, profile, small));
        }
        byte[] unknown =
                TlvCodec.encode(new OptionalParameters(List.of(new Tlv(0x1500, new byte[] {(byte) 255}))), 5, 1);
        byte[] tail = Arrays.copyOf(original, original.length + unknown.length);
        System.arraycopy(unknown, 0, tail, original.length, unknown.length);
        T extended = codec.decode(tail, 0, profile, LIMITS);
        IllegalArgumentException byteBound = assertThrows(
                IllegalArgumentException.class, () -> codec.encode(extended, 0, profile, new PduLimits(65536, 4, 1)));
        assertTrue(byteBound.getMessage().contains("byte bound"));
        IllegalArgumentException countBound = assertThrows(
                IllegalArgumentException.class, () -> codec.encode(extended, 0, profile, new PduLimits(65536, 5, 0)));
        assertTrue(countBound.getMessage().contains("count"));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(tail, 0, profile, new PduLimits(65536, 4, 1)));
        assertThrows(IllegalArgumentException.class, () -> codec.decode(tail, 0, profile, new PduLimits(65536, 5, 0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode(Arrays.copyOf(tail, tail.length - 1), 0, profile, LIMITS));
    }

    @Test
    void operationAddressAndIdentifierBoundariesDoNotUseAlertLimitsForMessageAddresses() {
        PduCodec codec = new PduCodec(CommonCommandCodecs.all(), LIMITS);
        for (SmppVersion version : SmppVersion.values()) {
            ProtocolProfile profile = ProtocolProfile.forVersion(version);
            QuerySm query = new QuerySm("i".repeat(64), new Address(6, 18, "a".repeat(20)), EMPTY);
            assertEquals(
                    query,
                    codec.decode(codec.encode(new Pdu<>(0, 1, query), profile), profile)
                            .command());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> codec.encode(
                            new Pdu<>(0, 1, new QuerySm("", new Address(0, 0, "a".repeat(21)), EMPTY)), profile));
            AlertNotification alert =
                    new AlertNotification(new Address(0, 0, "a".repeat(64)), new Address(0, 0, "b".repeat(64)), EMPTY);
            assertEquals(
                    alert,
                    codec.decode(codec.encode(new Pdu<>(0, 1, alert), profile), profile)
                            .command());
            CancelSm selection = new CancelSm("", "", new Address(0, 0, ""), new Address(0, 0, ""), EMPTY);
            assertEquals(
                    selection,
                    codec.decode(codec.encode(new Pdu<>(0, 1, selection), profile), profile)
                            .command());
            assertThrows(IllegalArgumentException.class, () -> codec.decode(raw(3, 0, "ff00000000"), profile));
            assertThrows(IllegalArgumentException.class, () -> codec.decode(raw(0x21, 0, "0000000000"), profile));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> codec.decode(raw(0x21, 0, "00000000010300000000000000000000"), profile));
            assertThrows(IllegalArgumentException.class, () -> codec.decode(raw(0x80000021L, 0, "00ff"), profile));
        }
    }
}
