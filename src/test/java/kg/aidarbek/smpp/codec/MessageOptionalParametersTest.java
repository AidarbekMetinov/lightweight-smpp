package kg.aidarbek.smpp.codec;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
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
import kg.aidarbek.smpp.protocol.SubmitSm;
import kg.aidarbek.smpp.protocol.SubmitSmResponse;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

class MessageOptionalParametersTest {
    @Test
    void outgoingAggregateLimitsPrecedeValueInterpretationForEveryMessageCommand() {
        OptionalParameters malformedRequest = parameters(tlv(0x0201, "0102"));
        MessageResponse malformedResponse = new MessageResponse(Optional.of(""), parameters(tlv(0x001d, "ff")));
        SubmitSm submit = submit(0, 0, malformedRequest);
        List<Command> commands = List.of(
                submit,
                new DeliverSm(submit.fields()),
                new DataSm("", new Address(0, 0, ""), new Address(0, 0, ""), 0, 0, 0, malformedRequest),
                new SubmitSmResponse(malformedResponse),
                new DeliverSmResponse(malformedResponse),
                new DataSmResponse(malformedResponse));
        for (PduLimits limits : List.of(new PduLimits(100, 0, 10), new PduLimits(100, 10, 0))) {
            PduCodec codec = new PduCodec(MessageCommandCodecs.all(MessageDirection.SUBMISSION), limits);
            String expected = limits.maximumTlvLength() == 0
                    ? "TLV block exceeds configured byte bound"
                    : "TLV count exceeds configured bound";
            for (Command command : commands) {
                IllegalArgumentException failure = assertThrows(
                        IllegalArgumentException.class,
                        () -> codec.encode(new Pdu<>(0, 1, command), ProtocolProfile.forVersion(SmppVersion.V5_0)));
                assertEquals(expected, failure.getMessage(), "command ID " + command.commandId());
            }
        }
    }

    @Test
    void typedInterpretationRequiresExactProfileCommandDirectionAndMalformedTlvFramingFails() {
        TypedTlvRegistry submission = MessageCommandCodecs.tlvRegistry(MessageDirection.SUBMISSION);
        TypedTlvRegistry delivery = MessageCommandCodecs.tlvRegistry(MessageDirection.DELIVERY);
        Tlv billing = tlv(0x060b, "80");
        assertTrue(submission
                .decode(SmppVersion.V5_0, 0x103, billing, OctetString.class)
                .isPresent());
        assertTrue(delivery.decode(SmppVersion.V5_0, 0x103, billing, OctetString.class)
                .isEmpty());
        assertTrue(submission
                .decode(SmppVersion.V3_4, 0x103, billing, OctetString.class)
                .isEmpty());
        assertTrue(submission
                .decode(SmppVersion.V5_0, 5, billing, OctetString.class)
                .isEmpty());
        assertEquals(
                billing,
                submission.encode(
                        SmppVersion.V5_0, 0x103, 0x060b, new OctetString(new byte[] {(byte) 128}), OctetString.class));
        byte[] base = encode(submit(0, 0, parameters()), SmppVersion.V5_0);
        for (String tail : List.of("04", "0424", "042400", "0424000201")) {
            byte[] extra = HexFormat.of().parseHex(tail);
            byte[] peer = Arrays.copyOf(base, base.length + extra.length);
            System.arraycopy(extra, 0, peer, base.length, extra.length);
            ByteBuffer.wrap(peer).putInt(peer.length);
            assertThrows(FieldCodecException.class, () -> MessageCodecValidationTest.decode(peer, SmppVersion.V5_0));
        }
    }

