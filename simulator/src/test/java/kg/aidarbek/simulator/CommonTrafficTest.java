package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.endpoint.BindDecision;
import kg.aidarbek.smpp.endpoint.ClientConfig;
import kg.aidarbek.smpp.endpoint.EndpointHandlers;
import kg.aidarbek.smpp.endpoint.EndpointOptions;
import kg.aidarbek.smpp.endpoint.ExchangeConfig;
import kg.aidarbek.smpp.endpoint.ExchangeOptions;
import kg.aidarbek.smpp.endpoint.ServerConfig;
import kg.aidarbek.smpp.endpoint.SmppClient;
import kg.aidarbek.smpp.endpoint.SmppServer;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.QuerySmResponse;
import kg.aidarbek.smpp.protocol.SubmitMultiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(25)
class CommonTrafficTest {
    @Test
    void invalidThreeFourReplacementSizeIsRejectedBeforeAnyTrafficOperationCanStart() {
        var invalid = SimulatorArguments.parse(
                "client",
                "--revision=" + "a".repeat(40),
                "--operation=replace",
                "--version=3.4",
                "--bind=tx",
                "--payload=255");
        assertThrows(IllegalArgumentException.class, () -> CommonTraffic.find("replace", invalid));
        var valid = SimulatorArguments.parse(
                "client",
                "--revision=" + "a".repeat(40),
                "--operation=replace",
                "--version=5.0",
                "--bind=tx",
                "--payload=65535");
        assertTrue(CommonTraffic.find("replace", valid).isPresent());
    }

    @Test
    void replacementKeepsProfileBoundsAndRejectsMetadataAbsentFromItsWireFormat() {
        for (String version : new String[] {"3.4", "5.0"}) {
            var config = config("client", version, "replace", "--reject=0");
            for (int bytes : new int[] {0, 254, 255, 256, 65535}) {
                TrafficContent content = new RawContent(bytes, 1).next(0);
                if (version.equals("3.4") && bytes > 254)
                    assertThrows(IllegalArgumentException.class, () -> CommonTraffic.replacement(config, content, 0));
                else {
                    var replacement = CommonTraffic.replacement(config, content, 0);
                    assertEquals(
                            bytes <= 255 ? bytes : 0, replacement.shortMessage().length());
                    assertEquals(
                            bytes > 255 ? 1 : 0,
                            replacement.optionalParameters().entries().size());
                }
            }
            TrafficContent text = new TrafficContent(
                    0,
                    8,
                    new kg.aidarbek.smpp.protocol.OctetString(new byte[] {0, 65}),
                    new kg.aidarbek.smpp.protocol.OptionalParameters(java.util.List.of()));
            assertThrows(IllegalArgumentException.class, () -> CommonTraffic.replacement(config, text, 0));
        }
    }

    @Test
    void allCommonOperationsExerciseRealRequestsAndDeterministicPositiveNegativeRepliesInBothProfiles()
            throws Exception {
        for (String version : new String[] {"3.4", "5.0"})
            for (String name : new String[] {"query", "cancel", "replace", "multi"})
                for (boolean reject : new boolean[] {false, true}) {
                    SimulatorConfig serverConfig =
                            config("server", version, name, reject ? "--reject=100" : "--reject=0");
                    SimulatorConfig clientConfig = config("client", version, name, "--reject=0");
                    try (ReplyController replies = new ReplyController(serverConfig, () -> new RawContent(160, 1))) {
                        EndpointHandlers.Builder handlers = EndpointHandlers.builder();
                        CommonTraffic.register(handlers, replies);
                        try (SmppServer server = new SmppServer(
                                        new ServerConfig(
                                                new InetSocketAddress("127.0.0.1", 0),
                                                Set.of(serverConfig.version()),
                                                serverConfig.version(),
                                                "sim",
                                                1,
                                                2),
                                        EndpointOptions.defaults(),
                                        (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                                        ignored -> {},
                                        null,
                                        new ExchangeConfig(ExchangeOptions.defaults(), handlers.build()));
                                SmppClient client = new SmppClient()) {
                            InetSocketAddress address =
                                    server.start().toCompletableFuture().get(3, TimeUnit.SECONDS);
                            var session = client.connect(new ClientConfig(
                                            address,
                                            new BindRequest(
                                                    BindMode.TRANSMITTER,
                                                    "sim",
                                                    "sim",
                                                    "",
                                                    clientConfig.version().interfaceVersion(),
                                                    0,
                                                    0,
                                                    ""),
                                            true))
                                    .toCompletableFuture()
                                    .get(3, TimeUnit.SECONDS);
                            var response = CommonTraffic.find(name, clientConfig)
                                    .orElseThrow()
                                    .send(session, new RawContent(160, 1).next(0), 0)
                                    .result()
                                    .toCompletableFuture()
                                    .get(3, TimeUnit.SECONDS);
                            assertEquals(reject ? 0x58 : 0, response.commandStatus());
                            assertEquals(1, replies.snapshot().received());
                            assertEquals(0, replies.snapshot().invalidContent());
                            assertEquals(reject ? 1 : 0, replies.snapshot().rejected());
                            if (response.command() instanceof QuerySmResponse query)
                                assertEquals(
                                        !reject || version.equals("3.4"),
                                        query.result().isPresent());
                            if (response.command() instanceof SubmitMultiResponse multi)
                                assertEquals(
                                        !reject || version.equals("3.4"),
                                        multi.result().isPresent());
                            assertTrue(client.shutdown(Duration.ofSeconds(1))
                                    .toCompletableFuture()
                                    .get(3, TimeUnit.SECONDS)
                                    .complete());
                            assertTrue(server.shutdown(Duration.ofSeconds(1))
                                    .toCompletableFuture()
                                    .get(3, TimeUnit.SECONDS)
                                    .complete());
                        }
                    }
                }
    }

    @Test
    void roleAndModeAreRejectedBeforeAttemptingARequest() {
        for (String name : new String[] {"query", "cancel", "replace", "multi"}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CommonTraffic.find(name, config("server", "5.0", name, "--reject=0")));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> CommonTraffic.find(
                            name,
                            SimulatorArguments.parse(
                                    "client", "--revision=" + "a".repeat(40), "--operation=" + name, "--bind=rx")));
        }
        assertTrue(CommonTraffic.find("unrelated", config("client", "5.0", "multi", "--reject=0"))
                .isEmpty());
    }

    static SimulatorConfig config(String mode, String version, String name, String faults) {
        return SimulatorArguments.parse(
                mode,
                "--revision=" + "a".repeat(40),
                "--version=" + version,
                "--operation=" + name,
                "--bind=tx",
                faults);
    }
}
