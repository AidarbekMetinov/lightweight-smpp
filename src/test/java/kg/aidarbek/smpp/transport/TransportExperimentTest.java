package kg.aidarbek.smpp.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TransportExperimentTest {
    @Test
    void accountsForEveryEchoAndWriteBeforeCompletingCleanup() throws Exception {
        try (TransportExperiment.ServerRun server = new TransportExperiment.ServerRun(1, 2)) {
            TransportExperiment.Result result = TransportExperiment.runClient(server.port(), 1, 2);
            assertEquals(2, result.frames());
            assertEquals(2, result.writes());
            assertTrue(result.elapsedNanos() > 0);
            assertEquals(2, server.awaitFrames());
        }
    }

    @Test
    void rejectsInvalidExperimentBoundsBeforeNetworking() {
        assertThrows(IllegalArgumentException.class, () -> new TransportExperiment.ServerRun(0, 1).close());
        assertThrows(IllegalArgumentException.class, () -> new TransportExperiment.ServerRun(65, 1).close());
        assertThrows(IllegalArgumentException.class, () -> new TransportExperiment.ServerRun(1, 0).close());
        assertThrows(IllegalArgumentException.class, () -> new TransportExperiment.ServerRun(1, 10001).close());
    }
}
