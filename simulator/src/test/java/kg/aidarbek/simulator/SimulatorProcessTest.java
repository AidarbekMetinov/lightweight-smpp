package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

@Timeout(35)
class SimulatorProcessTest {
    @TempDir
    Path directory;

    @Test
    void bidirectionalPayloadAndReceiverFaultProcessesRemainAccountable() throws Exception {
        var bidirectional = pair(
                "duplex",
                List.of("--operation=data", "--payload=4096", "--count=10", "--rates=10"),
                List.of("--operation=data", "--payload=4096", "--count=10", "--rates=10"));
        assertTrue(bidirectional.client().contains("\"SUCCESS\":10"), bidirectional.client());
        assertTrue(bidirectional.server().contains("\"SUCCESS\":10"), bidirectional.server());
        var rejected = pair("rejected", List.of("--reject=100"), List.of("--expect-failures=true"));
        assertTrue(rejected.client().contains("\"PEER_NEGATIVE\":10"), rejected.client());
        assertTrue(rejected.server().contains("\"rejected\":10"), rejected.server());
        var stalled = pair("stalled", List.of("--stall=100"), List.of("--timeout=PT0.05S", "--expect-failures=true"));
        assertTrue(stalled.client().contains("\"TIMEOUT\":10"), stalled.client());
        assertTrue(stalled.server().contains("\"stalled\":10"), stalled.server());
        var delayed = pair("delayed", List.of("--delay=100", "--delay-duration=PT0.2S"), List.of());
        assertTrue(delayed.client().contains("\"SUCCESS\":10"), delayed.client());
        var disconnected = pair("disconnected", List.of("--disconnect-after=3"), List.of("--expect-failures=true"));
        assertTrue(disconnected.server().contains("\"disconnected\":1"), disconnected.server());
        assertTrue(disconnected.client().contains("\"LOCAL_FAILURE\":1"), disconnected.client());
    }

    @Test
    void commonOperationsRunThroughTheStandaloneRegistryUnderBothProfiles() throws Exception {
        for (String version : List.of("3.4", "5.0")) {
            for (String operation : List.of("query", "cancel", "replace", "multi")) {
                var result = pair(
                        operation + "-" + version,
                        version,
                        List.of("--bind=tx"),
                        List.of("--operation=" + operation, "--bind=tx"));
                assertTrue(result.client().contains("\"SUCCESS\":10"), result.client());
                assertTrue(result.server().contains("\"received\":10"), result.server());
            }
        }
    }

    @Test
    void broadcastOperationsRunThroughTheStandaloneRegistry() throws Exception {
        for (String operation : List.of("broadcast", "query-broadcast", "cancel-broadcast")) {
            var result = pair(
                    operation,
                    "5.0",
                    List.of("--bind=tx", "--payload=4096"),
                    List.of("--operation=" + operation, "--bind=tx", "--payload=4096"));
            assertTrue(result.client().contains("\"SUCCESS\":10"), result.client());
            assertTrue(result.server().contains("\"received\":10"), result.server());
        }
        var rejected = pair(
                "broadcast-rejected",
                "5.0",
                List.of("--reject=100"),
                List.of("--operation=broadcast", "--expect-failures=true"));
        assertTrue(rejected.client().contains("\"PEER_NEGATIVE\":10"), rejected.client());
        assertTrue(rejected.server().contains("\"rejected\":10"), rejected.server());
    }

