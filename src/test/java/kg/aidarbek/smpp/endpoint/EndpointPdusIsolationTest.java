package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.codec.PduLimits;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.ControlCommand;
import kg.aidarbek.smpp.protocol.DataSm;
import kg.aidarbek.smpp.protocol.DataSmResponse;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.Tlv;
import kg.aidarbek.smpp.session.EndpointRole;
import org.junit.jupiter.api.Test;

/** Concurrent known-wire characterization keeps each endpoint's limits, role and explicit profile independent. */
class EndpointPdusIsolationTest {
    @Test
    void concurrentInstancesKeepFrameBoundsTlvBoundsProfileRulesAndDirection() throws Exception {
        List<PduLimits> limits =
                List.of(new PduLimits(1024, 64, 4), new PduLimits(32, 16, 4), new PduLimits(1024, 0, 0));
        CountDownLatch ready = new CountDownLatch(12);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(12)) {
            List<Future<?>> results = new ArrayList<>();
            for (SmppVersion version : SmppVersion.values())
                for (EndpointRole role : EndpointRole.values())
                    for (PduLimits bound : limits)
                        results.add(workers.submit(() -> {
                            EndpointPdus pdus = new EndpointPdus(role, bound);
                            ready.countDown();
                            assertTrue(start.await(2, TimeUnit.SECONDS));
                            for (int repeat = 0; repeat < 30; repeat++) verify(pdus, role, version, bound);
                            return null;
                        }));
            try {
                assertTrue(ready.await(2, TimeUnit.SECONDS));
                start.countDown();
                for (Future<?> result : results) result.get(5, TimeUnit.SECONDS);
            } finally {
                start.countDown();
                workers.shutdownNow();
            }
        }
    }

    private static void verify(EndpointPdus pdus, EndpointRole role, SmppVersion version, PduLimits limits) {
        ProtocolProfile profile = ProtocolProfile.forVersion(version);
        long sequence = 0x01020304;
        assertArrayEquals(
                hex("00000010000000150000000001020304"),
                pdus.encode(new Pdu<>(0, sequence, EndpointPdus.control(ControlCommand.Type.ENQUIRE_LINK)), profile));
        DataSm data = data(new Tlv(0x0424, new byte[] {1, 2, 3}));
        byte[] expected = hex("0000002300000103000000000102030400000073000000640000000404240003010203");
        if (limits.maximumPduLength() == 32 || limits.maximumTlvLength() == 0) {
            assertThrows(IllegalArgumentException.class, () -> pdus.encode(new Pdu<>(0, sequence, data), profile));
            assertThrows(IllegalArgumentException.class, () -> pdus.decode(expected, profile));
        } else {
            byte[] first = pdus.encode(new Pdu<>(0, sequence, data), profile);
            assertArrayEquals(expected, first);
            assertEquals(data, pdus.decode(first, profile).command());
            first[first.length - 1] = 99;
            assertArrayEquals(expected, pdus.encode(new Pdu<>(0, sequence, data), profile));

            DataSm directional = data(new Tlv(0x0426, new byte[] {1}));
            if (version == SmppVersion.V5_0 && role == EndpointRole.MESSAGE_CENTER)
                assertThrows(
                        IllegalArgumentException.class,
                        () -> pdus.encode(new Pdu<>(0, sequence, directional), profile));
            else
                assertArrayEquals(
                        hex("000000210000010300000000010203040000007300000064000000040426000101"),
                        pdus.encode(new Pdu<>(0, sequence, directional), profile));
        }
        var error = new Pdu<>(
                8, sequence, new DataSmResponse(new MessageResponse(Optional.of("id"), EndpointPdus.NO_PARAMETERS)));
        if (version == SmppVersion.V5_0)
            assertThrows(IllegalArgumentException.class, () -> pdus.encode(error, profile));
        else assertArrayEquals(hex("00000013800001030000000801020304696400"), pdus.encode(error, profile));
    }

    private static DataSm data(Tlv parameter) {
        return new DataSm(
                "",
                new Address(0, 0, "s"),
                new Address(0, 0, "d"),
                0,
                0,
                4,
                new OptionalParameters(List.of(parameter)));
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }
}
