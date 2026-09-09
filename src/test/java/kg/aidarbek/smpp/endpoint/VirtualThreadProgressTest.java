package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VirtualThreadProgressTest {
    @TempDir
    Path temporary;

    @ParameterizedTest
    @ValueSource(
            strings = {
                "responses",
                "admission",
                "reconnect",
                "server-start",
                "server-shutdown",
                "server-close",
                "outbind-start",
                "outbind-shutdown",
                "outbind-close"
            })
    void protocolWorkReleasesCarriersWhileWaitingForNotificationOwnership(String scenario) throws Exception {
        Path output = temporary.resolve(scenario + "-progress.log");
        String classpath = Path.of(VirtualThreadProgressProbe.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI())
                + File.pathSeparator
                + Path.of(EndpointConnection.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI());
        var command = List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djdk.virtualThreadScheduler.parallelism=2",
                "-Djdk.virtualThreadScheduler.maxPoolSize=2",
                "-cp",
                classpath,
                VirtualThreadProgressProbe.class.getName(),
                scenario);
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Fresh two-carrier probe did not terminate");
            assertEquals(0, process.exitValue(), () -> read(output));
            assertTrue(read(output).contains(scenario + " completed and all notifications retired"), read(output));
        } finally {
            process.destroyForcibly();
            assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Probe process was not reaped");
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }
}
