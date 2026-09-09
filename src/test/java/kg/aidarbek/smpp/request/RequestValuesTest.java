package kg.aidarbek.smpp.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import kg.aidarbek.smpp.protocol.ControlCommand;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

final class RequestValuesTest {
    @Test
    void identityAndTimeoutValuesRetainImmutableEqualityAndBoundaryContracts() {
        UUID generation = UUID.randomUUID();
        var identity = new RequestIdentity(generation, 0x7fffffffL);
        assertEquals(identity, new RequestIdentity(generation, 0x7fffffffL));
        assertEquals(identity.hashCode(), new RequestIdentity(generation, 0x7fffffffL).hashCode());
        assertThrows(IllegalArgumentException.class, () -> new RequestIdentity(generation, 0));
        assertThrows(IllegalArgumentException.class, () -> new RequestIdentity(generation, 0x80000000L));
        assertThrows(NullPointerException.class, () -> new RequestIdentity(null, 1));
        assertEquals(
                new RequestOptions(Duration.ofNanos(Long.MAX_VALUE)),
                RequestOptions.timeout(Duration.ofNanos(Long.MAX_VALUE)));
    }

    @Test
    void localFailuresPreserveStructuredFieldsAndThrowableSerialization() throws Exception {
        UUID generation = UUID.randomUUID();
        var original = new RequestFailure(
                RequestFailure.Reason.WRITE_FAILED,
                generation,
                7,
                0x15,
                TransmissionCertainty.MAY_HAVE_BEEN_SENT,
                new IllegalStateException("transport"));
        var restored = roundTrip(original, RequestFailure.class);
        assertEquals(original.reason(), restored.reason());
        assertEquals(original.generation(), restored.generation());
        assertEquals(original.sequenceNumber(), restored.sequenceNumber());
        assertEquals(original.requestCommandId(), restored.requestCommandId());
        assertEquals(original.transmission(), restored.transmission());
        assertEquals(original.requestIdentity(), restored.requestIdentity());
        assertEquals("transport", restored.getCause().getMessage());
    }

    @Test
    void peerNacksPreserveOrderedBinaryDataAfterSerializationAndDefensiveAccess() throws Exception {
        var identity = new RequestIdentity(UUID.randomUUID(), 9);
        byte[] source = {1, 2};
        var parameters = new OptionalParameters(List.of(new Tlv(0x1400, source), new Tlv(0x1400, new byte[] {3})));
        var nack = new Pdu<>(0xfedcba98L, 9, new ControlCommand(ControlCommand.Type.GENERIC_NACK, parameters));
        var original = new PeerNackException(identity, nack);
        source[0] = 9;
        original.nack().command().optionalParameters().entries().getFirst().value()[0] = 8;
        assertEquals(nack, original.nack());
        var restored = roundTrip(original, PeerNackException.class);
        assertEquals(identity, restored.requestIdentity());
        assertEquals(nack, restored.nack());
        assertTrue(restored.getMessage().contains("fedcba98"));
    }

    private static <T> T roundTrip(T value, Class<T> type) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return type.cast(input.readObject());
        }
    }
}
