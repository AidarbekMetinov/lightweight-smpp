package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SimulatorProcessCleanupTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"receipt", "traffic"})
    void interruptedFirstCleanupStillReapsBothActualChildren(String fixture) throws Exception {
        Path clientReady = directory.resolve("client.ready");
        Path serverReady = directory.resolve("server.ready");
        Process client = waitingChild(clientReady);
        Process server = null;
        try {
            server = waitingChild(serverReady);
            awaitReady(client, clientReady);
            awaitReady(server, serverReady);
            Thread.currentThread().interrupt();
            try {
                if (fixture.equals("receipt")) ReceiptScenarioProcessTest.stopBoth(client, server);
                else SimulatorProcessTest.stopBoth(client, server);
            } catch (InterruptedException expected) {
                // Interruption is propagated only after both independent cleanup owners are attempted.
            } finally {
                Thread.interrupted();
            }
            assertTrue(client.waitFor(5, TimeUnit.SECONDS), "The first child must stop");
            assertTrue(server.waitFor(1, TimeUnit.SECONDS), "The second child was skipped after interruption");
        } finally {
            Thread.interrupted();
            client.destroyForcibly();
            if (server != null) server.destroyForcibly();
            try {
                assertTrue(client.waitFor(5, TimeUnit.SECONDS));
            } finally {
                if (server != null) assertTrue(server.waitFor(5, TimeUnit.SECONDS));
            }
        }
    }

    private static Process waitingChild(Path ready) throws Exception {
        String classpath = Path.of(HelperSimulatorTest.WaitingChild.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI())
                .toString();
        return new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-Xms16m",
                        "-Xmx32m",
                        "-cp",
                        classpath,
                        HelperSimulatorTest.WaitingChild.class.getName(),
                        ready.toString())
                .redirectErrorStream(true)
                .redirectOutput(
                        ready.resolveSibling(ready.getFileName() + ".log").toFile())
                .start();
    }

    private static void awaitReady(Process child, Path ready) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!Files.exists(ready) && child.isAlive() && deadline - System.nanoTime() > 0) Thread.sleep(1);
        assertTrue(Files.exists(ready), "The finite child must signal readiness");
        assertTrue(child.isAlive());
    }
}
