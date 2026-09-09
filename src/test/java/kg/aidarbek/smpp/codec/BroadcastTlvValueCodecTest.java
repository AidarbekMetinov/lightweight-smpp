package kg.aidarbek.smpp.codec;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import kg.aidarbek.smpp.protocol.OctetString;
import org.junit.jupiter.api.Test;

class BroadcastTlvValueCodecTest {
    private record Fixture(int tag, String hex, int minimum, int maximum) {}

    private static final List<Fixture> FIXTURES = List.of(
            new Fixture(0x0600, "01", 1, 1), new Fixture(0x0601, "000010", 3, 3),
            new Fixture(0x0602, "00ff41", 1, 255), new Fixture(0x0603, "02", 1, 1),
            new Fixture(0x0604, "ffff", 2, 2), new Fixture(0x0605, "09001f", 3, 3),
            new Fixture(0x0606, "004e5900ff", 1, 101), new Fixture(0x0607, "00000144", 4, 4),
            new Fixture(0x0608, "ff", 1, 1), new Fixture(0x0609, "3236303930393132333435363030302b00", 17, 17),
            new Fixture(0x060a, "ff0041", 1, 255), new Fixture(0x0427, "09", 1, 1));

    @Test
    void everyBroadcastTagHasIndependentValueBytesBoundsAndOwnership() {
        for (Fixture fixture : FIXTURES) {
            BroadcastTlvValueCodec codec = new BroadcastTlvValueCodec(fixture.tag());
            byte[] expected = HexFormat.of().parseHex(fixture.hex());
            byte[] input = expected.clone();
            OctetString value = codec.decode(input).orElseThrow();
            Arrays.fill(input, (byte) 42);
            assertArrayEquals(expected, value.value());
            byte[] encoded = codec.encode(value);
            Arrays.fill(encoded, (byte) 7);
            assertArrayEquals(expected, codec.encode(value));
            assertThrows(
                    FieldCodecException.class,
                    () -> codec.decode(new byte[fixture.minimum() - 1]),
                    Integer.toHexString(fixture.tag()));
            assertThrows(
                    FieldCodecException.class,
                    () -> codec.decode(new byte[fixture.maximum() + 1]),
                    Integer.toHexString(fixture.tag()));
        }
        assertThrows(IllegalArgumentException.class, () -> new BroadcastTlvValueCodec(0x060b));
        for (int tag : new int[] {0x0602, 0x0606, 0x060a}) {
            int length = tag == 0x0606 ? 101 : 255;
            assertTrue(new BroadcastTlvValueCodec(tag).decode(new byte[length]).isPresent());
        }
    }

    @Test
    void reservedDiscriminantsHaveNoInterpretationAndCannotBeEncoded() {
        int[] tags = {0x0600, 0x0601, 0x0601, 0x0603, 0x0605, 0x0606, 0x0608, 0x0427, 0x0427};
        String[] reserved = {"02", "040000", "000003", "01", "010001", "03", "65", "02", "0a"};
        for (int i = 0; i < tags.length; i++) {
            BroadcastTlvValueCodec codec = new BroadcastTlvValueCodec(tags[i]);
            byte[] value = HexFormat.of().parseHex(reserved[i]);
            assertTrue(codec.decode(value).isEmpty(), Integer.toHexString(tags[i]));
            assertThrows(IllegalArgumentException.class, () -> codec.encode(new OctetString(value)));
        }
        for (String value : List.of("000000", "000100", "03ffff"))
            assertTrue(new BroadcastTlvValueCodec(0x0601)
                    .decode(HexFormat.of().parseHex(value))
                    .isPresent());
        for (int unit : new int[] {0, 8, 9, 10, 11, 12, 13, 14})
            assertTrue(new BroadcastTlvValueCodec(0x0605)
                    .decode(new byte[] {(byte) unit, (byte) 255, (byte) 255})
                    .isPresent());
        for (int state : new int[] {0, 1, 3, 4, 5, 6, 7, 8, 9})
            assertTrue(new BroadcastTlvValueCodec(0x0427)
                    .decode(new byte[] {(byte) state})
                    .isPresent());
    }

    @Test
    void endTimeFollowsReferencedAbsoluteSeventeenOctetGrammarWithoutCalendarInference() {
        BroadcastTlvValueCodec codec = new BroadcastTlvValueCodec(0x0609);
        for (String malformed : List.of(
                "260909123456000+",
                "260909123456000+X",
                "260909123456000R\0",
                "260009123456000+\0",
                "260909123456049+\0"))
            assertThrows(
                    IllegalArgumentException.class, () -> codec.decode(malformed.getBytes(StandardCharsets.US_ASCII)));
        assertTrue(codec.decode("260909123456048-\0".getBytes(StandardCharsets.US_ASCII))
                .isPresent());
    }
}
