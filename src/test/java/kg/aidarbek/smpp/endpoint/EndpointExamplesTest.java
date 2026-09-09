package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.examples.ExampleClient;
import kg.aidarbek.examples.ExampleServer;
import org.junit.jupiter.api.Test;

class EndpointExamplesTest {
    @Test
    void compiledExamplesPerformTheDocumentedControlExchange() throws Exception {
        CountDownLatch bound = new CountDownLatch(1);
        SmppServer server = ExampleServer.create(0, ignored -> bound.countDown());
        try {
            var address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            ExampleClient.run(address);
            assertTrue(
                    bound.await(100, TimeUnit.MILLISECONDS),
                    "The example client must bind to the actual example server");
        } finally {
            server.close();
            assertTrue(server.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
        }
    }
}
