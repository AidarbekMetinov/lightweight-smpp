package kg.aidarbek.smpp.codec;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import kg.aidarbek.smpp.profile.MessageDirection;
import kg.aidarbek.smpp.profile.MessageTlvRules;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.OctetString;
import org.junit.jupiter.api.Test;

class MessageTlvValueCodecTest {
    @Test
    void ucs2CallbackDisplayUsesCompleteOctetPairsAndReservedCodingHasNoInterpretation() {
        for (SmppVersion version : SmppVersion.values()) {
            MessageTlvValueCodec codec = new MessageTlvValueCodec(version, 0x0303);
            assertThrows(FieldCodecException.class, () -> codec.decode(new byte[] {8, 65}));
            assertTrue(codec.decode(new byte[] {8, 0, 65}).isPresent());
            assertTrue(codec.decode(new byte[] {11, 65}).isEmpty());
            assertThrows(IllegalArgumentException.class, () -> codec.encode(new OctetString(new byte[] {11, 65})));
        }
    }

    private record Fixture(int tag, String hex, int minimum, int maximum) {}

    private static final List<Fixture> FIXTURES = List.of(
            new Fixture(0x0005, "04", 1, 1),
            new Fixture(0x0006, "08", 1, 1),
            new Fixture(0x0007, "08", 1, 1),
            new Fixture(0x0008, "ff00", 2, 2),
            new Fixture(0x000d, "04", 1, 1),
            new Fixture(0x000e, "08", 1, 1),
            new Fixture(0x000f, "08", 1, 1),
            new Fixture(0x0010, "ff", 1, 1),
            new Fixture(0x0017, "ffffffff", 4, 4),
            new Fixture(0x0019, "01", 1, 1),
            new Fixture(0x001d, "4100", 1, 256),
            new Fixture(0x001e, "4100", 1, 65),
            new Fixture(0x0030, "83", 1, 1),
            new Fixture(0x0201, "03", 1, 1),
            new Fixture(0x0202, "a001", 2, 23),
            new Fixture(0x0203, "8001", 2, 23),
            new Fixture(0x0204, "ffff", 2, 2),
            new Fixture(0x0205, "ff", 1, 1),
            new Fixture(0x020a, "ffff", 2, 2),
            new Fixture(0x020b, "ffff", 2, 2),
            new Fixture(0x020c, "ffff", 2, 2),
            new Fixture(0x020d, "ff", 1, 1),
            new Fixture(0x020e, "ff", 1, 1),
            new Fixture(0x020f, "ff", 1, 1),
            new Fixture(0x0302, "0b", 1, 1),
            new Fixture(0x0303, "ff00ff", 1, 65),
            new Fixture(0x0304, "63", 1, 1),
            new Fixture(0x0381, "0101013132", 4, 19),
            new Fixture(0x0420, "01", 1, 1),
            new Fixture(0x0421, "01", 1, 1),
            new Fixture(0x0423, "03ffff", 3, 3),
            new Fixture(0x0424, "00ff41", 0, 65535),
            new Fixture(0x0425, "03", 1, 1),
            new Fixture(0x0426, "01", 1, 1),
            new Fixture(0x0427, "08", 1, 1),
            new Fixture(0x0428, "64", 1, 1),
            new Fixture(0x0501, "ff", 1, 1),
            new Fixture(0x060b, "80", 1, 1024),
            new Fixture(0x060d, "31303031303100", 7, 65),
            new Fixture(0x060e, "31303031303100", 7, 65),
            new Fixture(0x060f, "313233343536", 6, 6),
            new Fixture(0x0610, "313233343536", 6, 6),
            new Fixture(0x0611, "02", 1, 1),
            new Fixture(0x0612, "31323334353637383930", 10, 10),
            new Fixture(0x0613, "0000000001", 1, 5),
            new Fixture(0x1201, "02", 1, 1),
            new Fixture(0x1203, "ffff", 2, 2),
            new Fixture(0x1204, "03", 1, 1),
            new Fixture(0x130c, "", 0, 0),
            new Fixture(0x1380, "08", 1, 1),
            new Fixture(0x1383, "ffff", 2, 2));

    @Test
    void everyApplicableTagHasIndependentBytesAndMalformedLengthEvidence() {
        for (SmppVersion version : SmppVersion.values()) {
            Set<Integer> covered = new HashSet<>();
            for (Fixture f : FIXTURES) {
                if (!ProtocolProfile.forVersion(version).definesTag(f.tag())) continue;
                MessageTlvValueCodec codec = new MessageTlvValueCodec(version, f.tag());
                byte[] bytes = HexFormat.of().parseHex(f.hex());
                OctetString value = codec.decode(bytes).orElseThrow();
                assertArrayEquals(bytes, value.value());
                assertArrayEquals(bytes, codec.encode(value));
                byte[] input = bytes.clone();
                codec.decode(input);
                Arrays.fill(input, (byte) 7);
                assertArrayEquals(bytes, value.value());
                int minimum = f.minimum();
                int maximum = f.maximum();
                if (version == SmppVersion.V5_0 && f.tag() == 0x1204) maximum = 4;
                if (version == SmppVersion.V5_0 && f.tag() == 0x130c) maximum = 1;
                if (minimum > 0) {
                    byte[] shortValue = new byte[minimum - 1];
                    assertThrows(
                            FieldCodecException.class, () -> codec.decode(shortValue), Integer.toHexString(f.tag()));
                }
                byte[] longValue = new byte[maximum + 1];
                assertThrows(FieldCodecException.class, () -> codec.decode(longValue), Integer.toHexString(f.tag()));
                covered.add(f.tag());
            }
            Set<Integer> expected = new HashSet<>();
            for (long id : new long[] {4, 5, 0x103, 0x80000004L, 0x80000005L, 0x80000103L})
                for (MessageDirection direction : MessageDirection.values())
                    expected.addAll(MessageTlvRules.permittedTags(version, id, direction));
            assertEquals(expected, covered);
        }
    }

