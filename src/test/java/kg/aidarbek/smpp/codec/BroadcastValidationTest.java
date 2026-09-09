package kg.aidarbek.smpp.codec;

import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.EMPTY;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.REQUIRED;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.SOURCE;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.V5;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.codec;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.frame;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.parameters;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.BroadcastSm;
import kg.aidarbek.smpp.protocol.BroadcastSmResponse;
import kg.aidarbek.smpp.protocol.CancelBroadcastSmResponse;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.QueryBroadcastSmResponse;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

class BroadcastValidationTest {
    @Test
    void failedResponsesIgnoreBoundedIncomingBodyAndRequireCanonicalOmissionOnOutput() {
        for (long id : new long[] {0x80000111L, 0x80000112L, 0x80000113L}) {
            Command omitted = id == 0x80000111L
                    ? new BroadcastSmResponse(new MessageResponse(Optional.empty(), EMPTY))
                    : id == 0x80000112L
                            ? new QueryBroadcastSmResponse(new MessageResponse(Optional.empty(), EMPTY))
                            : new CancelBroadcastSmResponse(EMPTY);
            Pdu<Command> expected = new Pdu<>(0x144, 0x10203, omitted);
            assertEquals(expected, codec().decode(frame(id, 0x144, "ff00ab"), V5));
            assertArrayEquals(frame(id, 0x144, ""), codec().encode(expected, V5));
            Command body = id == 0x80000111L
                    ? new BroadcastSmResponse(new MessageResponse(Optional.of(""), EMPTY))
                    : id == 0x80000112L
                            ? new QueryBroadcastSmResponse(
                                    new MessageResponse(Optional.of(""), parameters("0427000101")))
                            : new CancelBroadcastSmResponse(parameters("0428000100"));
            assertThrows(IllegalArgumentException.class, () -> codec().encode(new Pdu<>(1, 1, body), V5));
        }
        assertThrows(IllegalArgumentException.class, () -> codec().decode(frame(0x80000111L, 0, ""), V5));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec().encode(
                                new Pdu<>(0, 1, new BroadcastSmResponse(new MessageResponse(Optional.empty(), EMPTY))),
                                V5));
    }

    @Test
    void nonReplacementOutputDoesNotPretendToAssignAMessageCenterIdentity() {
        BroadcastSm outgoing = new BroadcastSm("CBS", SOURCE, "id", 0, "", "", 0, 4, 0, REQUIRED);
        assertThrows(IllegalArgumentException.class, () -> codec().encode(new Pdu<>(0, 1, outgoing), V5));
        assertEquals(
                outgoing,
                codec().decode(
                                frame(
                                        0x111,
                                        0,
                                        "43425300010131323300696400000000000400"
                                                + BroadcastOptionalParametersTest.hex(REQUIRED)),
                                V5)
                        .command());
    }

    @Test
    void ignoredIncomingNonReplacementIdentityCannotConflictWithUserReference() {
        OptionalParameters referenced =
                BroadcastOptionalParametersTest.append(REQUIRED, new Tlv(0x0204, new byte[] {0, 9}));
        for (String flag : List.of("00", "02", "ff")) {
            BroadcastSm expected =
                    new BroadcastSm("CBS", SOURCE, "id", 0, "", "", Integer.parseInt(flag, 16), 4, 0, referenced);
            assertEquals(
                    expected,
                    codec().decode(
                                    frame(
                                            0x111,
                                            0,
                                            "43425300010131323300696400000000" + flag + "0400"
                                                    + BroadcastOptionalParametersTest.hex(referenced)),
                                    V5)
                            .command());
            assertThrows(IllegalArgumentException.class, () -> codec().encode(new Pdu<>(0, 1, expected), V5));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> codec().decode(
                                frame(
                                        0x111,
                                        0,
                                        "43425300010131323300696400000000010400"
                                                + BroadcastOptionalParametersTest.hex(referenced)),
                                V5));
    }

    @Test
    void addressAndTimesRespectBoundsWhileReservedIncomingNumericFieldsRemainRaw() {
        BroadcastSm legal = new BroadcastSm(
                "ABCDE",
                new Address(6, 18, "x".repeat(20)),
                "m".repeat(64),
                4,
                "260909123456048+",
                "000007000000000R",
                1,
                255,
                255,
                REQUIRED);
        byte[] frame = codec().encode(new Pdu<>(0, 1, legal), V5);
        assertEquals(legal, codec().decode(frame, V5).command());
        for (BroadcastSm invalid : List.of(
                new BroadcastSm("", new Address(1, 1, "x".repeat(21)), "", 0, "", "", 0, 0, 0, REQUIRED),
                new BroadcastSm("", new Address(7, 1, ""), "", 0, "", "", 0, 0, 0, REQUIRED),
                new BroadcastSm("", SOURCE, "", 5, "", "", 0, 0, 0, REQUIRED),
                new BroadcastSm("", SOURCE, "", 0, "", "", 2, 0, 0, REQUIRED),
                new BroadcastSm("", SOURCE, "", 0, "260000000000000+", "", 0, 0, 0, REQUIRED),
                new BroadcastSm("", SOURCE, "", 0, "", "000007000000100R", 0, 0, 0, REQUIRED)))
            assertThrows(IllegalArgumentException.class, () -> codec().encode(new Pdu<>(0, 1, invalid), V5));
        // Empty strings give fixed offsets: TON17, priority21 and replacement24.
        byte[] raw = codec().encode(
                        new Pdu<>(0, 1, new BroadcastSm("", new Address(0, 0, ""), "", 0, "", "", 0, 4, 0, REQUIRED)),
                        V5);
        raw[17] = (byte) 255;
        raw[21] = (byte) 255;
        raw[24] = (byte) 255;
        BroadcastSm decoded = (BroadcastSm) codec().decode(raw, V5).command();
        assertEquals(255, decoded.source().ton());
        assertEquals(255, decoded.priorityFlag());
        assertEquals(255, decoded.replaceIfPresentFlag());
    }
}
