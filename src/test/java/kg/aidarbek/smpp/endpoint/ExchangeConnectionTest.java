package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.DeliverSm;
import kg.aidarbek.smpp.protocol.DeliverSmResponse;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.QuerySm;
import kg.aidarbek.smpp.request.BoundedNotifications;
import kg.aidarbek.smpp.session.SessionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Controlled frame-port tests cover application budgets, ordered ownership and transport contention. */
class ExchangeConnectionTest {
    @Test
    void theFramePortStillOwnsADequeuedWriteUntilItsGuardOrCancellationSettles() throws Exception {
        BoundedNotifications notifications = new BoundedNotifications(16, 1);
        FakeFrameTransport transport = new FakeFrameTransport();
        EndpointConnection connection = EndpointConnection.client(
                transport,
                new ClientConfig(
                        new InetSocketAddress("127.0.0.1", 1),
                        new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x34, 0, 0, ""),
                        false),
                EndpointOptions.defaults(),
                notifications);
        FakeFrameTransport.PendingWrite dequeued = null;
        try {
            connection.start();
            transport.writes.remove();
            transport.receive(hex("00000016800000090000000000000001000210000134"));
            BoundSession session = connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS);
            transport.deferWrites = true;
            session.enquireLink();
            dequeued = transport.deferred.remove();
            connection.close();
            transport.termination().toCompletableFuture().get(1, TimeUnit.SECONDS);
            dequeued.send();
            assertTrue(transport.writes.isEmpty());
        } finally {
            connection.close();
            if (dequeued != null) dequeued.cancel();
            notifications.close();
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    void controlReserveStillHasItsOwnFiniteBoundWhenOrdinaryRequestsFillTheirQueue() throws Exception {
        BoundedNotifications notifications = new BoundedNotifications(32, 1);
        FakeFrameTransport transport = new FakeFrameTransport();
        EndpointConnection connection = EndpointConnection.client(
                transport,
                new ClientConfig(
                        new InetSocketAddress("127.0.0.1", 1),
                        new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x34, 0, 0, ""),
                        false),
                EndpointOptions.defaults(),
                notifications);
        try {
            connection.start();
            transport.writes.remove();
            transport.receive(hex("00000016800000090000000000000001000210000134"));
            BoundSession session = connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS);
            transport.deferWrites = true;
            for (int index = 0; index < 8; index++)
                session.query()
                        .orElseThrow()
                        .send(new QuerySm(
                                "id", CommonOperationsEndpointTest.SOURCE, CommonOperationsEndpointTest.EMPTY));
            for (int index = 0; index < 8; index++) transport.receive(RawPeer.header(0x15, 0, index + 1));
            assertEquals(
                    SessionState.BOUND_TRX,
                    connection.state(),
                    "Control response capacity is independent of full ordinary capacity");
            assertEquals(16, transport.deferred.size());
            transport.receive(RawPeer.header(0x15, 0, 9));
            assertEquals(
                    SessionState.CLOSED,
                    connection.state(),
                    "Control reserve is finite and cannot retain a ninth reply");
            assertTrue(transport.deferred.isEmpty());
            assertTrue(transport.writes.isEmpty());
        } finally {
            connection.close();
            notifications.close();
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    void inlineWriteCompletionDrainsALargeReadyReplyBacklogWithoutRecursiveStackGrowth() throws Exception {
        int count = 6000;
        AtomicInteger invoked = new AtomicInteger();
        BoundedNotifications notifications = new BoundedNotifications(16, 1);
        HandlerDispatcher handlers = new HandlerDispatcher(2, count);
        FakeFrameTransport transport = new FakeFrameTransport();
        CompletableFuture<HandlerResponse<DeliverSmResponse>> first = new CompletableFuture<>();
        HandlerResponse<DeliverSmResponse> accepted = new HandlerResponse<>(
                0, new DeliverSmResponse(new MessageResponse(Optional.of(""), EndpointPdus.NO_PARAMETERS)));
        EndpointHandlers registry = EndpointHandlers.builder()
                .on(MessageOperations.DELIVER_SM, incoming -> {
                    invoked.incrementAndGet();
                    return incoming.pdu().sequenceNumber() == 2 ? first : CompletableFuture.completedFuture(accepted);
                })
                .build();
        EndpointConnection connection = EndpointConnection.client(
                transport,
                new ClientConfig(
                        new InetSocketAddress("127.0.0.1", 1),
                        new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x34, 0, 0, ""),
                        false),
                EndpointOptions.defaults(),
                notifications,
                handlers,
                new ExchangeConfig(new ExchangeOptions(2, count, count, 1_048_576, Duration.ofSeconds(30)), registry));
        try {
            connection.start();
            transport.writes.remove();
            transport.receive(hex("00000016800000090000000000000001000210000134"));
            connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS);
            transport.discardWrittenFrames = true;
            for (int index = 0; index < count; index++) {
                byte[] request = hex("00000021000000050000000000000000" + "00".repeat(17));
                ByteBuffer.wrap(request).putInt(12, index + 2);
                transport.receive(request);
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while ((invoked.get() != count || handlers.outstanding() != 1) && System.nanoTime() - deadline < 0)
                Thread.onSpinWait();
            assertEquals(count, invoked.get());
            assertEquals(1, handlers.outstanding());
            first.complete(accepted);
            assertEquals(
                    count + 1,
                    transport.writtenFrames.get(),
                    "Inline transport callbacks must iteratively drain every ready response");
        } finally {
            first.complete(accepted);
            connection.close();
            handlers.close();
            notifications.close();
            assertTrue(handlers.awaitTermination(Duration.ofSeconds(2)));
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ordinaryTransportSaturationRetainsOneReplyAndItsOriginalWriteDeadline(boolean expire) throws Exception {
        AtomicLong clock = new AtomicLong(System.nanoTime());
        BoundedNotifications notifications = new BoundedNotifications(16, 1);
        HandlerDispatcher handlers = new HandlerDispatcher(1, 1);
        FakeFrameTransport transport = new FakeFrameTransport();
        CompletableFuture<HandlerResponse<DeliverSmResponse>> decision = new CompletableFuture<>();
        EndpointHandlers registry = EndpointHandlers.builder()
                .on(MessageOperations.DELIVER_SM, incoming -> decision)
                .build();
        EndpointConnection connection = EndpointConnection.client(
                transport,
                new ClientConfig(
                        new InetSocketAddress("127.0.0.1", 1),
                        new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x34, 0, 0, ""),
                        false),
                EndpointOptions.defaults(),
                notifications,
                handlers,
                new ExchangeConfig(ExchangeOptions.defaults(), registry),
                clock::get);
        try {
            connection.start();
            transport.writes.remove();
            transport.receive(hex("00000016800000090000000000000001000210000134"));
            BoundSession session = connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS);
            transport.receive(hex("00000021000000050000000000000007" + "00".repeat(17)));
            long bound = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (decision.getNumberOfDependents() == 0 && System.nanoTime() - bound < 0) Thread.onSpinWait();
            assertTrue(decision.getNumberOfDependents() > 0);
            transport.deferWrites = true;
            for (int index = 0; index < 8; index++)
                session.query()
                        .orElseThrow()
                        .send(new QuerySm(
                                "id", CommonOperationsEndpointTest.SOURCE, CommonOperationsEndpointTest.EMPTY));
            assertEquals(8, transport.deferred.size());
            decision.complete(new HandlerResponse<>(
                    0, new DeliverSmResponse(new MessageResponse(Optional.of(""), EndpointPdus.NO_PARAMETERS))));
            assertEquals(
                    SessionState.BOUND_TRX,
                    connection.state(),
                    "A bounded queued reply must survive transient ordinary queue saturation");
            assertTrue(transport.writes.isEmpty());
            while (!transport.deferred.isEmpty()) {
                transport.deferred.remove().send();
                assertEquals(3, RawPeer.command(transport.writes.remove()));
            }
            transport.deferWrites = false;
            if (expire) clock.addAndGet(TimeUnit.SECONDS.toNanos(11));
            connection.tick(clock.get());
            if (expire) {
                assertEquals(SessionState.CLOSED, connection.state());
                assertTrue(transport.writes.isEmpty(), "Retries cannot refresh the reply's write deadline");
            } else {
                assertEquals(7, RawPeer.sequence(transport.writes.poll(2, TimeUnit.SECONDS)));
                assertTrue(transport.writes.isEmpty());
            }
        } finally {
            decision.completeExceptionally(new IllegalStateException("test cleanup"));
            connection.close();
            handlers.close();
            notifications.close();
            assertTrue(handlers.awaitTermination(Duration.ofSeconds(2)));
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    void anExpiredQueuedHandlerCannotStartWhenAnEarlierStageReleasesCapacity() throws Exception {
        AtomicLong clock = new AtomicLong(System.nanoTime());
        AtomicInteger calls = new AtomicInteger();
        BoundedNotifications notifications = new BoundedNotifications(16, 1);
        HandlerDispatcher handlers = new HandlerDispatcher(1, 1);
        FakeFrameTransport transport = new FakeFrameTransport();
        CompletableFuture<HandlerResponse<DeliverSmResponse>> first = new CompletableFuture<>();
        EndpointHandlers registry = EndpointHandlers.builder()
                .on(MessageOperations.DELIVER_SM, incoming -> {
                    if (calls.incrementAndGet() == 1) return first;
                    return CompletableFuture.completedFuture(new HandlerResponse<>(
                            0,
                            new DeliverSmResponse(new MessageResponse(Optional.of(""), EndpointPdus.NO_PARAMETERS))));
                })
                .build();
        EndpointConnection connection = EndpointConnection.client(
                transport,
                new ClientConfig(
                        new InetSocketAddress("127.0.0.1", 1),
                        new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x34, 0, 0, ""),
                        false),
                EndpointOptions.defaults(),
                notifications,
                handlers,
                new ExchangeConfig(new ExchangeOptions(1, 1, 2, 1024, Duration.ofNanos(100)), registry),
                clock::get);
        try {
            connection.start();
            transport.writes.remove();
            transport.receive(hex("00000016800000090000000000000001000210000134"));
            connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS);
            transport.receive(hex("00000021000000050000000000000007" + "00".repeat(17)));
            long registrationBound = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (first.getNumberOfDependents() == 0 && System.nanoTime() - registrationBound < 0) Thread.onSpinWait();
            assertTrue(first.getNumberOfDependents() > 0);
            transport.receive(hex("00000021000000050000000000000008" + "00".repeat(17)));
            clock.addAndGet(101);
            first.complete(new HandlerResponse<>(
                    0, new DeliverSmResponse(new MessageResponse(Optional.of(""), EndpointPdus.NO_PARAMETERS))));
            assertEquals(8, RawPeer.status(transport.writes.poll(2, TimeUnit.SECONDS)));
            assertEquals(8, RawPeer.status(transport.writes.poll(2, TimeUnit.SECONDS)));
            assertEquals(1, calls.get(), "The handler queue cannot grant an expired invocation a fresh start");
        } finally {
            first.completeExceptionally(new IllegalStateException("test cleanup"));
            connection.close();
            handlers.close();
            notifications.close();
            assertTrue(handlers.awaitTermination(Duration.ofSeconds(2)));
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    void handlerDeadlineIncludesWaitingToEnterTheConnectionOwner() throws Exception {
        AtomicLong clock = new AtomicLong(System.nanoTime());
        AtomicInteger calls = new AtomicInteger();
        BoundedNotifications notifications = new BoundedNotifications(16, 1);
        HandlerDispatcher handlers = new HandlerDispatcher(1, 1);
        FakeFrameTransport transport = new FakeFrameTransport();
        EndpointHandlers registry = EndpointHandlers.builder()
                .on(MessageOperations.DELIVER_SM, incoming -> {
                    calls.incrementAndGet();
                    return CompletableFuture.completedFuture(new HandlerResponse<>(
                            0,
                            new DeliverSmResponse(new MessageResponse(Optional.of(""), EndpointPdus.NO_PARAMETERS))));
                })
                .build();
        EndpointConnection connection = EndpointConnection.client(
                transport,
                new ClientConfig(
                        new InetSocketAddress("127.0.0.1", 1),
                        new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x34, 0, 0, ""),
                        false),
                EndpointOptions.defaults(),
                notifications,
                handlers,
                new ExchangeConfig(new ExchangeOptions(1, 1, 2, 1024, Duration.ofNanos(100)), registry),
                clock::get);
        try {
            connection.start();
            transport.writes.remove();
            transport.receive(hex("00000016800000090000000000000001000210000134"));
            connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS);
            Thread delivery = Thread.ofPlatform()
                    .daemon()
                    .unstarted(() -> transport.receive(hex("00000021000000050000000000000007" + "00".repeat(17))));
            synchronized (connection) {
                delivery.start();
                long bound = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (delivery.getState() != Thread.State.BLOCKED && System.nanoTime() - bound < 0)
                    Thread.onSpinWait();
                assertEquals(Thread.State.BLOCKED, delivery.getState());
                clock.addAndGet(101);
            }
            byte[] expired = transport.writes.poll(2, TimeUnit.SECONDS);
            assertNotNull(expired);
            assertEquals(8, RawPeer.status(expired), "Complete frame arrival must start the handler budget");
            assertEquals(0, calls.get(), "An already expired callback must never start");
        } finally {
            connection.close();
            handlers.close();
            notifications.close();
            assertTrue(handlers.awaitTermination(Duration.ofSeconds(2)));
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aReplyRetainsItsOrderedSlotUntilTheTransportSettlesTheWrite(boolean activeWrite) throws Exception {
        BoundedNotifications notifications = new BoundedNotifications(16, 1);
        HandlerDispatcher handlers = new HandlerDispatcher(1, 1);
        FakeFrameTransport transport = new FakeFrameTransport();
        CompletableFuture<Void> releaseWriter = new CompletableFuture<>();
        CompletableFuture<Void> writerFinished = new CompletableFuture<>();
        CountDownLatch beforeWritten = new CountDownLatch(1);
        boolean writerStarted = false;
        EndpointHandlers registry = EndpointHandlers.builder()
                .on(
                        MessageOperations.DELIVER_SM,
                        incoming -> CompletableFuture.completedFuture(new HandlerResponse<>(
                                0,
                                new DeliverSmResponse(
                                        new MessageResponse(Optional.of(""), EndpointPdus.NO_PARAMETERS)))))
                .build();
        EndpointConnection connection = EndpointConnection.client(
                transport,
                new ClientConfig(
                        new InetSocketAddress("127.0.0.1", 1),
                        new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x34, 0, 0, ""),
                        false),
                EndpointOptions.defaults(),
                notifications,
                handlers,
                new ExchangeConfig(new ExchangeOptions(1, 1, 1, 1024, Duration.ofSeconds(2)), registry));
        try {
            connection.start();
            transport.writes.remove();
            transport.receive(hex("00000016800000090000000000000001000210000134"));
            connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS);
            transport.deferWrites = true;
            transport.receive(hex("00000021000000050000000000000007" + "00".repeat(17)));
            var reply = transport.deferred.poll(2, TimeUnit.SECONDS);
            assertNotNull(reply);
            if (activeWrite) {
                transport.beforeWritten = () -> {
                    beforeWritten.countDown();
                    releaseWriter.join();
                };
                writerStarted = true;
                Thread.ofPlatform().daemon().start(() -> {
                    try {
                        reply.send();
                        writerFinished.complete(null);
                    } catch (Throwable failure) {
                        writerFinished.completeExceptionally(failure);
                    }
                });
                assertTrue(beforeWritten.await(2, TimeUnit.SECONDS));
                assertEquals(7, RawPeer.sequence(transport.writes.remove()));
            } else transport.deferred.add(reply);
            transport.receive(hex("00000021000000050000000000000008" + "00".repeat(17)));
            assertEquals(
                    SessionState.CLOSED,
                    connection.state(),
                    "A queued/active response must retain the sole ordered response slot");
            if (!activeWrite) reply.send();
            assertTrue(transport.writes.isEmpty());
        } finally {
            releaseWriter.complete(null);
            connection.close();
            if (writerStarted) writerFinished.get(2, TimeUnit.SECONDS);
            handlers.close();
            notifications.close();
            assertTrue(handlers.awaitTermination(Duration.ofSeconds(2)));
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    void handlerExpiryRepliesOnceAndRetainsTheUnfinishedApplicationCapacity() throws Exception {
        AtomicLong clock = new AtomicLong(System.nanoTime());
        BoundedNotifications notifications = new BoundedNotifications(16, 1);
        HandlerDispatcher handlers = new HandlerDispatcher(1, 0);
        FakeFrameTransport transport = new FakeFrameTransport();
        CompletableFuture<HandlerResponse<DeliverSmResponse>> decision = new CompletableFuture<>();
        CompletableFuture<IncomingRequest<DeliverSm>> invoked = new CompletableFuture<>();
        EndpointHandlers registry = EndpointHandlers.builder()
                .on(MessageOperations.DELIVER_SM, incoming -> {
                    invoked.complete(incoming);
                    return decision;
                })
                .build();
        EndpointConnection connection = EndpointConnection.client(
                transport,
                new ClientConfig(
                        new InetSocketAddress("127.0.0.1", 1),
                        new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x34, 0, 0, ""),
                        false),
                EndpointOptions.defaults(),
                notifications,
                handlers,
                new ExchangeConfig(new ExchangeOptions(1, 0, 3, 1024, Duration.ofNanos(100)), registry),
                clock::get);
        try {
            connection.start();
            transport.writes.remove();
            transport.receive(hex("00000016800000090000000000000001000210000134"));
            connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS);
            transport.receive(hex("00000021000000050000000000000007" + "00".repeat(17)));
            IncomingRequest<?> incoming = invoked.get(2, TimeUnit.SECONDS);
            clock.addAndGet(101);
            connection.tick(clock.get());
            byte[] expired = transport.writes.poll(2, TimeUnit.SECONDS);
            assertNotNull(expired, "Handler deadline must settle without waiting for its application stage");
            assertEquals(8, RawPeer.status(expired));
            assertEquals(7, RawPeer.sequence(expired));
            assertTrue(incoming.isCancelled());
            assertEquals(1, handlers.outstanding(), "Logical timeout must retain physical asynchronous capacity");
            transport.receive(hex("00000021000000050000000000000008" + "00".repeat(17)));
            assertEquals(0x58, RawPeer.status(transport.writes.poll(2, TimeUnit.SECONDS)));
            decision.complete(new HandlerResponse<>(
                    0, new DeliverSmResponse(new MessageResponse(Optional.of("late"), EndpointPdus.NO_PARAMETERS))));
            handlers.close();
            assertTrue(handlers.awaitTermination(Duration.ofSeconds(2)));
            assertTrue(transport.writes.isEmpty(), "Late success cannot send a second response");
        } finally {
            decision.completeExceptionally(new IllegalStateException("test cleanup"));
            connection.close();
            handlers.close();
            notifications.close();
            assertTrue(handlers.awaitTermination(Duration.ofSeconds(2)));
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }
}
