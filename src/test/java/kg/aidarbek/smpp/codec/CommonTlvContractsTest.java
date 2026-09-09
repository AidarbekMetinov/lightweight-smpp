package kg.aidarbek.smpp.codec;

import static kg.aidarbek.smpp.codec.CommonCommandCodecsTest.EMPTY;
import static kg.aidarbek.smpp.codec.CommonCommandCodecsTest.LIMITS;
import static kg.aidarbek.smpp.codec.CommonMessageValidationTest.multi;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.stream.Collectors;
import kg.aidarbek.smpp.profile.CommonTlvRules;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.SubmitMulti;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

class CommonTlvContractsTest {
    private final PduCodec codec = new PduCodec(CommonCommandCodecs.all(), LIMITS);

    @Test
    void independentOperationTablesDoNotInheritSingleDestinationOnlyTags() {
        Set<Integer> multi34 = tags(
                "0204 020a 000d 020b 0005 020c 020e 020f 0019 0424 0201 0381 0302 0303 0202 1201 1203 1204 0030 130c 020d");
        Set<Integer> multi50 = tags(
                "0005 0006 0007 0008 000d 000e 000f 0010 0017 0019 0030 0201 0202 0204 0205 020a 020b 020c 020d 020e 020f 0302 0303 0304 0381 0421 0424 0426 0501 060b 060d 060e 060f 0610 0611 0612 0613 1201 1203 1204 130c 1380 1383");
        for (SmppVersion version : SmppVersion.values()) {
            for (long id : new long[] {3, 8, 0x0b}) assertEquals(Set.of(), CommonTlvRules.permittedTags(version, id));
            for (long id : new long[] {0x80000003L, 0x80000007L, 0x80000008L})
                assertEquals(
                        version == SmppVersion.V3_4 ? Set.of() : Set.of(0x0428),
                        CommonTlvRules.permittedTags(version, id));
            assertEquals(
                    version == SmppVersion.V3_4 ? Set.of() : Set.of(0x0424), CommonTlvRules.permittedTags(version, 7));
            assertEquals(Set.of(0x0422), CommonTlvRules.permittedTags(version, 0x102));
            assertEquals(version == SmppVersion.V3_4 ? multi34 : multi50, CommonTlvRules.permittedTags(version, 0x21));
            assertEquals(
                    version == SmppVersion.V3_4 ? Set.of() : tags("001d 0420 0423 0425 0428"),
                    CommonTlvRules.permittedTags(version, 0x80000021L));
        }
    }

    @Test
    void callbacksMayRepeatInOrderButRequireCorrespondingCompanionCounts() {
        OptionalParameters callbacks = parameters(
                0x0381, "01010131", 0x0381, "01010132", 0x0302, "00", 0x0302, "01", 0x0303, "084e00", 0x0303, "084e01");
        for (SmppVersion version : SmppVersion.values()) {
            SubmitMulti value = multi(2, 0, 0, 0, 0, callbacks);
            assertEquals(
                    value,
                    codec.decode(encode(value, version), ProtocolProfile.forVersion(version))
                            .command());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> encode(
                            multi(2, 0, 0, 0, 0, parameters(0x0381, "01010131", 0x0302, "00", 0x0302, "01")), version));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> encode(multi(1, 0, 0, 0, 0, parameters(0x0303, "08")), version));
        }
    }

    @Test
    void supportedSarPortsAndNetworkCompanionsRemainCoherent() {
        for (SmppVersion version : SmppVersion.values()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> encode(multi(1, 0, 0, 0, 0, parameters(0x020c, "0001")), version));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> encode(
                            multi(1, 0, 0, 0, 0, parameters(0x020c, "0001", 0x020e, "01", 0x020f, "02")), version));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> encode(multi(1, 0, 0x40, 0, 0, parameters(0x020a, "0001")), version));
            OptionalParameters complete = parameters(0x020c, "0001", 0x020e, "02", 0x020f, "01");
            encode(multi(1, 0, 0, 0, 0, complete), version);
            assertThrows(IllegalArgumentException.class, () -> incoming(version, 0x40, complete));
            assertEquals(
                    parameters(0x020c, "0001"),
                    incoming(version, 0x40, parameters(0x020c, "0001")).optionalParameters());
            assertEquals(
                    parameters(0x020c, "0001", 0x020e, "00", 0x020f, "01"),
                    incoming(version, 0x40, parameters(0x020c, "0001", 0x020e, "00", 0x020f, "01"))
                            .optionalParameters());
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> encode(multi(1, 0, 0, 0, 0, parameters(0x060d, "31333130323600")), SmppVersion.V5_0));
        OptionalParameters network = parameters(0x060d, "31333130323600", 0x060f, "313233343536");
        encode(multi(1, 0, 0, 0, 0, network), SmppVersion.V5_0);
        assertThrows(
                IllegalArgumentException.class,
                () -> encode(multi(1, 0, 0, 0, 0, parameters(0x0611, "02")), SmppVersion.V5_0));
        encode(
                multi(1, 0, 0, 0, 0, parameters(0x0611, "02", 0x0612, "31323334353637383930", 0x0613, "31")),
                SmppVersion.V5_0);
    }

    @Test
    void unsupportedRawInputStaysOwnedWhileKnownMalformedValuesFail() {
        for (SmppVersion version : SmppVersion.values()) {
            OptionalParameters ignored = parameters(0x0203, "", 0x0203, "ff", 0x1500, "abcd");
            assertEquals(ignored, incoming(version, 0, ignored).optionalParameters());
            assertThrows(IllegalArgumentException.class, () -> encode(multi(1, 0, 0, 0, 0, ignored), version));
            assertThrows(IllegalArgumentException.class, () -> incoming(version, 0, parameters(0x0204, "01")));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> incoming(version, 0, parameters(0x0204, "0001", 0x0204, "0002")));
            assertTrue(CommonCommandCodecs.tlvRegistry()
                    .decode(version, 0x21, new Tlv(0x020e, new byte[] {0}), OctetString.class)
                    .isEmpty());
        }
    }

    private byte[] encode(SubmitMulti value, SmppVersion version) {
        return codec.encode(new Pdu<>(0, 1, value), ProtocolProfile.forVersion(version));
    }

    private SubmitMulti incoming(SmppVersion version, int esm, OptionalParameters parameters) {
        byte[] base = encode(multi(1, 0, esm, 0, 0, EMPTY), version);
        byte[] tail = TlvCodec.encode(parameters, 60000, 100);
        byte[] frame = Arrays.copyOf(base, base.length + tail.length);
        System.arraycopy(tail, 0, frame, base.length, tail.length);
        ByteBuffer.wrap(frame).putInt(frame.length);
        return (SubmitMulti)
                codec.decode(frame, ProtocolProfile.forVersion(version)).command();
    }

    private static Set<Integer> tags(String hex) {
        return Arrays.stream(hex.split(" "))
                .map(value -> Integer.parseInt(value, 16))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static OptionalParameters parameters(Object... values) {
        java.util.ArrayList<Tlv> entries = new java.util.ArrayList<>();
        for (int i = 0; i < values.length; i += 2)
            entries.add(new Tlv((int) values[i], HexFormat.of().parseHex((String) values[i + 1])));
        return new OptionalParameters(entries);
    }
}
