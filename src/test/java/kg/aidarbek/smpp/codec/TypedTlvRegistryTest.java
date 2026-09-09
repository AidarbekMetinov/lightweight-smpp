package kg.aidarbek.smpp.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

class TypedTlvRegistryTest {
    @Test
    void standardInterpretationPreservesUnknownVersionAndLimitsCongestionToVersion5Responses() {
        TypedTlvRegistry registry = TypedTlvRegistry.standard();
        Tlv unknownVersion = new Tlv(0x0210, new byte[] {0x60});
        for (SmppVersion version : SmppVersion.values()) {
            for (long command : new long[] {0x80000001L, 0x80000002L, 0x80000009L}) {
                assertEquals(Optional.of(0x60), registry.decode(version, command, unknownVersion, Integer.class));
                assertEquals(
                        new Tlv(0x0210, new byte[] {0x50}),
                        registry.encode(version, command, 0x0210, 0x50, Integer.class));
            }
            assertEquals(Optional.empty(), registry.decode(version, 0x80000015L, unknownVersion, Integer.class));
        }
        Tlv congestion = new Tlv(0x0428, new byte[] {100});
        long[] responses = {
            0x80000000L,
            0x80000001L,
            0x80000002L,
            0x80000003L,
            0x80000004L,
            0x80000005L,
            0x80000006L,
            0x80000007L,
            0x80000008L,
            0x80000009L,
            0x80000015L,
            0x80000021L,
            0x80000103L,
            0x80000111L,
            0x80000112L,
            0x80000113L
        };
        for (long response : responses) {
            assertEquals(Optional.of(100), registry.decode(SmppVersion.V5_0, response, congestion, Integer.class));
            assertEquals(Optional.empty(), registry.decode(SmppVersion.V3_4, response, congestion, Integer.class));
            assertEquals(
                    Optional.empty(),
                    registry.decode(SmppVersion.V5_0, response & 0x7fffffffL, congestion, Integer.class));
        }
        Tlv reserved = new Tlv(0x0428, new byte[] {(byte) 0xff});
        assertEquals(Optional.empty(), registry.decode(SmppVersion.V5_0, 0x80000015L, reserved, Integer.class));
        assertEquals(new Tlv(0x0428, new byte[] {(byte) 0xff}), reserved);
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.encode(SmppVersion.V5_0, 0x80000015L, 0x0428, 101, Integer.class));
        assertThrows(
                FieldCodecException.class,
                () -> registry.decode(
                        SmppVersion.V5_0, 0x80000015L, new Tlv(0x0428, new byte[] {0, 0}), Integer.class));
        assertEquals(
                Optional.empty(),
                registry.decode(SmppVersion.V3_4, 0x80000015L, new Tlv(0x0428, new byte[] {0, 0}), Integer.class));
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.with(new TypedTlvRegistry.Registration<>(
                        SmppVersion.V5_0, 0x80000015L, 0x0428, new UnsignedByteTlvCodec(255))));
        Tlv encoded = registry.encode(SmppVersion.V5_0, 0x80000015L, 0x0428, 100, Integer.class);
        assertThrows(
                IllegalArgumentException.class, () -> TlvCodec.encode(new OptionalParameters(List.of(encoded)), 4, 1));
    }

    @Test
    void rejectsInvalidRegistrationContextsAndTagWidths() {
        TlvValueCodec<Integer> codec = new UnsignedByteTlvCodec(255);
        assertThrows(
                IllegalArgumentException.class,
                () -> new TypedTlvRegistry.Registration<>(SmppVersion.V3_4, 0x111, 0x1400, codec));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TypedTlvRegistry.Registration<>(SmppVersion.V5_0, -1, 0x1400, codec));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TypedTlvRegistry.Registration<>(SmppVersion.V5_0, 0x1_0000_0004L, 0x1400, codec));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TypedTlvRegistry.Registration<>(SmppVersion.V3_4, 4, -1, codec));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TypedTlvRegistry.Registration<>(SmppVersion.V3_4, 4, 65536, codec));
        assertThrows(NullPointerException.class, () -> new TypedTlvRegistry.Registration<>(null, 4, 0x1400, codec));
        assertThrows(
                NullPointerException.class,
                () -> new TypedTlvRegistry.Registration<>(SmppVersion.V3_4, 4, 0x1400, null));
        assertThrows(NullPointerException.class, () -> new TypedTlvRegistry(null));
    }

    @Test
    void rejectsAmbiguousRegistrationAndExtendsWithoutMutatingExistingRegistry() {
        TypedTlvRegistry.Registration<Integer> first =
                new TypedTlvRegistry.Registration<>(SmppVersion.V3_4, 4, 0x1400, new UnsignedByteTlvCodec(255));
        TypedTlvRegistry.Registration<Integer> ambiguous =
                new TypedTlvRegistry.Registration<>(SmppVersion.V3_4, 4, 0x1400, new UnsignedByteTlvCodec(100));
        assertThrows(IllegalArgumentException.class, () -> new TypedTlvRegistry(List.of(first, ambiguous)));
        assertThrows(IllegalArgumentException.class, () -> new TypedTlvRegistry(List.of(first, first)));
        TypedTlvRegistry original = new TypedTlvRegistry(List.of(first));
        assertThrows(IllegalArgumentException.class, () -> original.with(ambiguous));
        TypedTlvRegistry extended = original.with(
                new TypedTlvRegistry.Registration<>(SmppVersion.V5_0, 4, 0x1400, new UnsignedByteTlvCodec(100)));
        Tlv raw = new Tlv(0x1400, new byte[] {100});
        assertEquals(Optional.empty(), original.decode(SmppVersion.V5_0, 4, raw, Integer.class));
        assertEquals(Optional.of(100), extended.decode(SmppVersion.V5_0, 4, raw, Integer.class));
        TypedTlvRegistry otherCommand = extended.with(
                new TypedTlvRegistry.Registration<>(SmppVersion.V3_4, 5, 0x1400, new UnsignedByteTlvCodec(255)));
        assertEquals(Optional.of(100), otherCommand.decode(SmppVersion.V3_4, 5, raw, Integer.class));
    }

    @Test
    void appliesRegisteredInterpretationOnlyToItsVersionCommandAndTag() {
        List<TypedTlvRegistry.Registration<?>> registrations = new ArrayList<>();
        registrations.add(
                new TypedTlvRegistry.Registration<>(SmppVersion.V3_4, 4, 0x1400, new UnsignedByteTlvCodec(255)));
        TypedTlvRegistry registry = new TypedTlvRegistry(registrations);
        registrations.clear();
        Tlv raw = new Tlv(0x1400, new byte[] {(byte) 0xff});
        assertEquals(Optional.of(255), registry.decode(SmppVersion.V3_4, 4, raw, Integer.class));
        assertEquals(raw, registry.encode(SmppVersion.V3_4, 4, 0x1400, 255, Integer.class));
        assertEquals(Optional.empty(), registry.decode(SmppVersion.V5_0, 4, raw, Integer.class));
        assertEquals(Optional.empty(), registry.decode(SmppVersion.V3_4, 5, raw, Integer.class));
        assertEquals(
                Optional.empty(), registry.decode(SmppVersion.V3_4, 4, new Tlv(0x1401, new byte[0]), Integer.class));
        assertThrows(
                IllegalArgumentException.class, () -> registry.encode(SmppVersion.V5_0, 4, 0x1400, 255, Integer.class));
        assertThrows(IllegalArgumentException.class, () -> registry.decode(SmppVersion.V3_4, 4, raw, String.class));
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.encode(SmppVersion.V3_4, 4, 0x1400, "255", String.class));
    }
}