    @Test
    void configurationErrorsExitBeforeCreatingReportFiles() throws Exception {
        for (String invalid : List.of("--unknown=1", "--operation=deliver", "--window=0", "--model=unbounded")) {
            Path report = directory.resolve("invalid-" + Math.abs(invalid.hashCode()));
            Process process = start(directory.resolve("invalid.log"), "client", invalid, "--report=" + report);
            try {
                assertTrue(process.waitFor(5, TimeUnit.SECONDS));
                assertEquals(2, process.exitValue());
                assertFalse(Files.exists(report));
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                }
            }
        }
    }

    @Test
    void independentProcessesExchangeAndWriteFreshReportsForBothProfiles() throws Exception {
        for (String version : List.of("3.4", "5.0")) {
            Path serverDirectory = directory.resolve("server-" + version);
            Path serverLog = directory.resolve("server-" + version + ".log");
            var server = start(
                    serverLog,
                    "server",
                    "--port=0",
                    "--version=" + version,
                    "--report=" + serverDirectory,
                    "--duration=PT1S",
                    "--drain=PT1S",
                    "--payload=16");
            Process client = null;
            try {
                long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
                String ready = "";
                while (System.nanoTime() - deadline < 0) {
                    ready = Files.readString(serverLog);
                    if (ready.contains("READY port=")) break;
                    if (!server.isAlive()) break;
                    Thread.sleep(10);
                }
                assertTrue(ready.contains("READY port="), ready);
                String port = ready.lines()
                        .filter(line -> line.startsWith("READY port="))
                        .findFirst()
                        .orElseThrow()
                        .substring(11);
                Path clientDirectory = directory.resolve("client-" + version);
                Path clientLog = directory.resolve("client-" + version + ".log");
                client = start(
                        clientLog,
                        "client",
                        "--port=" + port,
                        "--version=" + version,
                        "--report=" + clientDirectory,
                        "--duration=PT1S",
                        "--drain=PT0.1S",
                        "--rates=10",
                        "--count=10",
                        "--payload=16");
                assertTrue(client.waitFor(10, TimeUnit.SECONDS));
                assertEquals(0, client.exitValue(), Files.readString(clientLog));
                assertTrue(server.waitFor(10, TimeUnit.SECONDS));
                assertEquals(0, server.exitValue(), Files.readString(serverLog));
                String report = Files.readString(clientDirectory.resolve("report.json"));
                assertTrue(report.contains("\"SUCCESS\":10"), report);
                assertTrue(report.contains("\"cleanupComplete\":true"), report);
                assertTrue(report.contains("\"passed\":true"), report);
                assertFalse(report.contains("testpwd"));
                assertTrue(
                        Files.readString(serverDirectory.resolve("report.json")).contains("\"received\":10"));
                assertTrue(Files.readAllLines(clientDirectory.resolve("resources.csv"))
                                .size()
                        > 1);
            } finally {
                if (client != null && client.isAlive()) {
                    client.destroyForcibly();
                    client.waitFor(5, TimeUnit.SECONDS);
                }
                if (server.isAlive()) {
                    server.destroyForcibly();
                    server.waitFor(5, TimeUnit.SECONDS);
                }
            }
        }
    }

    private Pair pair(String name, List<String> serverOptions, List<String> clientOptions) throws Exception {
        return pair(name, "5.0", serverOptions, clientOptions);
    }

    private Pair pair(String name, String version, List<String> serverOptions, List<String> clientOptions)
            throws Exception {
        Path serverReport = directory.resolve(name + "-server");
        Path clientReport = directory.resolve(name + "-client");
        Path serverLog = directory.resolve(name + "-server.log");
        Path clientLog = directory.resolve(name + "-client.log");
        var serverArgs = new ArrayList<>(List.of(
                "server",
                "--port=0",
                "--version=" + version,
                "--duration=PT1S",
                "--drain=PT1S",
                "--report=" + serverReport));
        serverArgs.addAll(serverOptions);
        var server = start(serverLog, serverArgs.toArray(String[]::new));
        Process client = null;
        try {
            long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
            String output = "";
            while (System.nanoTime() - deadline < 0) {
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
            var clientArgs = new ArrayList<>(List.of(
                    "client",
                    "--port=" + port,
                    "--version=" + version,
                    "--duration=PT1S",
                    "--drain=PT1S",
                    "--report=" + clientReport));
            clientArgs.addAll(clientOptions);
            client = start(clientLog, clientArgs.toArray(String[]::new));
            assertTrue(client.waitFor(8, TimeUnit.SECONDS));
            assertEquals(0, client.exitValue(), Files.readString(clientLog));
            assertTrue(server.waitFor(8, TimeUnit.SECONDS));
            assertEquals(0, server.exitValue(), Files.readString(serverLog));
            return new Pair(
                    Files.readString(clientReport.resolve("report.json")),
                    Files.readString(serverReport.resolve("report.json")));
        } finally {
            if (client != null && client.isAlive()) {
                client.destroyForcibly();
                client.waitFor(5, TimeUnit.SECONDS);
            }
            if (server.isAlive()) {
                server.destroyForcibly();
                server.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    private record Pair(String client, String server) {}

    private static Process start(Path log, String... arguments) throws Exception {
        var locations = List.of(SimulatorMain.class, SmppClient.class, Histogram.class).stream()
                .map(type -> Path.of(type.getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .getPath())
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
        command.addAll(List.of(arguments));
        command.add("--revision=e79b9f48d7f2c94aad96b2bb734abe338d420a15");
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("SMPP_SYSTEM_ID", "sim");
        builder.environment().put("SMPP_PASSWORD", "testpwd");
        return builder.start();
    }
}
