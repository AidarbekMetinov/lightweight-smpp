package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.endpoint.SmppClient;
import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SamplingProgressTest {
    @TempDir
    Path temporary;

    @Test
    void blockedResourceObservationLeavesTheOnlyVirtualCarrierAvailable() throws Exception {
        check("resource");
    }

    @Test
    void blockedPressureObservationLeavesTheOnlyVirtualCarrierAvailable() throws Exception {
        check("pressure");
    }

    private void check(String scenario) throws Exception {
        String classpath = String.join(
                File.pathSeparator,
                location(SamplingProgressProbe.class),
                location(SimulatorMain.class),
                location(SmppClient.class),
                location(Histogram.class));
        Path output = temporary.resolve(scenario + ".log");
        var command = List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx128m",
                "-Djdk.virtualThreadScheduler.parallelism=1",
                "-Djdk.virtualThreadScheduler.maxPoolSize=1",
                "-cp",
                classpath,
                SamplingProgressProbe.class.getName(),
                scenario,
                temporary.resolve(scenario).toString());
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(output.toFile())
                .start();
        try {
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Sampler probe did not finish within its bound");
            String transcript = Files.readString(output);
            assertEquals(0, process.exitValue(), transcript);
            assertTrue(transcript.contains("virtual releaser progressed; one platform sampler retired"), transcript);
        } finally {
            process.destroyForcibly();
            assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Sampler probe process was not reaped");
        }
    }

    private static String location(Class<?> type) throws Exception {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toString();
    }
}
