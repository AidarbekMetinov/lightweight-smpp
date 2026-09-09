package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import org.junit.jupiter.api.Test;

class SimulatorArgumentsTest {
    private static final String REVISION = "--revision=0123456789012345678901234567890123456789";

    @Test
    void parsesAReproducibleRampAndKeepsTheExactUnitsAndRoles() {
        SimulatorConfig config = SimulatorArguments.parse(
                "client",
                REVISION,
                "--rates=10,50,100,10",
                "--duration=PT4S",
                "--warmup=PT1S",
                "--connections=4",
                "--window=8",
                "--version=5.0",
                "--bind=trx",
                "--payload=4096",
                "--seed=-7",
                "--operation=data",
                "--run-id=test",
                "--report=build/example",
                "--reject=5",
                "--delay=10",
                "--stall=1",
                "--delay-duration=PT0.2S");
        assertNotNull(config, "Valid arguments must produce a usable immutable run configuration");
        assertEquals(SimulatorConfig.Mode.CLIENT, config.mode());
        assertEquals(SmppVersion.V5_0, config.version());
        assertEquals(BindMode.TRANSCEIVER, config.bindMode());
        assertEquals(List.of(10, 50, 100, 10), config.load().rates());
        assertEquals(Duration.ofSeconds(4), config.load().duration());
        assertEquals(4096, config.payloadBytes());
        assertEquals(-7, config.seed());
        assertEquals(5, config.faults().rejectPercent());
    }

    @Test
    void rejectsTyposDuplicatesSecretsAndUnboundedAllocationBeforeSideEffects() {
        for (String invalid : List.of(
                "--windwo=32",
                "--connections=0",
                "--window=0",
                "--payload=1048577",
                "--port=65536",
                "--bind=nope",
                "--version=3.3",
                "--password=secret",
                "--expect-failures=yes")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SimulatorArguments.parse("client", REVISION, invalid),
                    invalid);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> SimulatorArguments.parse("client", REVISION, "--port=1", "--port=2"));
        assertThrows(IllegalArgumentException.class, () -> SimulatorArguments.parse("unknown", REVISION));
        assertThrows(IllegalArgumentException.class, () -> SimulatorArguments.parse("client"));
    }

    @Test
    void supportsAnExplicitFixedConcurrencyPlanAndReceiverOnlyServer() {
        SimulatorConfig concurrent = SimulatorArguments.parse("client", REVISION, "--model=concurrency");
        assertNotNull(concurrent);
        assertEquals(LoadPlan.Model.FIXED_CONCURRENCY, concurrent.load().model());
        assertEquals(List.of(), concurrent.load().rates());
        SimulatorConfig server = SimulatorArguments.parse("server", REVISION, "--port=0");
        assertNotNull(server);
        assertEquals("none", server.operation());
        assertEquals(0, server.port());
    }
}
