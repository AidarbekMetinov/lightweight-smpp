package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import kg.aidarbek.smpp.endpoint.SmppClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReceiptScenarioProcessTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @CsvSource({"3.4,0", "5.0,0", "3.4,3", "5.0,3", "3.4,1", "5.0,1"})
    void separateJvmsCorrelateOneEarlyReceiptPerPositiveSubmissionAndNoReceiptForRejection(
            String version, int rejectEvery) throws Exception {
        Pair result = pair(version, "none", rejectEvery);
        int negatives = rejectEvery == 0 ? 0 : 4 / rejectEvery;
        int positives = 4 - negatives;
        assertEquals(0, result.clientExit(), result.clientLog());
        assertEquals(0, result.serverExit(), result.serverLog());
        assertEquals(positives, number(result.clientReport(), "positiveSubmissions"));
        assertEquals(negatives, number(result.clientReport(), "negativeSubmissions"));
        assertEquals(positives, number(result.clientReport(), "matched"));
        assertEquals(positives, number(result.clientReport(), "earlyReceipts"));
        assertEquals(positives, number(result.clientReport(), "receiptRequests"));
        assertEquals(positives, number(result.serverReport(), "positiveReceiptResponses"));
        assertTrue(number(result.clientReport(), "peakActive") <= 2);
        assertTrue(number(result.serverReport(), "peakDecisions") <= 2);
        assertTrue(result.clientReport().contains("\"cleanup\":true"));
        assertTrue(result.serverReport().contains("\"cleanup\":true"));
        assertTrue(result.clientReport().contains("\"passed\":true"));
        assertTrue(result.serverReport().contains("\"passed\":true"));
    }

    @Test
    void idleServerAndRefusedClientFinishWithTruthfulFailureReportsAndFreshPaths() throws Exception {
        Path serverLog = directory.resolve("idle.log");
        Path serverReport = directory.resolve("idle");
        Process server = launch(
                serverLog,
                "server",
                "--report=" + serverReport,
                "--timeout=PT0.1S",
                "--duration=PT0.3S",
                "--drain=PT0.5S");
        try {
            assertTrue(ready(server, serverLog) > 0);
            assertTrue(server.waitFor(5, TimeUnit.SECONDS));
            assertEquals(1, server.exitValue());
            String report = Files.readString(serverReport.resolve("summary.json"));
            assertEquals(0, number(report, "submissions"));
            assertTrue(report.contains("\"cleanup\":true"));
        } finally {
            stop(server);
        }
        byte[] original = Files.readAllBytes(serverReport.resolve("summary.json"));
        Process reuse = launch(directory.resolve("reuse.log"), "server", "--report=" + serverReport);
        try {
            assertTrue(reuse.waitFor(3, TimeUnit.SECONDS));
            assertEquals(1, reuse.exitValue());
        } finally {
            stop(reuse);
        }
        org.junit.jupiter.api.Assertions.assertArrayEquals(
                original, Files.readAllBytes(serverReport.resolve("summary.json")));
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) {
            port = reservation.getLocalPort();
        }
        Process client = launch(
                directory.resolve("refused.log"),
                "client",
                "--port=" + port,
                "--report=" + directory.resolve("refused"),
                "--timeout=PT0.2S",
                "--duration=PT0.6S",
                "--drain=PT0.5S");
        try {
            assertTrue(client.waitFor(5, TimeUnit.SECONDS));
            assertEquals(1, client.exitValue());
            String report = Files.readString(directory.resolve("refused/summary.json"));
            assertEquals(0, number(report, "attempted"));
            assertTrue(report.contains("\"cleanup\":true"));
            assertFalse(report.contains("\"failure\":\"\""));
        } finally {
            stop(client);
        }
    }

    @ParameterizedTest
    @CsvSource({"3.4,missing", "5.0,missing", "3.4,duplicate", "5.0,duplicate", "3.4,mismatch", "5.0,mismatch"})
    void missingDuplicateAndMismatchedReceiptsFailCorrelationWithSeparateActualCounts(String version, String fault)
            throws Exception {
        Pair result = pair(version, fault, 0);
        assertEquals(1, result.clientExit(), result.clientReport());
        assertEquals(4, number(result.clientReport(), "positiveSubmissions"));
        assertEquals(0, number(result.clientReport(), "negativeSubmissions"));
        assertEquals(1, number(result.serverReport(), "faultSelections"));
        assertEquals(1, number(result.serverReport(), "faultActions"));
        if (fault.equals("duplicate")) {
            assertEquals(5, number(result.clientReport(), "receiptRequests"));
            assertEquals(1, number(result.clientReport(), "duplicateReceipts"));
            assertEquals(1, number(result.serverReport(), "negativeReceiptResponses"));
        } else {
            assertEquals(1, number(result.clientReport(), "missingReceipts"));
            assertEquals(3, number(result.clientReport(), "matched"));
            assertEquals(fault.equals("missing") ? 3 : 4, number(result.clientReport(), "receiptRequests"));
            assertEquals(fault.equals("mismatch") ? 1 : 0, number(result.clientReport(), "unmatchedReceipts"));
        }
        assertEquals(0, number(result.clientReport(), "active"));
        assertEquals(0, number(result.clientReport(), "earlyHeld"));
        assertEquals(0, number(result.serverReport(), "pendingDecisions"));
        assertTrue(result.clientReport().contains("\"cleanup\":true"));
        assertTrue(result.serverReport().contains("\"cleanup\":true"));
    }

    private Pair pair(String version, String fault, int rejectEvery) throws Exception {
        Path base = Files.createDirectory(directory.resolve(version + "-" + fault));
        Path serverLog = base.resolve("server.log"), clientLog = base.resolve("client.log");
        Process server = launch(
                serverLog,
                "server",
                "--port=0",
                "--version=" + version,
                "--count=4",
                "--window=2",
                "--reject-every=" + rejectEvery,
                "--fault=" + fault,
                "--timeout=PT1S",
                "--duration=PT10S",
                "--drain=PT1S",
                "--report=" + base.resolve("server"));
        Process client = null;
        try {
            int port = ready(server, serverLog);
            client = launch(
                    clientLog,
                    "client",
                    "--port=" + port,
                    "--version=" + version,
                    "--count=4",
                    "--window=2",
                    "--reject-every=" + rejectEvery,
                    "--timeout=PT1S",
                    "--duration=PT8S",
                    "--drain=PT1S",
                    "--report=" + base.resolve("client"));
            assertTrue(client.waitFor(15, TimeUnit.SECONDS), "client exceeded finite process bound");
            assertTrue(server.waitFor(15, TimeUnit.SECONDS), "server exceeded finite process bound");
            return new Pair(
                    client.exitValue(),
                    server.exitValue(),
                    Files.readString(base.resolve("client/summary.json")),
                    Files.readString(base.resolve("server/summary.json")),
                    Files.readString(clientLog),
                    Files.readString(serverLog));
        } finally {
            stopBoth(client, server);
        }
    }

    private static Process launch(Path log, String... arguments) throws Exception {
        String classpath = Path.of(ReceiptScenario.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI())
                + File.pathSeparator
                + Path.of(SmppClient.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI());
        List<String> command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xms32m",
                "-Xmx128m",
                "-cp",
                classpath,
                ReceiptScenario.class.getName()));
        command.addAll(List.of(arguments));
        return new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
    }

    private static int ready(Process process, Path log) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() - deadline < 0) {
            var matcher = Pattern.compile("READY port=(\\d+)").matcher(Files.readString(log));
            if (matcher.find()) return Integer.parseInt(matcher.group(1));
            if (!process.isAlive()) break;
            Thread.sleep(10);
        }
        throw new AssertionError("Server did not bind and announce readiness: " + Files.readString(log));
    }

    private static int number(String json, String key) {
        var matcher = Pattern.compile("\"" + key + "\":(\\d+)").matcher(json);
        assertTrue(matcher.find(), key + " missing from " + json);
        return Integer.parseInt(matcher.group(1));
    }

    private static void stop(Process process) throws Exception {
        if (process == null || !process.isAlive()) return;
        process.destroy();
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            assertTrue(process.waitFor(2, TimeUnit.SECONDS));
        }
    }

    static void stopBoth(Process client, Process server) throws Exception {
        try {
            stop(client);
        } finally {
            stop(server);
        }
    }

    private record Pair(
            int clientExit,
            int serverExit,
            String clientReport,
            String serverReport,
            String clientLog,
            String serverLog) {}
}
