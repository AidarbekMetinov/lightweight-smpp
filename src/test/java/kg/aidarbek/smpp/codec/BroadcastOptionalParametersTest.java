package kg.aidarbek.smpp.codec;

import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.EMPTY;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.REQUIRED;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.SOURCE;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.V5;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.broadcast;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.codec;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.frame;
import static kg.aidarbek.smpp.codec.BroadcastCommandCodecsTest.parameters;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import kg.aidarbek.smpp.profile.BroadcastTlvRules;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BroadcastSm;
import kg.aidarbek.smpp.protocol.BroadcastSmResponse;
import kg.aidarbek.smpp.protocol.CancelBroadcastSm;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.QueryBroadcastSm;
import kg.aidarbek.smpp.protocol.QueryBroadcastSmResponse;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

class BroadcastOptionalParametersTest {
    @Test
    void exactOperationTablesKeepResponsesQueriesAndCancellationSeparate() {
        assertEquals(
                Set.of(
                        0x0600, 0x0601, 0x0602, 0x0603, 0x0604, 0x0605, 0x0606, 0x060a, 0x130c, 0x0381, 0x0303, 0x0302,
                        0x0005, 0x0203, 0x020b, 0x1201, 0x020d, 0x0424, 0x1204, 0x0019, 0x0201, 0x1203, 0x000d, 0x020a,
                        0x0202, 0x0204),
                BroadcastTlvRules.permittedTags(SmppVersion.V5_0, 0x111, 0));
        assertEquals(Set.of(0x0606, 0x0607, 0x0428), BroadcastTlvRules.permittedTags(SmppVersion.V5_0, 0x80000111L, 0));
        assertEquals(Set.of(0x0204), BroadcastTlvRules.permittedTags(SmppVersion.V5_0, 0x112, 0));
        assertEquals(
                Set.of(0x0427, 0x0606, 0x0608, 0x0609, 0x0204, 0x0428),
                BroadcastTlvRules.permittedTags(SmppVersion.V5_0, 0x80000112L, 0));
        assertEquals(Set.of(0x0601, 0x0204), BroadcastTlvRules.permittedTags(SmppVersion.V5_0, 0x113, 0));
        assertEquals(Set.of(0x0428), BroadcastTlvRules.permittedTags(SmppVersion.V5_0, 0x80000113L, 0));
        assertThrows(IllegalArgumentException.class, () -> BroadcastTlvRules.permittedTags(SmppVersion.V3_4, 0x111, 0));
        assertThrows(IllegalArgumentException.class, () -> BroadcastTlvRules.permittedTags(SmppVersion.V5_0, 4, 0));
    }