    @Test
    void reservedValuesRemainRawButHaveNoSupportedInterpretationAndCannotBeSent() {
        for (SmppVersion version : SmppVersion.values()) {
            for (String fixture : List.of(
                    "0005:05",
                    "0006:09",
                    "0019:02",
                    "0030:04",
                    "0201:04",
                    "0202:0101",
                    "020e:00",
                    "020f:00",
                    "0302:0c",
                    "0304:64",
                    "0381:02010131",
                    "0381:01070131",
                    "0381:01010231",
                    "0420:02",
                    "0421:02",
                    "0423:00ffff",
                    "0425:04",
                    "0426:02",
                    "0427:ff",
                    "0501:04",
                    "1201:03",
                    "1380:09")) {
                String[] parts = fixture.split(":");
                MessageTlvValueCodec codec = new MessageTlvValueCodec(version, Integer.parseInt(parts[0], 16));
                byte[] bytes = HexFormat.of().parseHex(parts[1]);
                assertTrue(codec.decode(bytes).isEmpty(), fixture);
                assertThrows(IllegalArgumentException.class, () -> codec.encode(new OctetString(bytes)), fixture);
            }
        }
        assertTrue(new MessageTlvValueCodec(SmppVersion.V3_4, 0x0427)
                .decode(new byte[] {0})
                .isEmpty());
        assertTrue(new MessageTlvValueCodec(SmppVersion.V5_0, 0x0427)
                .decode(new byte[] {0})
                .isPresent());
        assertTrue(new MessageTlvValueCodec(SmppVersion.V3_4, 0x0423)
                .decode(new byte[] {8, 0, 1})
                .isEmpty());
        assertTrue(new MessageTlvValueCodec(SmppVersion.V5_0, 0x0423)
                .decode(new byte[] {8, 0, 1})
                .isPresent());
    }

    @Test
    void compoundLayoutsValidateStringsDigitsAndVersionSpecificLengthAlternatives() {
        for (int tag : new int[] {0x001d, 0x001e, 0x060d, 0x060e}) {
            MessageTlvValueCodec codec = new MessageTlvValueCodec(SmppVersion.V5_0, tag);
            for (String hex : List.of("31323334353637", "31323300353600", "3132333435ff00"))
                assertThrows(
                        FieldCodecException.class,
                        () -> codec.decode(HexFormat.of().parseHex(hex)));
        }
        MessageTlvValueCodec callback = new MessageTlvValueCodec(SmppVersion.V5_0, 0x0381);
        assertTrue(callback.decode(HexFormat.of().parseHex("00010121f3")).isPresent());
        assertThrows(
                FieldCodecException.class, () -> callback.decode(HexFormat.of().parseHex("01010141")));
        assertThrows(
                FieldCodecException.class, () -> callback.decode(HexFormat.of().parseHex("000101f123")));
        for (int tag : new int[] {0x060f, 0x0610, 0x0612}) {
            byte[] value = new byte[tag == 0x0612 ? 10 : 6];
            Arrays.fill(value, (byte) '1');
            value[0] = 'X';
            assertThrows(
                    FieldCodecException.class, () -> new MessageTlvValueCodec(SmppVersion.V5_0, tag).decode(value));
        }
        MessageTlvValueCodec validity = new MessageTlvValueCodec(SmppVersion.V5_0, 0x1204);
        assertTrue(validity.decode(HexFormat.of().parseHex("0406ffff")).isPresent());
        assertThrows(FieldCodecException.class, () -> validity.decode(new byte[] {4}));
        assertThrows(FieldCodecException.class, () -> validity.decode(new byte[] {4, 0, 1}));
        assertTrue(validity.decode(new byte[] {4, 7, 0, 1}).isEmpty());
        assertTrue(new MessageTlvValueCodec(SmppVersion.V5_0, 0x130c)
                .decode(new byte[] {3})
                .isPresent());
        assertThrows(
                FieldCodecException.class,
                () -> new MessageTlvValueCodec(SmppVersion.V3_4, 0x130c).decode(new byte[] {0}));
        assertThrows(IllegalArgumentException.class, () -> new MessageTlvValueCodec(SmppVersion.V3_4, 0x0428));
    }
}
