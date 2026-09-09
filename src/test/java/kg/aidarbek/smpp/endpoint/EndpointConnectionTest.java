package kg.aidarbek.smpp.endpoint;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.BindRequest;
import kg.aidarbek.smpp.protocol.ControlCommand;
import kg.aidarbek.smpp.request.BoundedNotifications;
import kg.aidarbek.smpp.request.RequestFailure;
import kg.aidarbek.smpp.request.RequestHandle;
import kg.aidarbek.smpp.request.RequestOptions;
import kg.aidarbek.smpp.request.TransmissionCertainty;
import kg.aidarbek.smpp.session.SessionState;
import kg.aidarbek.smpp.spi.FrameListener;
import kg.aidarbek.smpp.spi.FrameTransportContract;
import kg.aidarbek.smpp.spi.TransportFailure;
import kg.aidarbek.smpp.spi.WriteClass;
import kg.aidarbek.smpp.spi.WriteObserver;
import org.junit.jupiter.api.Test;

class EndpointConnectionTest {
    @Test
    void deferredFakeRejectsAtItsQueueBoundWithoutRetainingAnUnacceptedWrite() throws Exception {
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
            assertNotNull(transport.writes.poll(2, TimeUnit.SECONDS));
            transport.receive(hex("00000016800000090000000000000001000210000134"));
            BoundSession session = connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS);
            transport.deferWrites = true;
            for (int index = 0; index < 8; index++) session.enquireLink();
            var excess = session.enquireLink();
            ExecutionException error = assertThrows(
                    ExecutionException.class,
                    () -> excess.result().toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertTrue(
                    error.getCause().getCause() instanceof TransportFailure,
                    "Queue refusal must obey the transport failure contract");
            assertEquals(
                    TransportFailure.Kind.FULL,
                    ((TransportFailure) error.getCause().getCause()).kind());
            assertEquals(8, transport.deferred.size());
        } finally {
            connection.close();
            notifications.close();
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    void fakeTerminationWaitsForTheWinningCloseCallbackAndAcceptedWriteSettlement() throws Exception {
        FakeFrameTransport transport = new FakeFrameTransport();
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch closing = new CountDownLatch(1);
        CompletableFuture<Void> releaseWrite = new CompletableFuture<>();
        CompletableFuture<Void> releaseClose = new CompletableFuture<>();
        CompletableFuture<Void> writeFinished = new CompletableFuture<>();
        CompletableFuture<Void> closeFinished = new CompletableFuture<>();
        transport.start(new FrameListener() {
            @Override
            public void connected() {}

            @Override
            public void frame(byte[] frame) {}

            @Override
            public void closed(TransportFailure failure) {
                closing.countDown();
                releaseClose.join();
            }
        });
        try {
            Thread.ofPlatform().daemon().start(() -> {
                try {
                    transport.write(
                            RawPeer.header(0x15, 0, 1),
                            WriteClass.ORDINARY,
                            System.nanoTime() + TimeUnit.SECONDS.toNanos(2),
                            new WriteObserver() {
                                @Override
                                public boolean beforeWrite() {
                                    writing.countDown();
                                    releaseWrite.join();
                                    return true;
                                }

                                @Override
                                public void written() {}

                                @Override
                                public void failed(TransportFailure failure) {}
                            });
                    writeFinished.complete(null);
                } catch (Throwable failure) {
                    writeFinished.completeExceptionally(failure);
                }
            });
            assertTrue(writing.await(2, TimeUnit.SECONDS));
            Thread.ofPlatform().daemon().start(() -> {
                transport.close();
                closeFinished.complete(null);
            });
            assertTrue(closing.await(2, TimeUnit.SECONDS));
            releaseWrite.complete(null);
            writeFinished.get(2, TimeUnit.SECONDS);
            assertThrows(
                    TimeoutException.class,
                    () -> transport.termination().toCompletableFuture().get(50, TimeUnit.MILLISECONDS));
            releaseClose.complete(null);
            closeFinished.get(2, TimeUnit.SECONDS);
            transport.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
        } finally {
            releaseWrite.complete(null);
            releaseClose.complete(null);
            transport.close();
        }
    }

    @Test
    void clientBindExpiryUsesTheWinningWindowDeadlineOutcome() throws Exception {
        AtomicLong clock = new AtomicLong(System.nanoTime());
        BoundedNotifications notifications = new BoundedNotifications(8, 1);
        FakeFrameTransport transport = new FakeFrameTransport();
        EndpointConnection connection = EndpointConnection.client(
                transport,
                new ClientConfig(
                        new InetSocketAddress("127.0.0.1", 1),
                        new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x34, 0, 0, ""),
                        false),
                EndpointOptions.defaults(),
                notifications,
                clock::get);
        try {
            connection.start();
            clock.addAndGet(TimeUnit.SECONDS.toNanos(11));
            connection.tick(clock.get());
            ExecutionException failure = assertThrows(
                    ExecutionException.class,
                    () -> connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof RequestFailure, "Bind must retain the request deadline outcome");
            RequestFailure requestFailure = (RequestFailure) failure.getCause();
            assertEquals(RequestFailure.Reason.DEADLINE_EXPIRED, requestFailure.reason());
            assertEquals(TransmissionCertainty.MAY_HAVE_BEEN_SENT, requestFailure.transmission());
        } finally {
            connection.close();
            notifications.close();
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    void bindAdmissionFailureAndTransportDisconnectSettleTheObservedWorkflow() throws Exception {
        for (boolean exhaustedNotifications : new boolean[] {true, false}) {
            BoundedNotifications notifications = new BoundedNotifications(exhaustedNotifications ? 2 : 8, 1);
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
                assertDoesNotThrow(
                        connection::start, "Internal transport callbacks must settle local admission failure");
                TransportFailure disconnected = new TransportFailure(TransportFailure.Kind.EOF, false, null);
                if (!exhaustedNotifications) connection.closed(disconnected);
                ExecutionException error = assertThrows(
                        ExecutionException.class,
                        () -> connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS));
                assertTrue(error.getCause() instanceof RequestFailure);
                RequestFailure failure = (RequestFailure) error.getCause();
                assertEquals(
                        exhaustedNotifications
                                ? RequestFailure.Reason.NOTIFICATION_BACKLOG
                                : RequestFailure.Reason.DISCONNECTED,
                        failure.reason());
                assertEquals(
                        exhaustedNotifications
                                ? TransmissionCertainty.NOT_SENT
                                : TransmissionCertainty.MAY_HAVE_BEEN_SENT,
                        failure.transmission());
                if (!exhaustedNotifications) assertEquals(disconnected, failure.getCause());
                assertEquals(SessionState.CLOSED, connection.state());
            } finally {
                connection.close();
                notifications.close();
                assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
            }
        }
    }