    @Test
    void requiredAndSingletonRulesApplyInBothDirectionsButUnknownInputIsPreserved() {
        for (int required : new int[] {0x0601, 0x0604, 0x0605, 0x0606}) {
            OptionalParameters missing = new OptionalParameters(
                    REQUIRED.entries().stream().filter(t -> t.tag() != required).toList());
            assertThrows(IllegalArgumentException.class, () -> codec().encode(new Pdu<>(0, 1, broadcast(missing)), V5));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> codec().decode(frame(0x111, 0, "4342530001013132330000000000000400" + hex(missing)), V5));
        }
        OptionalParameters duplicate = append(REQUIRED, new Tlv(0x0601, new byte[] {0, 0, 0}));
        assertThrows(IllegalArgumentException.class, () -> codec().encode(new Pdu<>(0, 1, broadcast(duplicate)), V5));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec().decode(frame(0x111, 0, "4342530001013132330000000000000400" + hex(duplicate)), V5));
        OptionalParameters extended = append(
                REQUIRED,
                new Tlv(0x1400, new byte[] {9}),
                new Tlv(0x1400, new byte[] {8}),
                new Tlv(0x060b, new byte[] {}));
        assertEquals(
                broadcast(extended),
                codec().decode(frame(0x111, 0, "4342530001013132330000000000000400" + hex(extended)), V5)
                        .command());
        assertThrows(IllegalArgumentException.class, () -> codec().encode(new Pdu<>(0, 1, broadcast(extended)), V5));
        for (int required : new int[] {0x0427, 0x0606, 0x0608}) {
            OptionalParameters full = parameters("0427000101060600010006080001ff");
            OptionalParameters missing = new OptionalParameters(
                    full.entries().stream().filter(t -> t.tag() != required).toList());
            QueryBroadcastSmResponse response =
                    new QueryBroadcastSmResponse(new MessageResponse(Optional.of("id"), missing));
            assertThrows(IllegalArgumentException.class, () -> codec().encode(new Pdu<>(0, 1, response), V5));
        }
    }

    @Test
    void immediatePriorityOmitsRepetitionAndIgnoresItOnInputIncludingMalformedValues() {
        OptionalParameters immediate = new OptionalParameters(
                REQUIRED.entries().stream().filter(t -> t.tag() != 0x0604).toList());
        BroadcastSm request = new BroadcastSm("CBS", SOURCE, "", 1, "", "", 0, 4, 0, immediate);
        byte[] encoded = codec().encode(new Pdu<>(0, 0x10203, request), V5);
        assertEquals(request, codec().decode(encoded, V5).command());
        OptionalParameters supplied = append(immediate, new Tlv(0x0604, new byte[] {7}));
        BroadcastSm input = new BroadcastSm("CBS", SOURCE, "", 1, "", "", 0, 4, 0, supplied);
        assertEquals(
                input,
                codec().decode(frame(0x111, 0, "4342530001013132330000010000000400" + hex(supplied)), V5)
                        .command());
        assertThrows(IllegalArgumentException.class, () -> codec().encode(new Pdu<>(0, 1, input), V5));
    }

    @Test
    void recognizedMalformedAndReservedValuesAreDistinctFromUnknownRawInput() {
        OptionalParameters malformed = append(REQUIRED, new Tlv(0x0600, new byte[] {0, 0}));
        assertThrows(FieldCodecException.class, () -> codec().encode(new Pdu<>(0, 1, broadcast(malformed)), V5));
        assertThrows(
                FieldCodecException.class,
                () -> codec().decode(frame(0x111, 0, "4342530001013132330000000000000400" + hex(malformed)), V5));
        OptionalParameters reserved = append(REQUIRED, new Tlv(0x0600, new byte[] {2}));
        BroadcastSm incoming =
                (BroadcastSm) codec().decode(frame(0x111, 0, "4342530001013132330000000000000400" + hex(reserved)), V5)
                        .command();
        assertEquals(reserved, incoming.optionalParameters());
        assertTrue(BroadcastCommandCodecs.tlvRegistry()
                .decode(SmppVersion.V5_0, 0x111, new Tlv(0x0600, new byte[] {2}), OctetString.class)
                .isEmpty());
        assertThrows(IllegalArgumentException.class, () -> codec().encode(new Pdu<>(0, 1, broadcast(reserved)), V5));
        Tlv area = new Tlv(0x0606, new byte[] {0, 65});
        assertEquals(
                Optional.of(new OctetString(area.value())),
                BroadcastCommandCodecs.tlvRegistry().decode(SmppVersion.V5_0, 0x111, area, OctetString.class));
        assertTrue(BroadcastCommandCodecs.tlvRegistry()
                .decode(SmppVersion.V3_4, 0x111, area, OctetString.class)
                .isEmpty());
        assertTrue(BroadcastCommandCodecs.tlvRegistry()
                .decode(SmppVersion.V5_0, 0x113, area, OctetString.class)
                .isEmpty());
    }

    @Test
    void identityReplacementAndNetworkPriorityConditionsAreExplicit() {
        OptionalParameters reference = append(REQUIRED, new Tlv(0x0204, new byte[] {0, 9}));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec().encode(
                                new Pdu<>(0, 1, new BroadcastSm("", SOURCE, "id", 0, "", "", 1, 4, 0, reference)), V5));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec().encode(
                                new Pdu<>(0, 1, new BroadcastSm("", SOURCE, "", 0, "", "", 1, 4, 0, REQUIRED)), V5));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec().encode(new Pdu<>(0, 1, new QueryBroadcastSm("", SOURCE, EMPTY)), V5));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec().encode(
                                new Pdu<>(0, 1, new QueryBroadcastSm("id", SOURCE, parameters("020400020009"))), V5));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec().encode(
                                new Pdu<>(0, 1, new CancelBroadcastSm("", "id", SOURCE, parameters("020400020009"))),
                                V5));
        for (int network : new int[] {1, 2, 3}) {
            List<Tlv> values = new ArrayList<>(
                    REQUIRED.entries().stream().filter(t -> t.tag() != 0x0601).toList());
            values.add(new Tlv(0x0601, new byte[] {(byte) network, 0, 0}));
            int invalid = network == 1 ? 3 : 4;
            BroadcastSm bad = new BroadcastSm("", SOURCE, "", invalid, "", "", 0, 4, 0, new OptionalParameters(values));
            assertThrows(IllegalArgumentException.class, () -> codec().encode(new Pdu<>(0, 1, bad), V5));
        }
        assertDoesNotThrow(() ->
                codec().encode(new Pdu<>(0, 1, new QueryBroadcastSm("", SOURCE, parameters("020400020000"))), V5));
        assertDoesNotThrow(() -> codec().encode(new Pdu<>(0, 1, new CancelBroadcastSm("CBS", "", SOURCE, EMPTY)), V5));
        assertDoesNotThrow(() ->
                codec().encode(new Pdu<>(0, 1, new BroadcastSm("", SOURCE, "", 0, "", "", 1, 4, 0, reference)), V5));
    }

    @Test
    void callbackAndAreaResultCompanionsRetainOrderedParallelCounts() {
        OptionalParameters callbacks = append(
                REQUIRED,
                new Tlv(0x0381, new byte[] {1, 1, 1, 49}),
                new Tlv(0x0381, new byte[] {1, 1, 1, 50}),
                new Tlv(0x0302, new byte[] {0}));
        assertThrows(IllegalArgumentException.class, () -> codec().encode(new Pdu<>(0, 1, broadcast(callbacks)), V5));
        OptionalParameters fixed = append(callbacks, new Tlv(0x0302, new byte[] {1}));
        assertEquals(
                broadcast(fixed),
                codec().decode(codec().encode(new Pdu<>(0, 1, broadcast(fixed)), V5), V5)
                        .command());
        for (OptionalParameters broken :
                List.of(parameters("0606000200410606000200420607000400000144"), parameters("0607000400000144")))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> codec().encode(
                                    new Pdu<>(
                                            0,
                                            1,
                                            new BroadcastSmResponse(new MessageResponse(Optional.of("id"), broken))),
                                    V5));
        OptionalParameters areas = parameters("06060002004106060002004206070004000001440607000400000145");
        BroadcastSmResponse response = new BroadcastSmResponse(new MessageResponse(Optional.of("id"), areas));
        assertEquals(
                response,
                codec().decode(codec().encode(new Pdu<>(0, 1, response), V5), V5)
                        .command());
        OptionalParameters unpaired = parameters("04270001010606000200410606000200420608000132");
        assertThrows(
                IllegalArgumentException.class,
                () -> codec().encode(
                                new Pdu<>(
                                        0,
                                        1,
                                        new QueryBroadcastSmResponse(new MessageResponse(Optional.of("id"), unpaired))),
                                V5));
        OptionalParameters paired = append(unpaired, new Tlv(0x0608, new byte[] {(byte) 255}));
        QueryBroadcastSmResponse query = new QueryBroadcastSmResponse(new MessageResponse(Optional.of("id"), paired));
        assertEquals(
                query,
                codec().decode(codec().encode(new Pdu<>(0, 1, query), V5), V5).command());
        OptionalParameters premature = append(
                paired, new Tlv(0x0609, "260909123456000+\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec().encode(
                                new Pdu<>(
                                        0,
                                        1,
                                        new QueryBroadcastSmResponse(
                                                new MessageResponse(Optional.of("id"), premature))),
                                V5));
    }

    static OptionalParameters append(OptionalParameters base, Tlv... entries) {
        List<Tlv> all = new ArrayList<>(base.entries());
        all.addAll(List.of(entries));
        return new OptionalParameters(all);
    }

    static String hex(OptionalParameters parameters) {
        return java.util.HexFormat.of().formatHex(TlvCodec.encode(parameters, 66000, 64));
    }
}
