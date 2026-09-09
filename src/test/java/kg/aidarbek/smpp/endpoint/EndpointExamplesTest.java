package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kg.aidarbek.examples.ExampleClient;
import kg.aidarbek.examples.ExampleServer;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.DeliverSmResponse;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.SubmitSm;
import org.junit.jupiter.api.Test;

class EndpointExamplesTest {
    @Test
    void exampleClientSendsASubmissionAndAcknowledgesDeliveryBeforeGracefulUnbind() throws Exception {
        CompletableFuture<Void> finished = new CompletableFuture<>();
        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread.ofPlatform().daemon().start(() -> {
                try {
                    ExampleClient.run((InetSocketAddress) listener.getLocalSocketAddress());
                    finished.complete(null);
                } catch (Throwable failure) {
                    finished.completeExceptionally(failure);
                }
            });
            try (RawPeer peer = RawPeer.accept(listener)) {
                peer.send(RawPeer.bindResponse(0x80000009L, 0, RawPeer.sequence(peer.read()), 0x34));
                byte[] submission = peer.read();
                assertEquals(
                        4, RawPeer.command(submission), "The compiled client example must perform a real submission");
                assertEquals(2, RawPeer.sequence(submission));
                peer.send(HexFormat.of().parseHex("0000001580000004000000000000000264656d6f00"));
                peer.send(HexFormat.of().parseHex("00000021000000050000000000000015" + "00".repeat(17)));
                boolean deliveryAcknowledged = false;
                while (true) {
                    byte[] request = peer.read();
                    long command = RawPeer.command(request);
                    if (command == 0x80000005L) {
                        assertEquals(0, RawPeer.status(request));
                        assertEquals(21, RawPeer.sequence(request));
                        deliveryAcknowledged = true;
                    } else if (command == 0x15) peer.send(RawPeer.header(0x80000015L, 0, RawPeer.sequence(request)));
                    else {
                        assertEquals(6, command);
                        assertTrue(
                                deliveryAcknowledged,
                                "Graceful example shutdown must flush its delivery acknowledgement");
                        peer.send(RawPeer.header(0x80000006L, 0, RawPeer.sequence(request)));
                        break;
                    }
                }
                finished.get(5, TimeUnit.SECONDS);
            } finally {
                finished.handle((ignored, failure) -> null).get(6, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void exampleServerAcceptsASubmissionAndOriginatesAnIndependentDelivery() throws Exception {
        CompletableFuture<Void> delivery = new CompletableFuture<>();
        EndpointHandlers handlers = EndpointHandlers.builder()
                .on(MessageOperations.DELIVER_SM, incoming -> {
                    delivery.complete(null);
                    return CompletableFuture.completedFuture(new HandlerResponse<>(
                            0,
                            new DeliverSmResponse(new MessageResponse(Optional.of(""), EndpointPdus.NO_PARAMETERS))));
                })
                .build();
        SmppClient client =
                new SmppClient(EndpointOptions.defaults(), new ExchangeConfig(ExchangeOptions.defaults(), handlers));
        SmppServer server = ExampleServer.create(0, ignored -> {});
        try {
            var address = server.start().toCompletableFuture().get(2, TimeUnit.SECONDS);
            BoundSession session = client.connect(new ClientConfig(
                            address, new BindRequest(BindMode.TRANSCEIVER, "demo", "demo", "", 0x34, 0, 0, ""), true))
                    .toCompletableFuture()
                    .get(2, TimeUnit.SECONDS);
            var response = session.submission()
                    .orElseThrow()
                    .send(new SubmitSm(ExchangeMatrixTest.shortMessage()))
                    .result()
                    .toCompletableFuture()
                    .get(2, TimeUnit.SECONDS);
            assertEquals(
                    0, response.commandStatus(), "The runnable server example must expose real application acceptance");
            assertEquals("demo-1", response.command().fields().messageId().orElseThrow());
            delivery.get(2, TimeUnit.SECONDS);
        } finally {
            client.close();
            server.close();
            assertTrue(client.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
            assertTrue(server.termination()
                    .toCompletableFuture()
                    .get(3, TimeUnit.SECONDS)
                    .complete());
        }
    }

    @Test
    void compiledExamplesPerformTheDocumentedMessageExchange() throws Exception {
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
