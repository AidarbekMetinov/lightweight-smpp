package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.endpoint.BindDecision;
import kg.aidarbek.smpp.endpoint.BoundSession;
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
import kg.aidarbek.smpp.protocol.BroadcastSm;
import kg.aidarbek.smpp.protocol.BroadcastSmResponse;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.QueryBroadcastSmResponse;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(25)
class BroadcastTrafficTest {
    @Test
    void deterministicInvalidVersionRoleAndModeFailBeforeEndpointCreation() {
        for (String name : new String[] {"broadcast", "query-broadcast", "cancel-broadcast"}) {
            assertTrue(BroadcastTraffic.find(name, config("client", "5.0", name, "--reject=0"))
                    .isPresent());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> BroadcastTraffic.find(name, config("client", "3.4", name, "--reject=0")));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> BroadcastTraffic.find(name, config("server", "5.0", name, "--reject=0")));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> BroadcastTraffic.find(
                            name,
                            SimulatorArguments.parse(
                                    "client",
                                    "--revision=" + "a".repeat(40),
                                    "--version=5.0",
                                    "--bind=rx",
                                    "--operation=" + name)));
        }
        assertTrue(BroadcastTraffic.find("other", config("client", "5.0", "broadcast", "--reject=0"))
                .isEmpty());
    }

    @Test
    void payloadHasOneTlvAndBroadcastFieldsCannotCarryReceiptOrUdhiFlags() {
        SimulatorConfig config = config("client", "5.0", "broadcast", "--reject=0");
        for (int length : new int[] {0, 254, 255, 256, 65535}) {
            BroadcastSm broadcast = BroadcastTraffic.broadcast(config, new RawContent(length, 1).next(0));
            List<Tlv> payload = broadcast.optionalParameters().entries().stream()
                    .filter(t -> t.tag() == 0x0424)
                    .toList();
            assertEquals(1, payload.size());
            assertEquals(length, payload.getFirst().valueLength());
            assertEquals(
                    2,
                    broadcast.optionalParameters().entries().stream()
                            .filter(t -> t.tag() == 0x0606)
                            .count());
            assertEquals(4, broadcast.dataCoding());
        }
        TrafficContent invalid =
                new TrafficContent(4, 1, new OctetString(new byte[0]), new OptionalParameters(List.of()));
        assertThrows(IllegalArgumentException.class, () -> BroadcastTraffic.broadcast(config, invalid));
    }

    @Test
    void allBroadcastOperationsExerciseRealPositiveNegativeRepliesAndCongestionObservation() throws Exception {
        for (String name : new String[] {"broadcast", "query-broadcast", "cancel-broadcast"})
            for (boolean reject : new boolean[] {false, true}) {
                SimulatorConfig sender = config("client", "5.0", name, "--reject=0"),
                        receiver = config("server", "5.0", name, reject ? "--reject=100" : "--reject=0");
                try (ReplyController replies = new ReplyController(receiver, () -> new RawContent(160, 1))) {
                    EndpointHandlers.Builder handlers = EndpointHandlers.builder();
                    BroadcastTraffic.register(handlers, replies);
                    SmppServer server = server(receiver, handlers.build());
                    SmppClient client = new SmppClient();
                    try (server;
                            client) {
                        InetSocketAddress address =
                                server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
                        BoundSession session = client.connect(new ClientConfig(
                                        address,
                                        new BindRequest(BindMode.TRANSMITTER, "sim", "sim", "", 0x50, 0, 0, ""),
                                        true))
                                .toCompletableFuture()
                                .get(2, TimeUnit.SECONDS);
                        var response = BroadcastTraffic.find(name, sender)
                                .orElseThrow()
                                .send(session, new RawContent(160, 1).next(0), 0)
                                .result()
                                .toCompletableFuture()
                                .get(2, TimeUnit.SECONDS);
                        assertEquals(reject ? 0x58 : 0, response.commandStatus());
                        assertEquals(1, replies.snapshot().received());
                        assertEquals(0, replies.snapshot().invalidContent());
                        assertEquals(reject ? 1 : 0, replies.snapshot().rejected());
                        if (!reject)
                            assertEquals(80, session.congestion().orElseThrow().level());
                        if (response.command() instanceof BroadcastSmResponse broadcast)
                            assertEquals(!reject, broadcast.fields().messageId().isPresent());
                        if (response.command() instanceof QueryBroadcastSmResponse query) {
                            assertEquals(!reject, query.fields().messageId().isPresent());
                            if (!reject)
                                assertEquals(
                                        2,
                                        query.fields().optionalParameters().entries().stream()
                                                .filter(t -> t.tag() == 0x0608)
                                                .count());
                        }
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

    private static SimulatorConfig config(String mode, String version, String operation, String fault) {
        return CommonTrafficTest.config(mode, version, operation, fault);
    }

    private static SmppServer server(SimulatorConfig config, EndpointHandlers handlers) {
        return new SmppServer(
                new ServerConfig(
                        new InetSocketAddress("127.0.0.1", 0), Set.of(config.version()), config.version(), "sim", 1, 2),
                EndpointOptions.defaults(),
                (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                ignored -> {},
                null,
                new ExchangeConfig(ExchangeOptions.defaults(), handlers));
    }
}
