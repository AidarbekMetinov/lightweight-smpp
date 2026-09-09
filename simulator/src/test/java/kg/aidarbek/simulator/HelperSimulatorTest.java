package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.endpoint.SmppClient;
import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(45)
class HelperSimulatorTest {
    @TempDir
    Path directory;

    @Test
    void interruptedFirstCleanupStillStopsBothRealChildren() throws Exception {
        Path clientReady = directory.resolve("client.ready");
        Path serverReady = directory.resolve("server.ready");
        Process client = waitingChild(clientReady);
        Process server = null;
        try {
            server = waitingChild(serverReady);
            awaitReady(client, clientReady);
            awaitReady(server, serverReady);
            boolean interrupted = false;
            Thread.currentThread().interrupt();
            try {
                stopBoth(client, server);
            } catch (InterruptedException expected) {
                interrupted = true;
            } finally {
                Thread.interrupted();
            }
            assertTrue(client.waitFor(5, TimeUnit.SECONDS), "The first child must stop");
            assertTrue(
                    server.waitFor(1, TimeUnit.SECONDS),
                    "The second child must stop even when the first cleanup was interrupted: " + interrupted);
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

    @Test
    void explicitTextContentIsValidatedAndReportedUnderBothProfiles() throws Exception {
        for (String version : List.of("3.4", "5.0")) {
            pair("gsm7-" + version, version, "gsm7", 32, "submit", 1);
            pair("ucs2-" + version, version, "ucs2", 32, "data", 1);
        }
    }

    @Test
    void receiptTextFlexibleAndTlvVariantsUseDeliverySemantics() throws Exception {
        for (String version : List.of("3.4", "5.0")) {
            pair("receipt-" + version, version, "receipt", 8, "deliver", 1);
            pair("flexible-" + version, version, "receipt-flexible", 100, "data", 1);
            pair("tlv-" + version, version, "receipt-tlv", 0, "deliver", 1);
        }
    }

    @Test
    void eachOfTwoConnectionsReceivesBothPartsOfTheSarFixture() throws Exception {
        for (String version : List.of("3.4", "5.0")) {
            pair("sar-" + version, version, "sar", 161, "submit", 2);
        }
    }

    @Test
    void invalidHelperSelectionFailsBeforeCreatingReportsOrEndpointResources() throws Exception {
        for (List<String> invalid : List.of(
                List.of("--content=unknown"),
                List.of("--content=ucs2", "--payload=3"),
                List.of("--content=receipt", "--payload=8", "--operation=submit"),
                List.of("--content=receipt-tlv", "--payload=1", "--operation=data"),
                List.of("--content=receipt-flexible", "--payload=100", "--operation=data"))) {
            Path report = directory.resolve("invalid-" + Math.abs(invalid.hashCode()));
            var arguments = new ArrayList<>(List.of("client", "--report=" + report));
            arguments.addAll(invalid);
            Path log = directory.resolve("invalid.log");
            Process process = start(log, arguments);
            try {
                assertTrue(process.waitFor(8, TimeUnit.SECONDS));
                assertEquals(2, process.exitValue(), Files.readString(log));
                assertFalse(Files.exists(report));
            } finally {
                stop(process);
            }
        }
    }

    private void pair(String name, String version, String content, int payload, String operation, int connections)
            throws Exception {
        Path serverReport = directory.resolve(name + "-server");
        Path clientReport = directory.resolve(name + "-client");
        Path serverLog = directory.resolve(name + "-server.log");
        Path clientLog = directory.resolve(name + "-client.log");
        boolean serverOriginates = operation.equals("deliver") || content.startsWith("receipt");
        var shared = List.of(
                "--version=" + version,
                "--content=" + content,
                "--payload=" + payload,
                "--connections=" + connections,
                "--duration=PT1S",
                "--drain=PT0.5S",
                "--count=4",
                "--rates=4");
        var serverArguments = new ArrayList<>(List.of(
                "server",
                "--port=0",
                "--report=" + serverReport,
                "--operation=" + (serverOriginates ? operation : "none")));
        serverArguments.addAll(shared);
        Process server = start(serverLog, serverArguments);
        Process client = null;
        try {
            long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
            String output = "";
            while (deadline - System.nanoTime() > 0) {
                output = Files.readString(serverLog);
                if (output.contains("READY port=") || !server.isAlive()) break;
                Thread.sleep(10);
            }
            assertTrue(output.contains("READY port="), output);
            String port = output.lines()
                    .filter(line -> line.startsWith("READY port="))
                    .findFirst()
                    .orElseThrow()
                    .substring(11);
            var clientArguments = new ArrayList<>(List.of(
                    "client",
                    "--port=" + port,
                    "--report=" + clientReport,
                    "--operation=" + (serverOriginates ? "none" : operation)));
            clientArguments.addAll(shared);
            client = start(clientLog, clientArguments);
            assertTrue(client.waitFor(8, TimeUnit.SECONDS));
            assertEquals(0, client.exitValue(), Files.readString(clientLog));
            assertTrue(server.waitFor(8, TimeUnit.SECONDS));
            String serverJson = Files.readString(serverReport.resolve("report.json"));
            String clientJson = Files.readString(clientReport.resolve("report.json"));
            assertEquals(0, server.exitValue(), serverJson);
            String origin = serverOriginates ? serverJson : clientJson;
            String receiver = serverOriginates ? clientJson : serverJson;
            assertTrue(origin.contains("\"SUCCESS\":4"), origin);
            assertTrue(receiver.contains("\"received\":4"), receiver);
            for (String report : List.of(serverJson, clientJson)) {
                assertTrue(report.contains("\"content\":\"" + content + "\""), report);
                assertTrue(report.contains("\"invalidContent\":0"), report);
                assertTrue(report.contains("\"incompleteAssemblies\":0"), report);
                assertTrue(report.contains("\"cleanupComplete\":true"), report);
                assertTrue(report.contains("\"passed\":true"), report);
            }
        } finally {
            stopBoth(client, server);
        }
    }

    private static Process waitingChild(Path ready) throws Exception {
        String classpath = Path.of(WaitingChild.class
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
                        WaitingChild.class.getName(),
                        ready.toString())
                .redirectErrorStream(true)
                .redirectOutput(
                        ready.resolveSibling(ready.getFileName() + ".log").toFile())
                .start();
    }

    private static void awaitReady(Process child, Path ready) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!Files.exists(ready) && child.isAlive() && deadline - System.nanoTime() > 0) {
            Thread.sleep(1);
        }
        assertTrue(Files.exists(ready), "The finite child must signal readiness");
        assertTrue(child.isAlive());
    }

    private static Process start(Path log, List<String> arguments) throws Exception {
        var locations = List.of(SimulatorMain.class, SmppClient.class, Histogram.class).stream()
                .map(type -> Path.of(java.net.URI.create(type.getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .toExternalForm()))
                        .toString())
                .distinct()
                .toList();
        var command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xms64m",
                "-Xmx256m",
                "-cp",
                String.join(java.io.File.pathSeparator, locations),
                SimulatorMain.class.getName()));
        command.addAll(arguments);
        command.add("--revision=e79b9f48d7f2c94aad96b2bb734abe338d420a15");
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("SMPP_SYSTEM_ID", "sim");
        builder.environment().put("SMPP_PASSWORD", "sim");
        return builder.start();
    }

    private static void stop(Process process) throws Exception {
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            assertTrue(process.waitFor(5, TimeUnit.SECONDS));
        }
    }

    private static void stopBoth(Process client, Process server) throws Exception {
        try {
            stop(client);
        } finally {
            stop(server);
        }
    }

    public static final class WaitingChild {
        private WaitingChild() {}

        public static void main(String[] arguments) throws Exception {
            Files.writeString(Path.of(arguments[0]), "ready");
            Thread.sleep(Duration.ofSeconds(30));
        }
    }
}