    @Test
    void aResponseCannotSettleAQueuedBindOrControlBeforeItsWriteGuard() throws Exception {
        BoundedNotifications notifications = new BoundedNotifications(16, 1);
        FakeFrameTransport transport = new FakeFrameTransport();
        transport.deferWrites = true;
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
            byte[] bindResponse = hex("00000016800000090000000000000001000210000134");
            transport.receive(bindResponse);
            assertEquals(SessionState.BINDING, connection.state(), "An unsent bind cannot establish a session");
            transport.deferred.remove().send();
            assertNotNull(transport.writes.poll(2, TimeUnit.SECONDS));
            transport.receive(bindResponse);
            BoundSession session = connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS);
            RequestHandle<ControlCommand> enquiry = session.enquireLink();
            byte[] enquiryResponse = hex("00000010800000150000000000000002");
            transport.receive(enquiryResponse);
            assertTrue(!enquiry.isDone(), "An unsent enquiry cannot have a wire response");
            transport.deferred.remove().send();
            assertNotNull(transport.writes.poll(2, TimeUnit.SECONDS));
            transport.receive(enquiryResponse);
            assertEquals(
                    0,
                    enquiry.result()
                            .toCompletableFuture()
                            .get(2, TimeUnit.SECONDS)
                            .commandStatus());
        } finally {
            connection.close();
            notifications.close();
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    void controlInvocationBudgetIncludesWaitingForTheConnectionOwner() throws Exception {
        AtomicLong clock = new AtomicLong(System.nanoTime());
        BoundedNotifications notifications = new BoundedNotifications(8, 1);
        FakeFrameTransport transport = new FakeFrameTransport();
        EndpointConnection connection = EndpointConnection.client(
                transport,
                new ClientConfig(
                        new InetSocketAddress("127.0.0.1", 1),
                        new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x34, 0, 0, ""),
                        false),
                EndpointOptions.defaults(),
                notifications,
                clock::get);
        try {
            connection.start();
            assertNotNull(transport.writes.poll(2, TimeUnit.SECONDS));
            transport.receive(hex("00000016800000090000000000000001000210000134"));
            BoundSession session = connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS);
            CompletableFuture<Throwable> result = new CompletableFuture<>();
            Thread invocation = Thread.ofPlatform().daemon().unstarted(() -> {
                try {
                    session.enquireLink(RequestOptions.timeout(Duration.ofMillis(10)));
                    result.complete(null);
                } catch (RuntimeException failure) {
                    result.complete(failure);
                }
            });
            connection.coordination.lock();
            try {
                invocation.start();
                long waitBound = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
                while (!connection.coordination.hasQueuedThread(invocation) && System.nanoTime() - waitBound < 0)
                    Thread.onSpinWait();
                assertTrue(connection.coordination.hasQueuedThread(invocation));
                clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(11));
            } finally {
                connection.coordination.unlock();
            }
            Throwable failure = result.get(2, TimeUnit.SECONDS);
            assertTrue(failure instanceof RequestFailure, "Lock waiting must consume the original request budget");
            assertEquals(RequestFailure.Reason.DEADLINE_EXPIRED, ((RequestFailure) failure).reason());
            assertEquals(TransmissionCertainty.NOT_SENT, ((RequestFailure) failure).transmission());
            assertTrue(transport.writes.isEmpty());
        } finally {
            connection.close();
            notifications.close();
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    void failedInitialBindWritePreservesWinningRequestFailureAndTransmissionKnowledge() throws Exception {
        for (boolean afterGuard : new boolean[] {false, true}) {
            BoundedNotifications notifications = new BoundedNotifications(8, 1);
            FakeFrameTransport transport = new FakeFrameTransport();
            TransportFailure writeFailure = new TransportFailure(
                    afterGuard ? TransportFailure.Kind.WRITE_FAILED : TransportFailure.Kind.REJECTED, afterGuard, null);
            transport.writeFailure = writeFailure;
            transport.failAfterGuard = afterGuard;
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
                ExecutionException error = assertThrows(
                        ExecutionException.class,
                        () -> connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS));
                assertTrue(
                        error.getCause() instanceof RequestFailure,
                        "Binding must publish its window's structured failure");
                RequestFailure failure = (RequestFailure) error.getCause();
                assertEquals(RequestFailure.Reason.WRITE_FAILED, failure.reason());
                assertEquals(
                        afterGuard ? TransmissionCertainty.MAY_HAVE_BEEN_SENT : TransmissionCertainty.NOT_SENT,
                        failure.transmission());
                assertEquals(writeFailure, failure.getCause());
                assertTrue(transport.writes.isEmpty());
            } finally {
                connection.close();
                notifications.close();
                assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
            }
        }
    }

    @Test
    void cancellationBeforeTransportStartNeverWritesABind() throws Exception {
        BoundedNotifications notifications = new BoundedNotifications(8, 1);
        FakeFrameTransport transport = new FakeFrameTransport();
        ClientConfig config = new ClientConfig(
                new InetSocketAddress("127.0.0.1", 1),
                new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x34, 0, 0, ""),
                false);
        EndpointConnection connection =
                EndpointConnection.client(transport, config, EndpointOptions.defaults(), notifications);
        try {
            assertTrue(connection.cancelBind());
            assertEquals(SessionState.CLOSED, connection.state());
            assertThrows(
                    ExecutionException.class,
                    () -> connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS));
            connection.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertTrue(transport.writes.isEmpty());
        } finally {
            connection.close();
            notifications.close();
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    void immediateFakeSharesTheRealTransportOwnershipAndLifecycleContract() throws Exception {
        FakeFrameTransport transport = new FakeFrameTransport();
        try {
            FrameTransportContract.verify(
                    transport, transport::receive, () -> transport.writes.poll(3, TimeUnit.SECONDS));
        } finally {
            transport.close();
        }
    }

    @Test
    void prebindMessageAndUnknownRequestReceiveDistinctNegativesWhileMalformedResponseOnlyCloses() throws Exception {
        BoundedNotifications notifications = new BoundedNotifications(8, 1);
        AuthenticationDispatcher authentication = new AuthenticationDispatcher(1, 1, null);
        FakeFrameTransport transport = new FakeFrameTransport();
        ServerConfig config = new ServerConfig(
                new InetSocketAddress("127.0.0.1", 0), Set.of(SmppVersion.V3_4), SmppVersion.V3_4, "mc", 1, 1);
        EndpointConnection connection = EndpointConnection.server(
                transport,
                new InetSocketAddress("127.0.0.1", 1),
                config,
                EndpointOptions.defaults(),
                notifications,
                authentication,
                (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                ignored -> {});
        try {
            connection.start();
            transport.receive(hex("00000021000000040000000000000011" + "00".repeat(17)));
            assertArrayEquals(hex("00000010800000040000000400000011"), transport.writes.poll(2, TimeUnit.SECONDS));
            assertDoesNotThrow(() -> transport.receive(hex("00000010123456780000000000000012")));
            assertArrayEquals(hex("00000010800000000000000300000012"), transport.writes.poll(2, TimeUnit.SECONDS));
            assertDoesNotThrow(() -> transport.receive(hex("00000011800000090000000000000001ff")));
            connection.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertTrue(transport.writes.isEmpty());
            assertEquals(SessionState.CLOSED, connection.state());
        } finally {
            connection.close();
            authentication.close();
            notifications.close();
            assertTrue(authentication.awaitTermination(Duration.ofSeconds(2)));
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    void acceptedIdleAndBlockedAuthenticationExpireWithoutResurrectionOrReleasedInvocationCapacity() throws Exception {
        for (boolean sendBind : new boolean[] {false, true}) {
            BoundedNotifications notifications = new BoundedNotifications(8, 1);
            AuthenticationDispatcher authentication = new AuthenticationDispatcher(1, 1, null);
            FakeFrameTransport transport = new FakeFrameTransport();
            CompletableFuture<BindDecision> decision = new CompletableFuture<>();
            CountDownLatch invoked = new CountDownLatch(1);
            AtomicInteger bound = new AtomicInteger();
            ServerConfig config = new ServerConfig(
                    new InetSocketAddress("127.0.0.1", 0), Set.of(SmppVersion.V3_4), SmppVersion.V3_4, "mc", 1, 1);
            EndpointConnection connection = EndpointConnection.server(
                    transport,
                    new InetSocketAddress("127.0.0.1", 1),
                    config,
                    EndpointOptions.defaults(),
                    notifications,
                    authentication,
                    (request, peer) -> {
                        invoked.countDown();
                        return decision;
                    },
                    ignored -> bound.incrementAndGet());
            try {
                connection.start();
                if (sendBind) {
                    transport.receive(hex("0000001700000009000000000000001700000034000000"));
                    assertTrue(invoked.await(2, TimeUnit.SECONDS));
                }
                connection.tick(System.nanoTime() + Duration.ofSeconds(11).toNanos());
                assertEquals(SessionState.CLOSED, connection.state());
                connection.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
                assertEquals(sendBind ? 1 : 0, authentication.outstanding());
                decision.complete(BindDecision.ACCEPT);
                authentication.close();
                assertTrue(authentication.awaitTermination(Duration.ofSeconds(2)));
                assertTrue(transport.writes.isEmpty());
                assertEquals(0, bound.get());
            } finally {
                decision.complete(BindDecision.ACCEPT);
                connection.close();
                authentication.close();
                notifications.close();
                assertTrue(authentication.awaitTermination(Duration.ofSeconds(2)));
                assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
            }
        }
    }

    @Test
    void clientReportsNegativeAndUnsupportedVersionOutcomesWithoutLosingRawStatusOrAdvertisement() throws Exception {
        for (boolean negative : new boolean[] {false, true}) {
            BoundedNotifications notifications = new BoundedNotifications(8, 1);
            FakeFrameTransport transport = new FakeFrameTransport();
            ClientConfig config = new ClientConfig(
                    new InetSocketAddress("127.0.0.1", 1),
                    new BindRequest(BindMode.TRANSCEIVER, "", "", "", 0x50, 0, 0, ""),
                    false);
            EndpointConnection connection =
                    EndpointConnection.client(transport, config, EndpointOptions.defaults(), notifications);
            try {
                connection.start();
                assertNotNull(transport.writes.poll(2, TimeUnit.SECONDS));
                transport.receive(hex(
                        negative
                                ? "00000010800000090000000e00000001"
                                : "000000188000000900000000000000016d63000210000160"));
                ExecutionException rejected = assertThrows(
                        ExecutionException.class,
                        () -> connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS));
                EndpointException failure = (EndpointException) rejected.getCause();
                assertEquals(
                        negative ? EndpointException.Reason.BIND_REJECTED : EndpointException.Reason.VERSION_REJECTED,
                        failure.reason());
                if (negative) assertEquals(0x0e, failure.commandStatus().orElseThrow());
                else assertEquals(0x60, failure.advertisement().orElseThrow());
                assertEquals(SessionState.CLOSED, connection.state());
            } finally {
                connection.close();
                notifications.close();
                assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
            }
        }
    }

    @Test
    void serverRejectsUnacceptedRequestedVersionBeforeAuthenticationAndClosesAfterReply() throws Exception {
        BoundedNotifications notifications = new BoundedNotifications(8, 1);
        AuthenticationDispatcher authentication = new AuthenticationDispatcher(1, 1, null);
        FakeFrameTransport transport = new FakeFrameTransport();
        AtomicInteger authentications = new AtomicInteger();
        ServerConfig config = new ServerConfig(
                new InetSocketAddress("127.0.0.1", 0), Set.of(SmppVersion.V3_4), SmppVersion.V3_4, "mc", 1, 1);
        EndpointConnection connection = EndpointConnection.server(
                transport,
                new InetSocketAddress("127.0.0.1", 1),
                config,
                EndpointOptions.defaults(),
                notifications,
                authentication,
                (request, peer) -> {
                    authentications.incrementAndGet();
                    return CompletableFuture.completedFuture(BindDecision.ACCEPT);
                },
                ignored -> {});
        try {
            connection.start();
            transport.receive(hex("0000001700000009000000000000001700000050000000"));
            assertArrayEquals(hex("00000010800000090000000d00000017"), transport.writes.poll(2, TimeUnit.SECONDS));
            connection.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(0, authentications.get());
            assertEquals(SessionState.CLOSED, connection.state());
        } finally {
            connection.close();
            authentication.close();
            notifications.close();
            assertTrue(authentication.awaitTermination(Duration.ofSeconds(2)));
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    void clientBindsThroughRequestWindowAndKeepsRequestedVersionSeparateFromAdvertisement() throws Exception {
        BoundedNotifications notifications = new BoundedNotifications(16, 1);
        FakeFrameTransport transport = new FakeFrameTransport();
        ClientConfig config = new ClientConfig(
                new InetSocketAddress("127.0.0.1", 1),
                new BindRequest(BindMode.TRANSMITTER, "", "", "", 0x34, 0, 0, ""),
                false);
        EndpointConnection connection =
                EndpointConnection.client(transport, config, EndpointOptions.defaults(), notifications);
        try {
            connection.start();
            assertArrayEquals(
                    hex("0000001700000002000000000000000100000034000000"), transport.writes.poll(2, TimeUnit.SECONDS));
            transport.receive(hex("000000188000000200000000000000016d63000210000150"));
            BoundSession session = connection.bound().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(SessionState.BOUND_TX, session.state());
            assertEquals(
                    SmppVersion.V3_4,
                    session.negotiation().effectiveProfile().orElseThrow().version());
            assertEquals(0x50, session.negotiation().advertisement().orElseThrow());
            RequestHandle<ControlCommand> enquiry = session.enquireLink();
            assertEquals(2, enquiry.identity().sequenceNumber());
            session.close();
            assertTrue(enquiry.isDone());
            session.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
        } finally {
            connection.close();
            notifications.close();
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    void serverControlsKeepPeerRequestNamespaceSeparateAndFinishCrossedUnbinds() throws Exception {
        BoundedNotifications notifications = new BoundedNotifications(16, 1);
        AuthenticationDispatcher authentication = new AuthenticationDispatcher(1, 1, null);
        FakeFrameTransport transport = new FakeFrameTransport();
        CompletableFuture<BoundSession> bound = new CompletableFuture<>();
        ServerConfig config = new ServerConfig(
                new InetSocketAddress("127.0.0.1", 0), Set.of(SmppVersion.V3_4), SmppVersion.V3_4, "mc", 1, 1);
        EndpointConnection connection = EndpointConnection.server(
                transport,
                new InetSocketAddress("127.0.0.1", 1),
                config,
                EndpointOptions.defaults(),
                notifications,
                authentication,
                (request, peer) -> CompletableFuture.completedFuture(BindDecision.ACCEPT),
                bound::complete);
        try {
            connection.start();
            transport.receive(hex("0000001700000009000000000000000100000034000000"));
            assertNotNull(transport.writes.poll(2, TimeUnit.SECONDS));
            BoundSession session = bound.get(2, TimeUnit.SECONDS);
            RequestHandle<ControlCommand> enquiry = session.enquireLink();
            assertNotNull(enquiry, "A bound server exposes the real request mechanism");
            assertArrayEquals(hex("00000010000000150000000000000001"), transport.writes.poll(2, TimeUnit.SECONDS));
            transport.receive(hex("00000010000000150000000000000001"));
            assertArrayEquals(hex("00000010800000150000000000000001"), transport.writes.poll(2, TimeUnit.SECONDS));
            assertTrue(!enquiry.isDone());
            transport.receive(hex("00000010800000150000000000000001"));
            assertEquals(
                    0,
                    enquiry.result()
                            .toCompletableFuture()
                            .get(2, TimeUnit.SECONDS)
                            .commandStatus());
            RequestHandle<ControlCommand> unbind = session.unbind();
            assertArrayEquals(hex("00000010000000060000000000000002"), transport.writes.poll(2, TimeUnit.SECONDS));
            transport.receive(hex("00000010000000060000000000000002"));
            assertArrayEquals(hex("00000010800000060000000000000002"), transport.writes.poll(2, TimeUnit.SECONDS));
            assertEquals(SessionState.UNBINDING, session.state());
            transport.receive(hex("00000010800000060000000000000002"));
            assertEquals(
                    0,
                    unbind.result()
                            .toCompletableFuture()
                            .get(2, TimeUnit.SECONDS)
                            .commandStatus());
            session.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(SessionState.CLOSED, session.state());
        } finally {
            connection.close();
            authentication.close();
            notifications.close();
            assertTrue(authentication.awaitTermination(Duration.ofSeconds(2)));
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    @Test
    void serverAuthenticatesRawBindAndWritesAdvertisedSuccessBeforeNotifyingApplication() throws Exception {
        BoundedNotifications notifications = new BoundedNotifications(8, 1);
        AuthenticationDispatcher authentication = new AuthenticationDispatcher(1, 1, null);
        FakeFrameTransport transport = new FakeFrameTransport();
        CompletableFuture<BoundSession> bound = new CompletableFuture<>();
        ServerConfig config = new ServerConfig(
                new InetSocketAddress("127.0.0.1", 0),
                Set.of(SmppVersion.V3_4, SmppVersion.V5_0),
                SmppVersion.V5_0,
                "mc",
                1,
                1);
        EndpointConnection connection = EndpointConnection.server(
                transport,
                new InetSocketAddress("127.0.0.1", 1),
                config,
                EndpointOptions.defaults(),
                notifications,
                authentication,
                (request, peer) -> {
                    assertEquals(0x34, request.interfaceVersion());
                    return CompletableFuture.completedFuture(BindDecision.ACCEPT);
                },
                bound::complete);
        try {
            connection.start();
            transport.receive(hex("0000001700000009000000000000001700000034000000"));
            assertArrayEquals(
                    hex("000000188000000900000000000000176d63000210000150"),
                    transport.writes.poll(2, TimeUnit.SECONDS));
            BoundSession session = bound.get(2, TimeUnit.SECONDS);
            assertEquals(SessionState.BOUND_TRX, session.state());
            assertEquals(
                    SmppVersion.V3_4,
                    session.negotiation().effectiveProfile().orElseThrow().version());
            session.close();
            session.termination().toCompletableFuture().get(2, TimeUnit.SECONDS);
            assertEquals(SessionState.CLOSED, session.state());
        } finally {
            connection.close();
            authentication.close();
            notifications.close();
            assertTrue(authentication.awaitTermination(Duration.ofSeconds(2)));
            assertTrue(notifications.awaitTermination(Duration.ofSeconds(2)));
        }
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }
}
