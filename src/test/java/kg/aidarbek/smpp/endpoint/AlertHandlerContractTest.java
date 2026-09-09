package kg.aidarbek.smpp.endpoint;

import static kg.aidarbek.smpp.endpoint.CommonOperationsEndpointTest.DESTINATION;
import static kg.aidarbek.smpp.endpoint.CommonOperationsEndpointTest.EMPTY;
import static kg.aidarbek.smpp.endpoint.CommonOperationsEndpointTest.SOURCE;
import static kg.aidarbek.smpp.endpoint.CommonOperationsEndpointTest.bind;
import static kg.aidarbek.smpp.endpoint.CommonOperationsEndpointTest.server;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.AlertNotification;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.DeliverSm;
import kg.aidarbek.smpp.protocol.DeliverSmResponse;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.ShortMessage;
import org.junit.jupiter.api.Test;

class AlertHandlerContractTest {
    @Test
    void aLateCompletedStageCannotBeatTheDeadlineWhenThePeriodicTickHasNotRun() throws Exception {
        var clock = new java.util.concurrent.atomic.AtomicLong(System.nanoTime());
        var decision = new CompletableFuture<Void>();
        var context = new CompletableFuture<IncomingNotification<AlertNotification>>();
        var callbacks = new kg.aidarbek.smpp.request.BoundedNotifications(8, 1);
        var handlers = new HandlerDispatcher(1, 1);
        CommonTestTransport transport = new CommonTestTransport();
        EndpointConnection connection = EndpointConnection.client(
                transport,
                new ClientConfig(
                        new java.net.InetSocketAddress("127.0.0.1", 1),
                        new kg.aidarbek.smpp.protocol.BindRequest(BindMode.RECEIVER, "", "", "", 0x34, 0, 0, ""),
                        false),
                EndpointOptions.defaults(),
                callbacks,
                handlers,
                new ExchangeConfig(
                        new ExchangeOptions(1, 1, 2, 4096, Duration.ofSeconds(1)),
                        EndpointHandlers.builder()
                                .onAlert(incoming -> {
                                    context.complete(incoming);
                                    return decision;
                                })
                                .build()),
                clock::get);
        try {
            connection.start();
            transport.written.remove();
            transport.receive(RawPeer.bindResponse(0x80000001L, 0, 1, 0x34));
            connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS);
            transport.receive(java.nio.ByteBuffer.allocate(22)
                    .putInt(22)
                    .putInt(0x102)
                    .putInt(0)
                    .putInt(81)
                    .put(new byte[6])
                    .array());
            IncomingNotification<AlertNotification> incoming = context.get(2, TimeUnit.SECONDS);
            clock.set(incoming.deadlineNanos());
            decision.complete(null);
            connection.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(kg.aidarbek.smpp.session.SessionState.CLOSED, connection.state());
            assertTrue(incoming.isCancelled());
        } finally {
            decision.complete(null);
            connection.close();
            handlers.close();
            callbacks.close();
            assertTrue(handlers.awaitTermination(Duration.ofSeconds(2)));
            assertTrue(callbacks.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    void alertAndMessageInvocationShareOneSessionLaneWhileControlProgressRemainsIndependent() throws Exception {
        CountDownLatch messageEntered = new CountDownLatch(1);
        CompletableFuture<Void> releaseInvocation = new CompletableFuture<>();
        CountDownLatch alertEntered = new CountDownLatch(1);
        EndpointHandlers handlers = EndpointHandlers.builder()
                .on(MessageOperations.DELIVER_SM, request -> {
                    messageEntered.countDown();
                    releaseInvocation.join();
                    return CompletableFuture.completedFuture(new HandlerResponse<>(
                            0, new DeliverSmResponse(new MessageResponse(Optional.of(""), EMPTY))));
                })
                .onAlert(notification -> {
                    alertEntered.countDown();
                    return CompletableFuture.completedFuture(null);
                })
                .build();
        CompletableFuture<BoundSession> bound = new CompletableFuture<>();
        try (SmppServer server = server(SmppVersion.V3_4, EndpointHandlers.empty(), bound);
                SmppClient client = new SmppClient(
                        EndpointOptions.defaults(),
                        new ExchangeConfig(new ExchangeOptions(2, 2, 4, 4096, Duration.ofSeconds(2)), handlers))) {
            bind(client, server, SmppVersion.V3_4, BindMode.RECEIVER);
            BoundSession mc = bound.get(2, TimeUnit.SECONDS);
            var message = mc.delivery()
                    .orElseThrow()
                    .send(new DeliverSm(new ShortMessage(
                            "",
                            SOURCE,
                            DESTINATION,
                            0,
                            0,
                            0,
                            "",
                            "",
                            0,
                            0,
                            4,
                            0,
                            new OctetString(new byte[0]),
                            EMPTY)));
            assertTrue(messageEntered.await(2, TimeUnit.SECONDS));
            mc.alerts()
                    .orElseThrow()
                    .send(new AlertNotification(SOURCE, DESTINATION, EMPTY))
                    .result()
                    .toCompletableFuture()
                    .get(2, TimeUnit.SECONDS);
            assertEquals(
                    0,
                    mc.enquireLink()
                            .result()
                            .toCompletableFuture()
                            .get(2, TimeUnit.SECONDS)
                            .commandStatus());
            assertFalse(alertEntered.await(50, TimeUnit.MILLISECONDS));
            releaseInvocation.complete(null);
            assertTrue(alertEntered.await(2, TimeUnit.SECONDS));
            message.result().toCompletableFuture().get(2, TimeUnit.SECONDS);
        } finally {
            releaseInvocation.complete(null);
        }
    }

    @Test
    void timedOutAlertRetainsPhysicalStageAndCannotStartUnboundedReplacementWork() throws Exception {
        CompletableFuture<Void> decision = new CompletableFuture<>();
        CompletableFuture<IncomingNotification<AlertNotification>> context = new CompletableFuture<>();
        AtomicInteger invoked = new AtomicInteger();
        EndpointHandlers handlers = EndpointHandlers.builder()
                .onAlert(notification -> {
                    invoked.incrementAndGet();
                    context.complete(notification);
                    return decision;
                })
                .build();
        CompletableFuture<BoundSession> bound = new CompletableFuture<>();
        SmppClient client = new SmppClient(
                OutbindLifecycleTest.options(Duration.ofSeconds(1)),
                new ExchangeConfig(new ExchangeOptions(1, 0, 2, 4096, Duration.ofMillis(100)), handlers));
        try (SmppServer server = server(SmppVersion.V3_4, EndpointHandlers.empty(), bound)) {
            BoundSession esme = bind(client, server, SmppVersion.V3_4, BindMode.RECEIVER);
            bound.get(2, TimeUnit.SECONDS)
                    .alerts()
                    .orElseThrow()
                    .send(new AlertNotification(SOURCE, DESTINATION, EMPTY));
            IncomingNotification<AlertNotification> incoming = context.get(2, TimeUnit.SECONDS);
            esme.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertTrue(incoming.isCancelled());
            assertEquals(1, invoked.get());
            EndpointTermination result =
                    client.shutdown(Duration.ofMillis(80)).toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(1, result.remainingHandlers());
            assertFalse(result.complete());
            decision.complete(null);
        } finally {
            decision.complete(null);
            client.close();
        }
    }

    @Test
    void absentHandlerIgnoresAValidAlertAndRegistrySnapshotsRejectDuplicateOrNullHooks() throws Exception {
        EndpointHandlers.Builder builder = EndpointHandlers.builder();
        EndpointHandlers before = builder.build();
        builder.onAlert(notification -> CompletableFuture.completedFuture(null));
        assertTrue(before.alert().isEmpty());
        assertTrue(builder.build().alert().isPresent());
        assertThrows(
                IllegalArgumentException.class,
                () -> builder.onAlert(notification -> CompletableFuture.completedFuture(null)));
        assertThrows(
                NullPointerException.class, () -> EndpointHandlers.builder().onAlert(null));
        CompletableFuture<BoundSession> bound = new CompletableFuture<>();
        try (SmppServer server = server(SmppVersion.V5_0, EndpointHandlers.empty(), bound);
                SmppClient client = new SmppClient()) {
            bind(client, server, SmppVersion.V5_0, BindMode.RECEIVER);
            BoundSession mc = bound.get(2, TimeUnit.SECONDS);
            mc.alerts()
                    .orElseThrow()
                    .send(new AlertNotification(SOURCE, DESTINATION, EMPTY))
                    .result()
                    .toCompletableFuture()
                    .get(2, TimeUnit.SECONDS);
            assertEquals(
                    0,
                    mc.enquireLink()
                            .result()
                            .toCompletableFuture()
                            .get(2, TimeUnit.SECONDS)
                            .commandStatus());
        }
    }
}
