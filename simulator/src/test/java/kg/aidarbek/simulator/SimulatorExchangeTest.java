package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class SimulatorExchangeTest {
    @Test
    void interruptedStartupPreservesTheOwnersInterruptAndStillAllowsCleanup() throws Exception {
        try (var listener = new java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            var config = SimulatorArguments.parse(
                    "client",
                    "--revision=e79b9f48d7f2c94aad96b2bb734abe338d420a15",
                    "--host=" + listener.getInetAddress().getHostAddress(),
                    "--port=" + listener.getLocalPort());
            try (var endpoint =
                    new SimulatorEndpoint(config, EndpointHandlers.empty(), "sim", "sim", event -> {}, () -> {})) {
                try {
                    Thread.currentThread().interrupt();
                    assertThrows(InterruptedException.class, endpoint::start);
                    assertTrue(Thread.currentThread().isInterrupted());
                } finally {
                    Thread.interrupted();
                }
                assertTrue(endpoint.shutdown());
            }
        }
    }

    @Test
    void countsContentExtractionFailuresBeforeReturningANegativeAcknowledgement() throws Exception {
        var config = SimulatorArguments.parse(
                "server", "--revision=e79b9f48d7f2c94aad96b2bb734abe338d420a15", "--version=5.0");
        try (var replies = new ReplyController(config, () -> new RawContent(160, 1))) {
            var handlers = EndpointHandlers.builder();
            MessageTraffic.register(handlers, replies);
            try (var server = new SmppServer(
                    new ServerConfig(
                            new InetSocketAddress("127.0.0.1", 0),
                            Set.of(config.version()),
                            config.version(),
                            "sim",
                            2,
                            8),
                    EndpointOptions.defaults(),
                    (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                    session -> {},
                    null,
                    new ExchangeConfig(ExchangeOptions.defaults(), handlers.build()))) {
                var address = server.start().toCompletableFuture().get(5, TimeUnit.SECONDS);
                try (var peer = new java.net.Socket()) {
                    peer.connect(address, 2000);
                    peer.setSoTimeout(3000);
                    var input = new java.io.DataInputStream(peer.getInputStream());
                    var output = new java.io.DataOutputStream(peer.getOutputStream());
                    write(output, 9, 1, new byte[] {'s', 'i', 'm', 0, 's', 'i', 'm', 0, 0, 0x50, 0, 0, 0});
                    assertEquals(0, java.nio.ByteBuffer.wrap(read(input)).getInt(8));
                    var bodyBytes = new java.io.ByteArrayOutputStream();
                    var body = new java.io.DataOutputStream(bodyBytes);
                    body.writeByte(0); // service_type
                    body.write(new byte[] {1, 1, '1', '0', '0', '0', 0, 1, 1, '2', '0', '0', '0', 0});
                    body.write(
                            new byte[9]); // esm, protocol, priority, schedule, validity, registered, replace, coding,
                    // default
                    body.writeByte(160);
                    body.write(new byte[160]);
                    for (int index = 0; index < 65; index++) {
                        body.writeShort(0x1400 + index);
                        body.writeShort(1);
                        body.writeByte(1);
                    }
                    write(output, 4, 2, bodyBytes.toByteArray());
                    assertNotEquals(0, java.nio.ByteBuffer.wrap(read(input)).getInt(8));
                    assertEquals(1, replies.snapshot().received());
                    assertEquals(1, replies.snapshot().invalidContent());
                }
                assertTrue(server.shutdown(Duration.ofSeconds(2))
                        .toCompletableFuture()
                        .get(5, TimeUnit.SECONDS)
                        .complete());
            }
        }
    }

    private static void write(java.io.DataOutputStream output, int command, int sequence, byte[] body)
            throws java.io.IOException {
        output.writeInt(16 + body.length);
        output.writeInt(command);
        output.writeInt(0);
        output.writeInt(sequence);
        output.write(body);
        output.flush();
    }

    private static byte[] read(java.io.DataInputStream input) throws java.io.IOException {
        int length = input.readInt();
        assertTrue(length >= 16 && length <= 1048576);
        byte[] rest = input.readNBytes(length - 4);
        assertEquals(length - 4, rest.length);
        return java.nio.ByteBuffer.allocate(length).putInt(length).put(rest).array();
    }

    @Test
    void appliesReceiverContentChecksAndDeterministicRejectionOverRealEndpoints() throws Exception {
        var config = SimulatorArguments.parse(
                "server", "--revision=e79b9f48d7f2c94aad96b2bb734abe338d420a15", "--reject=100");
        try (var replies = new ReplyController(config, () -> new RawContent(160, 1))) {
            var handlers = EndpointHandlers.builder();
            MessageTraffic.register(handlers, replies);
            try (var server = new SmppServer(
                            new ServerConfig(
                                    new InetSocketAddress("127.0.0.1", 0),
                                    Set.of(config.version()),
                                    config.version(),
                                    "sim",
                                    2,
                                    8),
                            EndpointOptions.defaults(),
                            (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                            session -> {},
                            null,
                            new ExchangeConfig(ExchangeOptions.defaults(), handlers.build()));
                    var client = new SmppClient(EndpointOptions.defaults())) {
                var address = server.start().toCompletableFuture().get(5, TimeUnit.SECONDS);
                var session = client.connect(new ClientConfig(
                                address, new BindRequest(BindMode.TRANSCEIVER, "sim", "sim", "", 0x34, 0, 0, ""), true))
                        .toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                var senderConfig =
                        SimulatorArguments.parse("client", "--revision=e79b9f48d7f2c94aad96b2bb734abe338d420a15");
                var operation = MessageTraffic.find("submit", senderConfig).orElseThrow();
                var response = operation
                        .send(session, new RawContent(160, 1).next(0), 0)
                        .result()
                        .toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
                assertEquals(0x58, response.commandStatus());
                assertEquals(1, replies.snapshot().rejected());
                assertEquals(1, replies.snapshot().received());
                assertTrue(client.shutdown(Duration.ofSeconds(2))
                        .toCompletableFuture()
                        .get(5, TimeUnit.SECONDS)
                        .complete());
                assertTrue(server.shutdown(Duration.ofSeconds(2))
                        .toCompletableFuture()
                        .get(5, TimeUnit.SECONDS)
                        .complete());
            }
        }
    }
}
