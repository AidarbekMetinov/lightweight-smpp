package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(15)
class LoadRunTest {
    @TempDir
    Path directory;

    @Test
    void recordsIdleBaselinesAndStopsTheSamplingOwnerForBothEndpointRoles() throws Exception {
        run(false);
    }

    @Test
    void composesFiniteChurnWithDynamicSessionsAndRetainsItsLifecycleReport() throws Exception {
        run(true);
    }

    private void run(boolean churn) throws Exception {
        String revision = "--revision=0123456789012345678901234567890123456789";
        var serverConfig = SimulatorArguments.parse(
                "server",
                revision,
                "--port=0",
                "--duration=PT0.5S",
                "--drain=PT0.2S",
                "--payload=16",
                "--sample=PT0.01S",
                "--report=" + directory.resolve("server"));
        var ready = new CompletableFuture<Integer>();
        var finished = new CompletableFuture<Integer>();
        Thread server = SimulatorTestOwner.start(() -> {
            try {
                finished.complete(SimulatorRun.execute(serverConfig, "sim", "sim", event -> {
                    if (event.startsWith("READY port=")) ready.complete(Integer.parseInt(event.substring(11)));
                }));
            } catch (Exception failure) {
                ready.completeExceptionally(failure);
                finished.completeExceptionally(failure);
            }
        });
        try {
            int port = ready.get(5, TimeUnit.SECONDS);
            var clientConfig = SimulatorArguments.parse(
                    "client",
                    revision,
                    "--port=" + port,
                    "--warmup=PT0.1S",
                    "--duration=PT0.2S",
                    "--drain=PT0.1S",
                    "--rates=10",
                    "--count=2",
                    "--payload=16",
                    "--sample=PT0.01S",
                    "--churn-rate=" + (churn ? 10 : 0),
                    "--churn-count=" + (churn ? 1 : 0),
                    "--expect-failures=" + churn,
                    "--report=" + directory.resolve("client"));
            assertEquals(0, SimulatorRun.execute(clientConfig, "sim", "sim", event -> {}));
            assertEquals(0, finished.get(5, TimeUnit.SECONDS));
            for (String role : new String[] {"client", "server"}) {
                String report = Files.readString(directory.resolve(role).resolve("report.json"));
                assertTrue(report.contains("\"baselineRecorded\":true"), report);
                assertTrue(report.contains("\"samplingTerminated\":true"), report);
                assertTrue(report.contains("\"cleanupComplete\":true"), report);
                assertTrue(report.contains("\"pressure\":{\"baseline\":"), report);
                assertTrue(Files.readAllLines(directory.resolve(role).resolve("pressure.csv"))
                                .size()
                        > 1);
                if (churn) {
                    assertTrue(report.contains("\"replacementBound\":1"), report);
                    assertTrue(report.contains("\"retainedStreams\":0"), report);
                }
            }
        } finally {
            if (server.isAlive()) server.interrupt();
            server.join(Duration.ofSeconds(5));
            assertFalse(server.isAlive());
        }
    }
}
