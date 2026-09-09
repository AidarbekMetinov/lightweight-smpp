package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import org.junit.jupiter.api.Test;

class EndpointResourcesTest {
    @Test
    void failedConstructionKeepsItsPermitUntilTransportCleanupActuallySucceeds() throws Exception {
        EndpointResources resources =
                new EndpointResources(EndpointAdversarialTest.options(1, Duration.ofSeconds(2), 1, 3), null);
        CompletableFuture<Void> cleanup = new CompletableFuture<>();
        IOException failure = new IOException("unfinished constructor transport cleanup");
        try {
            resources.reserve().retire(cleanup);
            assertThrows(
                    EndpointException.class,
                    resources::reserve,
                    "An unfinished allocated transport still consumes capacity");
            cleanup.completeExceptionally(failure);
            assertEquals(1, resources.connectionCount());
            EndpointTermination result = resources
                    .shutdown(Duration.ofMillis(50), true)
                    .toCompletableFuture()
                    .get(2, TimeUnit.SECONDS);
            assertFalse(result.complete());
            assertEquals(1, result.remainingConnections());
            assertEquals(java.util.List.of(failure), result.failures());
        } finally {
            cleanup.complete(null);
            resources.close();
        }
    }

    @Test
    void failedTransportCleanupIsPreservedAndCannotReleaseAConnectionSlot() throws Exception {
        EndpointOptions defaults = EndpointOptions.defaults();
        EndpointOptions options = new EndpointOptions(
                1,
                4,
                1024,
                1,
                8,
                defaults.connectTimeout(),
                defaults.bindTimeout(),
                defaults.requestTimeout(),
                Duration.ofMillis(100),
                defaults.pduLimits());
        EndpointResources resources = new EndpointResources(options, null);
        IOException closeFailure = new IOException("fixture close failure");
        EndpointConnectionTest.FakeTransport transport = new EndpointConnectionTest.FakeTransport(closeFailure);
        EndpointResources.Permit permit = resources.reserve();
        EndpointConnection connection = EndpointConnection.client(
                transport,
                new ClientConfig(
                        new InetSocketAddress("127.0.0.1", 1),
                        new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x34, 0, 0, ""),
                        false),
                options,
                resources.notifications);
        permit.attach(connection);
        try {
            connection.close();
            ExecutionException error = assertThrows(
                    ExecutionException.class,
                    () -> connection.termination().toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertEquals(closeFailure, error.getCause());
            assertThrows(EndpointException.class, resources::reserve);
            resources.close();
            EndpointTermination termination =
                    resources.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertFalse(termination.complete());
            assertEquals(1, termination.remainingConnections());
            assertTrue(termination.failures().contains(closeFailure));
        } finally {
            resources.close();
        }
    }

    @Test
    void exceptionalListenerTerminationCannotBeReportedAsSuccessfulCleanup() throws Exception {
        EndpointResources resources = new EndpointResources(EndpointOptions.defaults(), null);
        IOException failure = new IOException("fixture listener close failure");
        resources.listener(() -> {}, CompletableFuture.failedFuture(failure));
        resources.close();
        EndpointTermination result =
                resources.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
        assertFalse(result.complete());
        assertTrue(result.failures().contains(failure));
    }
}
