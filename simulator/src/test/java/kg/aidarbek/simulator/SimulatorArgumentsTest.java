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
    @Test
    void rejectsAnArrivalRateRatioForFixedConcurrencyWithoutAnOfferedRate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SimulatorArguments.parse("client", REVISION, "--model=concurrency", "--minimum-rate-ratio=0.99"));
        assertNotNull(SimulatorArguments.parse("client", REVISION, "--model=concurrency"));
    }

    @org.junit.jupiter.api.Test
    void parsesFiniteLifecycleOptionsAndRejectsAmbiguousChurnOrServerReconnectOwnership() {
        var config = SimulatorArguments.parse(
                "client",
                "--revision=0123456789012345678901234567890123456789",
                "--keepalive-idle=PT1S",
                "--keepalive-timeout=PT0.2S",
                "--reconnect-attempts=2",
                "--reconnect-delay=PT0.01S");
        assertEquals(2, config.lifecycle().reconnectPolicy().orElseThrow().maximumAttempts());
        assertEquals(
                java.time.Duration.ofSeconds(1),
                config.lifecycle().create(true).keepalive().orElseThrow().idleInterval());
        assertThrows(
                IllegalArgumentException.class,
                () -> SimulatorArguments.parse(
                        "server", "--revision=0123456789012345678901234567890123456789", "--reconnect-attempts=2"));
        assertThrows(
                IllegalArgumentException.class,
                () -> SimulatorArguments.parse(
                        "client",
                        "--revision=0123456789012345678901234567890123456789",
                        "--reconnect-attempts=2",
                        "--churn-rate=1"));
    }

    private static final String REVISION = "--revision=0123456789012345678901234567890123456789";

    @Test
    void parsesExplicitBurstPacingChurnAndResourceCriteriaWithoutHiddenDefaults() {
        var config = SimulatorArguments.parse(
                "client",
                REVISION,
                "--rates=2,10,2",
                "--duration=PT5S",
                "--holds=PT2S,PT1S,PT2S",
                "--scenario=W-BURST",
                "--spin=PT0S",
                "--churn-rate=2",
                "--churn-count=4",
                "--consumer-delay=PT0.05S",
                "--sample=PT0.1S",
                "--verify-fault-mix=true",
                "--fault-tolerance=0.1",
                "--max-rss-mib=1024",
                "--max-heap-mib=256",
                "--max-fd-growth=2",
                "--max-thread-growth=2");
        assertEquals(
                List.of(Duration.ofSeconds(2), Duration.ofSeconds(1), Duration.ofSeconds(2)),
                config.load().holds());
        assertEquals("W-BURST", config.settings().scenario());
        assertEquals(Duration.ZERO, config.settings().spin());
        assertEquals(2, config.settings().churnRate());
        assertEquals(4, config.settings().churnCount());
        assertEquals(Duration.ofMillis(50), config.settings().consumerDelay());
        assertEquals(Duration.ofMillis(100), config.settings().sampleInterval());
        assertEquals(true, config.settings().verifyFaultMix());
        assertEquals(0.1, config.settings().faultTolerance());
        assertEquals(1_073_741_824, config.settings().maximumRssBytes());
        assertEquals(268_435_456, config.settings().maximumHeapBytes());
        assertEquals(2, config.settings().maximumDescriptorGrowth());
        assertEquals(2, config.settings().maximumThreadGrowth());
    }

    @Test
    void rejectsUnboundedPacingChurnSamplingAndCriteriaBeforeAllocatingResources() {
        for (String option : List.of(
                "--spin=PT0.002S",
                "--spin=PT-1S",
                "--churn-rate=1001",
                "--churn-count=1",
                "--consumer-delay=PT2H",
                "--sample=PT0.001S",
                "--sample=PT2M",
                "--fault-tolerance=NaN",
                "--fault-tolerance=1.1",
                "--max-rss-mib=-1",
                "--max-heap-mib=-1",
                "--max-fd-growth=-2",
                "--max-thread-growth=-2",
                "--scenario=bad name"))
            assertThrows(
                    IllegalArgumentException.class, () -> SimulatorArguments.parse("client", REVISION, option), option);
        assertThrows(
                IllegalArgumentException.class, () -> SimulatorArguments.parse("server", REVISION, "--churn-rate=1"));
        assertThrows(
                IllegalArgumentException.class,
                () -> SimulatorArguments.parse("client", REVISION, "--churn-rate=1", "--churn-count=1000001"));
        assertThrows(
                IllegalArgumentException.class, () -> SimulatorArguments.parse("client", REVISION, "--holds=PT9S"));
    }

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
