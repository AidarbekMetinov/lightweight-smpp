package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class ReplyControllerTest {
    @Test
    void retiresClosedStreamsWithoutMergingOrHidingTheirIncompleteSarFixture() throws Exception {
        var config = SimulatorArguments.parse(
                "server", "--revision=0123456789012345678901234567890123456789", "--content=sar", "--payload=161");
        try (var peer = new Peer(config, new AtomicLong())) {
            var content = HelperContentPlans.create("sar", 161, 1);
            var operation = MessageTraffic.find("submit", peer.sender).orElseThrow();
            assertEquals(
                    0,
                    operation
                            .send(peer.session, content.next(0), 0)
                            .result()
                            .toCompletableFuture()
                            .get(2, TimeUnit.SECONDS)
                            .commandStatus());
            assertEquals(1, peer.replies.snapshot().incompleteAssemblies());
            peer.session.close();
            peer.session.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            await(() -> {
                peer.replies.advance(0);
                return peer.replies.snapshot().retainedStreams() == 0;
            });
            assertEquals(1, peer.replies.snapshot().incompleteAssemblies());
            var next = peer.client
                    .connect(new ClientConfig(
                            (InetSocketAddress) peer.session.peer(),
                            new BindRequest(BindMode.TRANSCEIVER, "sim", "sim", "", 0x34, 0, 0, ""),
                            true))
                    .toCompletableFuture()
                    .get(2, TimeUnit.SECONDS);
            assertEquals(
                    0,
                    operation
                            .send(next, content.next(1), 1)
                            .result()
                            .toCompletableFuture()
                            .get(2, TimeUnit.SECONDS)
                            .commandStatus());
            assertEquals(2, peer.replies.snapshot().incompleteAssemblies());
            assertEquals(1, peer.replies.snapshot().retainedStreams());
        }
    }

    @Test
    void slowConsumerDelaysOrdinaryAcknowledgementsUsingControlledTime() throws Exception {
        var clock = new AtomicLong(Long.MAX_VALUE - 100_000_000);
        var config = SimulatorArguments.parse(
                "server", "--revision=0123456789012345678901234567890123456789", "--consumer-delay=PT0.2S");
        try (var peer = new Peer(config, clock)) {
            var call = MessageTraffic.find("submit", peer.sender)
                    .orElseThrow()
                    .send(peer.session, new RawContent(160, 1).next(0), 0);
            await(() -> peer.replies.snapshot().pendingDecisions() > 0 || call.isDone());
            assertEquals(1, peer.replies.snapshot().pendingDecisions());
            peer.replies.advance(clock.get() + 199_999_999);
            assertFalse(call.isDone());
            peer.replies.advance(clock.get() + 200_000_000);
            assertEquals(
                    0,
                    call.result().toCompletableFuture().get(2, TimeUnit.SECONDS).commandStatus());
            assertEquals(0, peer.replies.snapshot().pendingDecisions());
        }
    }

    private static void await(java.util.function.BooleanSupplier condition) {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (!condition.getAsBoolean() && deadline - System.nanoTime() > 0) LockSupport.parkNanos(100_000);
        assertTrue(condition.getAsBoolean(), "Coordinated peer event did not arrive");
    }

    private static final class Peer implements AutoCloseable {
        private final ReplyController replies;
        private final SmppClient client;
        private final SmppServer server;
        private final BoundSession session;
        private final SimulatorConfig sender =
                SimulatorArguments.parse("client", "--revision=0123456789012345678901234567890123456789");

        Peer(SimulatorConfig config, AtomicLong clock) throws Exception {
            replies = new ReplyController(
                    config,
                    () -> config.content().equals("raw")
                            ? new RawContent(160, 1)
                            : HelperContentPlans.create(config.content(), config.payloadBytes(), 1),
                    clock::get);
            var handlers = EndpointHandlers.builder();
            MessageTraffic.register(handlers, replies);
            server = new SmppServer(
                    new ServerConfig(
                            new InetSocketAddress("127.0.0.1", 0),
                            Set.of(config.version()),
                            config.version(),
                            "sim",
                            2,
                            8),
                    EndpointOptions.defaults(),
                    (request, address) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                    value -> {},
                    null,
                    new ExchangeConfig(ExchangeOptions.defaults(), handlers.build()));
            client = new SmppClient(EndpointOptions.defaults());
            try {
                var address = server.start().toCompletableFuture().get(3, TimeUnit.SECONDS);
                session = client.connect(new ClientConfig(
                                address, new BindRequest(BindMode.TRANSCEIVER, "sim", "sim", "", 0x34, 0, 0, ""), true))
                        .toCompletableFuture()
                        .get(3, TimeUnit.SECONDS);
            } catch (Exception failure) {
                close();
                throw failure;
            }
        }

        @Override
        public void close() {
            replies.close();
            try {
                try {
                    assertTrue(client.shutdown(Duration.ofSeconds(2))
                            .toCompletableFuture()
                            .get(3, TimeUnit.SECONDS)
                            .complete());
                } finally {
                    assertTrue(server.shutdown(Duration.ofSeconds(2))
                            .toCompletableFuture()
                            .get(3, TimeUnit.SECONDS)
                            .complete());
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) {
                throw new AssertionError(failure);
            } finally {
                client.close();
                server.close();
            }
        }
    }
}
