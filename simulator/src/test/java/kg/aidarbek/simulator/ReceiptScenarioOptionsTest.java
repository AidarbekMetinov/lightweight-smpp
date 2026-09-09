package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Duration;
import kg.aidarbek.smpp.profile.SmppVersion;
import org.junit.jupiter.api.Test;

class ReceiptScenarioOptionsTest {
    @Test
    void parsesOnlyFiniteExplicitLoopbackConfiguration() {
        var options = ReceiptScenarioOptions.parse(new String[] {
            "server",
            "--version=5.0",
            "--port=0",
            "--count=7",
            "--window=2",
            "--reject-every=3",
            "--fault=duplicate",
            "--timeout=PT2S",
            "--duration=PT12S",
            "--drain=PT1S",
            "--report=run"
        });
        assertNotNull(options);
        assertEquals(true, options.server());
        assertEquals(SmppVersion.V5_0, options.version());
        assertEquals(0, options.port());
        assertEquals(7, options.count());
        assertEquals(2, options.window());
        assertEquals(3, options.rejectEvery());
        assertEquals(ReceiptScenarioOptions.Fault.DUPLICATE, options.fault());
        assertEquals(Duration.ofSeconds(2), options.timeout());
        assertEquals(Path.of("run"), options.report());
    }

    @Test
    void refusesUnboundedUnknownDuplicateAndContradictoryOptions() {
        for (String[] arguments : new String[][] {
            {},
            {"client", "--report=x"},
            {"server"},
            {"server", "--report=x", "--count=10001"},
            {"server", "--report=x", "--window=33"},
            {"server", "--report=x", "--window=0"},
            {"server", "--report=x", "--timeout=PT0S"},
            {"server", "--report=x", "--duration=PT121S"},
            {"server", "--report=x", "--timeout=PT8S", "--duration=PT2S"},
            {"server", "--report=x", "--version=3.3"},
            {"server", "--report=x", "--fault=random"},
            {"server", "--report=x", "--count=0"},
            {"server", "--report=x", "--count=2", "--count=3"},
            {"server", "--report=x", "--host=example.com"},
            {"server", "--report=x", "--reject-every=-1"},
            {"server", "--report=x", "--drain=PT31S"},
            {"client", "--port=2775", "--report=x", "--fault=missing"},
            {"server", "--report=x", "--reject-every=1", "--fault=duplicate"}
        }) assertThrows(IllegalArgumentException.class, () -> ReceiptScenarioOptions.parse(arguments));
    }
}
