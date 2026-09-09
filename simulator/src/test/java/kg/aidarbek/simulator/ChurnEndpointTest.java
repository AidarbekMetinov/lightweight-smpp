package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import kg.aidarbek.smpp.endpoint.EndpointHandlers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(20)
class ChurnEndpointTest {
    @Test
    void replacesDistinctGenerationsInsideTheOriginalPhysicalConnectionBound() throws Exception {
        run(false);
    }

    @Test
    void reconnectsOnlyFreshGenerationsWithinItsExplicitLifetimeAttemptBudget() throws Exception {
        run(true);
    }

    private void run(boolean reconnect) throws Exception {
        var ready = new CompletableFuture<Integer>();
        var serverConfig =
                SimulatorArguments.parse("server", "--port=0", "--revision=0123456789012345678901234567890123456789");
        try (var server = new SimulatorEndpoint(
                serverConfig,
                EndpointHandlers.empty(),
                "sim",
                "sim",
                event -> ready.complete(Integer.parseInt(event.substring(11))),
                () -> {})) {
            var bound = new CompletableFuture<Void>();
            Thread owner = Thread.ofVirtual().start(() -> {
                try {
                    server.start();
                    bound.complete(null);
                } catch (Exception failure) {
                    bound.completeExceptionally(failure);
                }
            });
            try {
                var arguments = new java.util.ArrayList<>(java.util.List.of(
                        "client",
                        "--port=" + ready.get(3, TimeUnit.SECONDS),
                        "--revision=0123456789012345678901234567890123456789",
                        "--duration=PT1S"));
                arguments.addAll(
                        reconnect
                                ? java.util.List.of("--reconnect-attempts=3", "--reconnect-delay=PT0.01S")
                                : java.util.List.of("--churn-rate=10", "--churn-count=2"));
                var clientConfig = SimulatorArguments.parse(arguments.toArray(String[]::new));
                try (var client = new SimulatorEndpoint(
                        clientConfig, EndpointHandlers.empty(), "sim", "sim", event -> {}, () -> {})) {
                    client.start();
                    bound.get(3, TimeUnit.SECONDS);
                    client.startChurn(0);
                    for (int cycle = 0; cycle < 2; cycle++) {
                        var oldClient = client.session(0).id();
                        var oldServer = server.session(0).id();
                        if (reconnect) server.session(0).close();
                        long now = cycle * 100_000_000L;
                        long deadline =
                                System.nanoTime() + Duration.ofSeconds(3).toNanos();
                        do {
                            client.advance(now);
                            server.advance(now);
                            if (!oldClient.equals(client.session(0).id())
                                    && !oldServer.equals(server.session(0).id())) break;
                            LockSupport.parkNanos(100_000);
                        } while (deadline - System.nanoTime() > 0);
                        assertNotEquals(oldClient, client.session(0).id());
                        assertNotEquals(oldServer, server.session(0).id());
                        assertEquals(0, client.nextOrdinal(0));
                        assertEquals(1, client.nextOrdinal(0));
                    }
                    client.stopChurn();
                    if (reconnect) assertEquals(3, client.reconnectSnapshot().attempts());
                    else assertEquals(2, client.churnSnapshot().initiated());
                    assertEquals(2, client.lifecycleSnapshot().replacementBound());
                    assertEquals(1, client.lifecycleSnapshot().peakConnections());
                    assertEquals(1, server.lifecycleSnapshot().peakConnections());
                    assertTrue(client.shutdown());
                    if (reconnect) {
                        assertEquals(0, client.reconnectSnapshot().unfinishedLoops());
                        assertEquals(
                                java.util.Map.of("CANCELLED", 1L),
                                client.reconnectSnapshot().terminalReasons());
                    }
                }
                assertTrue(server.shutdown());
            } finally {
                if (owner.isAlive()) owner.interrupt();
                owner.join(3000);
                assertFalse(owner.isAlive());
            }
        }
    }
}