    @Test
    void outgoingUnknownUnexpectedAndReservedValuesFailWhileIncomingRawEntriesSurvive() {
        for (SmppVersion version : SmppVersion.values()) {
            for (Tlv entry : List.of(tlv(0x7777, "01"), tlv(0x0427, "02"), tlv(0x0201, "ff"))) {
                SubmitSm proposed = submit(0, 0, new OptionalParameters(List.of(entry)));
                assertThrows(IllegalArgumentException.class, () -> encode(proposed, version));
                byte[] incoming = append(encode(submit(0, 0, new OptionalParameters(List.of())), version), entry);
                SubmitSm decoded = (SubmitSm)
                        MessageCodecValidationTest.decode(incoming, version).command();
                assertEquals(
                        List.of(entry), decoded.fields().optionalParameters().entries());
            }
            byte[] malformedKnown =
                    append(encode(submit(0, 0, new OptionalParameters(List.of())), version), tlv(0x0201, ""));
            assertThrows(FieldCodecException.class, () -> MessageCodecValidationTest.decode(malformedKnown, version));
            byte[] unknownWrongKnownWidth =
                    append(encode(submit(0, 0, new OptionalParameters(List.of())), version), tlv(0x0427, "010203"));
            assertDoesNotThrow(() -> MessageCodecValidationTest.decode(unknownWrongKnownWidth, version));
        }
    }

