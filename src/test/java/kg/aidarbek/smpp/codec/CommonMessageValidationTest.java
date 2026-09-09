package kg.aidarbek.smpp.codec;

import static kg.aidarbek.smpp.codec.CommonCommandCodecsTest.EMPTY;
import static kg.aidarbek.smpp.codec.CommonCommandCodecsTest.LIMITS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.AlertNotification;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.MultiDestination;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.ReplaceSm;
import kg.aidarbek.smpp.protocol.SubmitMulti;
import kg.aidarbek.smpp.protocol.SubmitMultiResponse;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

class CommonMessageValidationTest {
    private final PduCodec codec = new PduCodec(CommonCommandCodecs.all(), LIMITS);
    private static final ProtocolProfile V34 = ProtocolProfile.forVersion(SmppVersion.V3_4);
    private static final ProtocolProfile V50 = ProtocolProfile.forVersion(SmppVersion.V5_0);

    @Test
    void payloadAndDestinationCountsUseTheActualProfileLimits() {
        for (int length : new int[] {0, 254, 255}) {
            ReplaceSm replace = replacement(length, 0, 0, EMPTY);
            SubmitMulti multi = multi(1, length, 0, 0, 0, EMPTY);
            for (ProtocolProfile profile : List.of(V34, V50)) {
                if (profile == V34 && length == 255) {
                    assertThrows(IllegalArgumentException.class, () -> encode(replace, profile));
                    assertThrows(IllegalArgumentException.class, () -> encode(multi, profile));
                } else {
                    assertEquals(27 + length, encode(replace, profile).length);
                    assertEquals(35 + length, encode(multi, profile).length);
                }
            }
        }
        for (int count : new int[] {1, 254, 255}) {
            SubmitMulti command = multi(count, 0, 0, 0, 0, EMPTY);
            byte[] encoded = encode(command, V50);
            assertEquals(
                    count,
                    ((SubmitMulti) codec.decode(encoded, V50).command())
                            .destinations()
                            .size());
            if (count == 255) {
                assertThrows(IllegalArgumentException.class, () -> encode(command, V34));
                assertThrows(IllegalArgumentException.class, () -> codec.decode(encoded, V34));
            } else assertArrayEquals(encoded, encode(command, V34));
        }
    }

    @Test
    void flagsTimesAndTransactionSupportDoNotLeakBetweenProfiles() {
        for (SubmitMulti value :
                List.of(multi(1, 0, 2, 0, 0, EMPTY), multi(1, 0, 0, 1, 0, EMPTY), multi(1, 0, 0, 0, 255, EMPTY))) {
            assertThrows(IllegalArgumentException.class, () -> encode(value, V34));
            encode(value, V50);
        }
        assertThrows(IllegalArgumentException.class, () -> encode(replacement(0, 3, 0, EMPTY), V34));
        encode(replacement(0, 3, 255, EMPTY), V50);
        assertThrows(IllegalArgumentException.class, () -> encode(replacement(0, 0xe0, 0, EMPTY), V50));
        ReplaceSm invalidTime = new ReplaceSm(
                "id", new Address(0, 0, ""), "261309120000000+", "", 0, 0, new OctetString(new byte[0]), EMPTY);
        assertThrows(IllegalArgumentException.class, () -> encode(invalidTime, V50));
        byte[] reserved = encode(multi(1, 0, 0, 0, 0, EMPTY), V50);
        reserved[25] = 0x3c;
        reserved[27] = (byte) 255;
        reserved[30] = (byte) 0xe0;
        reserved[31] = (byte) 255;
        SubmitMulti incoming = (SubmitMulti) codec.decode(reserved, V34).command();
        assertEquals(255, incoming.priorityFlag());
        assertEquals(255, incoming.replaceIfPresentFlag());
        assertThrows(IllegalArgumentException.class, () -> encode(incoming, V34));
    }

    @Test
    void replacementPayloadTlvIsFiveOnlyAndSupersededIncomingBytesRemainAvailable() {
        OptionalParameters payload = new OptionalParameters(List.of(new Tlv(0x0424, new byte[] {65})));
        assertThrows(IllegalArgumentException.class, () -> encode(replacement(0, 0, 0, payload), V34));
        encode(replacement(0, 0, 0, payload), V50);
        assertThrows(IllegalArgumentException.class, () -> encode(replacement(1, 0, 0, payload), V50));
        byte[] wire = raw(7, 0, "6964000000000000000001ff0424000141");
        ReplaceSm preserved = (ReplaceSm) codec.decode(wire, V50).command();
        assertArrayEquals(new byte[] {(byte) 255}, preserved.shortMessage().value());
        assertEquals(payload, preserved.optionalParameters());
        assertThrows(IllegalArgumentException.class, () -> encode(preserved, V50));
        ReplaceSm legacy = (ReplaceSm) codec.decode(wire, V34).command();
        assertEquals(payload, legacy.optionalParameters());
    }

    @Test
    void multipleResponseBodyRulesAndAlertValueSupportAreExplicit() {
        SubmitMultiResponse absent = new SubmitMultiResponse(Optional.empty(), EMPTY);
        assertThrows(IllegalArgumentException.class, () -> encode(absent, V50));
        assertThrows(IllegalArgumentException.class, () -> codec.encode(new Pdu<>(11, 1, absent), V34));
        assertEquals(16, codec.encode(new Pdu<>(11, 1, absent), V50).length);
        assertEquals(absent, codec.decode(raw(0x80000021L, 11, "ffff"), V50).command());
        for (ProtocolProfile profile : List.of(V34, V50)) {
            OptionalParameters invalid = new OptionalParameters(List.of(new Tlv(0x0422, new byte[] {3})));
            AlertNotification alert = new AlertNotification(new Address(0, 0, ""), new Address(0, 0, ""), invalid);
            assertThrows(IllegalArgumentException.class, () -> encode(alert, profile));
            assertTrue(CommonCommandCodecs.tlvRegistry()
                    .decode(profile.version(), 0x102, invalid.entries().getFirst(), OctetString.class)
                    .isEmpty());
            assertEquals(
                    alert,
                    codec.decode(raw(0x102, 0, "0000000000000422000103"), profile)
                            .command());
            assertThrows(
                    IllegalArgumentException.class, () -> codec.decode(raw(0x102, 0, "00000000000004220000"), profile));
        }
    }

    private byte[] encode(Command command, ProtocolProfile profile) {
        return codec.encode(new Pdu<>(0, 1, command), profile);
    }

    static byte[] raw(long id, long status, String bodyHex) {
        byte[] body = HexFormat.of().parseHex(bodyHex);
        return ByteBuffer.allocate(16 + body.length)
                .putInt(16 + body.length)
                .putInt((int) id)
                .putInt((int) status)
                .putInt(1)
                .put(body)
                .array();
    }

    static ReplaceSm replacement(int length, int registered, int defaultId, OptionalParameters parameters) {
        return new ReplaceSm(
                "id",
                new Address(0, 0, ""),
                "",
                "",
                registered,
                defaultId,
                new OctetString(new byte[length]),
                parameters);
    }

    static SubmitMulti multi(
            int count, int length, int esm, int replace, int defaultId, OptionalParameters parameters) {
        return new SubmitMulti(
                "",
                new Address(0, 0, ""),
                Collections.nCopies(count, new MultiDestination.Sme(new Address(0, 0, ""))),
                esm,
                0,
                0,
                "",
                "",
                0,
                replace,
                0,
                defaultId,
                new OctetString(new byte[length]),
                parameters);
    }
}