    @Test
    void payloadAlternativesAreProfileAwareAndCountsAndConfiguredBoundsRemainEnforced() {
        for (SmppVersion version : SmppVersion.values()) {
            SubmitSm both = submit(0, 1, parameters(tlv(0x0424, "0102")));
            assertThrows(IllegalArgumentException.class, () -> encode(both, version));
            byte[] peer = append(encode(submit(0, 1, new OptionalParameters(List.of())), version), tlv(0x0424, "0102"));
            if (version == SmppVersion.V3_4)
                assertThrows(IllegalArgumentException.class, () -> MessageCodecValidationTest.decode(peer, version));
            else {
                SubmitSm decoded = (SubmitSm)
                        MessageCodecValidationTest.decode(peer, version).command();
                assertEquals(1, decoded.fields().shortMessage().length());
                assertEquals(
                        2,
                        decoded.fields()
                                .optionalParameters()
                                .entries()
                                .getFirst()
                                .valueLength());
            }
            SubmitSm duplicate = submit(0, 0, parameters(tlv(0x0424, ""), tlv(0x0424, "")));
            assertThrows(IllegalArgumentException.class, () -> encode(duplicate, version));
            for (int size : new int[] {0, 254, 255, 65535}) {
                SubmitSm payload = submit(0, 0, parameters(new Tlv(0x0424, new byte[size])));
                assertEquals(
                        payload,
                        MessageCodecValidationTest.decode(encode(payload, version), version)
                                .command());
            }
            byte[] withTwo = append(encode(submit(0, 0, parameters(tlv(0x0424, "01"))), version), tlv(0x7777, ""));
            PduCodec limited =
                    new PduCodec(MessageCommandCodecs.all(MessageDirection.SUBMISSION), new PduLimits(70000, 100, 1));
            assertThrows(
                    IllegalArgumentException.class, () -> limited.decode(withTwo, ProtocolProfile.forVersion(version)));
            PduCodec byteLimited =
                    new PduCodec(MessageCommandCodecs.all(MessageDirection.SUBMISSION), new PduLimits(70000, 4, 10));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> byteLimited.decode(withTwo, ProtocolProfile.forVersion(version)));
        }
    }

    @Test
    void sarCompanionsAndUdhiOnlyUseSupportedIncomingSemantics() {
        for (SmppVersion version : SmppVersion.values()) {
            OptionalParameters complete = parameters(tlv(0x020c, "1234"), tlv(0x020e, "02"), tlv(0x020f, "01"));
            assertDoesNotThrow(() -> encode(submit(0, 1, complete), version));
            assertThrows(IllegalArgumentException.class, () -> encode(submit(0x40, 1, complete), version));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> encode(submit(0x40, 1, parameters(tlv(0x020a, "0001"))), version));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> encode(submit(0, 1, parameters(tlv(0x020c, "1234"))), version));
            OptionalParameters invalid = parameters(tlv(0x020c, "1234"), tlv(0x020e, "02"), tlv(0x020f, "03"));
            assertThrows(IllegalArgumentException.class, () -> encode(submit(0, 1, invalid), version));
            byte[] raw = encode(submit(0x40, 1, new OptionalParameters(List.of())), version);
            byte[] incomplete = append(raw, tlv(0x020c, "1234"));
            assertDoesNotThrow(() -> MessageCodecValidationTest.decode(incomplete, version));
            byte[] unsupported = append(incomplete, tlv(0x020e, "00"), tlv(0x020f, "01"));
            assertDoesNotThrow(() -> MessageCodecValidationTest.decode(unsupported, version));
        }
    }

    @Test
    void callbackAndNetworkCompanionsAreCorrelatedWithoutReordering() {
        Tlv callback = tlv(0x0381, "01010131"), pres = tlv(0x0302, "01"), alpha = tlv(0x0303, "0431");
        OptionalParameters correlated = parameters(callback, pres, callback, alpha, pres, alpha);
        assertEquals(
                correlated,
                ((SubmitSm) MessageCodecValidationTest.decode(
                                        encode(submit(0, 0, correlated), SmppVersion.V5_0), SmppVersion.V5_0)
                                .command())
                        .fields()
                        .optionalParameters());
        for (OptionalParameters invalid : List.of(
                parameters(callback, callback, pres),
                parameters(alpha),
                parameters(pres),
                parameters(tlv(0x060d, "31303031303100")),
                parameters(tlv(0x0611, "02"))))
            assertThrows(IllegalArgumentException.class, () -> encode(submit(0, 0, invalid), SmppVersion.V5_0));
        OptionalParameters network = parameters(
                tlv(0x060d, "31303031303100"),
                tlv(0x060f, "313233343536"),
                tlv(0x0611, "02"),
                tlv(0x0612, "31323334353637383930"),
                tlv(0x0613, "01"));
        assertDoesNotThrow(() -> encode(submit(0, 0, network), SmppVersion.V5_0));
    }

    @Test
    void dataDirectionAndExplicitVendorContextControlOutgoingPermission() {
        DataSm data =
                new DataSm("", new Address(0, 0, ""), new Address(0, 0, ""), 0, 0, 0, parameters(tlv(0x060b, "80")));
        assertDoesNotThrow(() -> encode(data, SmppVersion.V5_0));
        PduCodec delivery =
                new PduCodec(MessageCommandCodecs.all(MessageDirection.DELIVERY), new PduLimits(1000, 500, 10));
        assertThrows(
                IllegalArgumentException.class,
                () -> delivery.encode(new Pdu<>(0, 1, data), ProtocolProfile.forVersion(SmppVersion.V5_0)));
        MessageTlvExtension extension = new MessageTlvExtension(
                SmppVersion.V5_0,
                4,
                MessageDirection.SUBMISSION,
                0x1400,
                new MessageTlvValueCodec(SmppVersion.V5_0, 0x0424),
                false);
        PduCodec extended = new PduCodec(
                MessageCommandCodecs.all(MessageDirection.SUBMISSION, List.of(extension)),
                new PduLimits(1000, 500, 10));
        SubmitSm vendor = submit(0, 0, parameters(tlv(0x1400, "00ff")));
        assertDoesNotThrow(
                () -> extended.encode(new Pdu<>(0, 1, vendor), ProtocolProfile.forVersion(SmppVersion.V5_0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> extended.encode(new Pdu<>(0, 1, vendor), ProtocolProfile.forVersion(SmppVersion.V3_4)));
        assertThrows(
                IllegalArgumentException.class,
                () -> extended.encode(
                        new Pdu<>(0, 1, new DeliverSm(vendor.fields())), ProtocolProfile.forVersion(SmppVersion.V5_0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> MessageCommandCodecs.all(MessageDirection.SUBMISSION, List.of(extension, extension)));
    }

    static SubmitSm submit(int esm, int shortLength, OptionalParameters parameters) {
        return new SubmitSm(MessageCodecValidationTest.fields(0, 0, shortLength, esm, 0, "", "", 0, 0, 0, parameters));
    }

    static OptionalParameters parameters(Tlv... entries) {
        return new OptionalParameters(List.of(entries));
    }

    static Tlv tlv(int tag, String hex) {
        return new Tlv(tag, HexFormat.of().parseHex(hex));
    }

    private static byte[] encode(Command command, SmppVersion version) {
        return MessageCodecValidationTest.encode(command, version);
    }

    static byte[] append(byte[] frame, Tlv... entries) {
        byte[] extra = TlvCodec.encode(parameters(entries), 66000, 128);
        byte[] result = Arrays.copyOf(frame, frame.length + extra.length);
        System.arraycopy(extra, 0, result, frame.length, extra.length);
        ByteBuffer.wrap(result).putInt(result.length);
        return result;
    }
}
