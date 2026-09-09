package kg.aidarbek.smpp.request;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import kg.aidarbek.smpp.protocol.BindResponse;
import kg.aidarbek.smpp.protocol.ControlCommand;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class RequestWindowTest {
    private static final UUID GENERATION = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final RequestOptions OPTIONS = RequestOptions.timeout(Duration.ofNanos(100));

    @Test
    void ownedNotificationShutdownReportsCompletionWithoutDiscardingPendingResults() throws Exception {
        RequestWindow window = new RequestWindow(GENERATION, 2, 32, 2, () -> 0);
        try {
            var first = admit(window);
            var second = admit(window);
            window.close();
            assertTrue(first.isDone());
            assertTrue(second.isDone());
            assertTrue(window.awaitNotifications(Duration.ofSeconds(2)));
            assertEquals(0, window.notificationsOutstanding());
            assertTrue(first.result().toCompletableFuture().isCompletedExceptionally());
            assertTrue(second.result().toCompletableFuture().isCompletedExceptionally());
        } finally {
            window.close();
        }
    }

    @Test
    void retainsDistinctGenerationNamespacesAndRejectsForeignWriteHandlesAndWrongResponseTypes() {
        try (RequestWindow oldWindow = new RequestWindow(GENERATION, 1, 16, 1, () -> 0);
                RequestWindow newWindow = new RequestWindow(UUID.randomUUID(), 1, 16, 1, () -> 0)) {
            var oldHandle = admit(oldWindow);
            var newHandle = admit(newWindow);
            assertEquals(
                    oldHandle.identity().sequenceNumber(), newHandle.identity().sequenceNumber());
            assertThrows(IllegalArgumentException.class, () -> newWindow.beginWrite(oldHandle));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> newWindow.failWrite(oldHandle, new RuntimeException("wrong window")));
            assertFalse(newWindow.accept(GENERATION, response(1, ControlCommand.Type.ENQUIRE_LINK_RESPONSE, 0)));
            assertTrue(oldWindow.accept(GENERATION, response(1, ControlCommand.Type.ENQUIRE_LINK_RESPONSE, 0)));
            assertFalse(newHandle.isDone());
        }
        try (RequestWindow window = new RequestWindow(GENERATION, 1, 16, 1, () -> 0)) {
            var handle = window.admit(9, 0x80000009L, ControlCommand.class, 16, OPTIONS);
            var body = new BindResponse(
                    kg.aidarbek.smpp.protocol.BindMode.TRANSCEIVER,
                    java.util.Optional.of("peer"),
                    new OptionalParameters(java.util.List.of()));
            assertFalse(window.accept(GENERATION, new Pdu<>(0, 1, body)));
            assertFalse(handle.isDone());
        }
    }

    @Test
    void validatesConstructionAndAdmissionBeforeReservingAnyCapacity() {
        try (BoundedNotifications notifications = new BoundedNotifications(2, 1)) {
            assertThrows(NullPointerException.class, () -> new RequestWindow(null, 1, 16, notifications, () -> 0));
            assertThrows(NullPointerException.class, () -> new RequestWindow(GENERATION, 1, 16, notifications, null));
            assertThrows(NullPointerException.class, () -> new RequestWindow(GENERATION, 1, 16, null, () -> 0));
            assertThrows(
                    IllegalArgumentException.class, () -> new RequestWindow(GENERATION, 0, 16, notifications, () -> 0));
            assertThrows(
                    IllegalArgumentException.class, () -> new RequestWindow(GENERATION, 1, 15, notifications, () -> 0));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new RequestWindow(GENERATION, 1, 16, notifications, () -> 0, 0));
            try (RequestWindow window = new RequestWindow(GENERATION, 1, 32, notifications, () -> 0)) {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> window.admit(0x15, 0x80000006L, ControlCommand.class, 16, OPTIONS));
                assertThrows(
                        IllegalArgumentException.class,
                        () -> window.admit(0, 0x80000000L, ControlCommand.class, 16, OPTIONS));
                assertThrows(
                        IllegalArgumentException.class,
                        () -> window.admit(0x80000015L, 0x80000015L, ControlCommand.class, 16, OPTIONS));
                assertThrows(
                        IllegalArgumentException.class,
                        () -> window.admit(0x15, 0x80000015L, ControlCommand.class, 15, OPTIONS));
                assertThrows(
                        IllegalArgumentException.class,
                        () -> window.admit(0x15, 0x80000015L, ControlCommand.class, 0x100000000L, OPTIONS));
                assertThrows(NullPointerException.class, () -> window.admit(0x15, 0x80000015L, null, 16, OPTIONS));
                assertThrows(
                        NullPointerException.class,
                        () -> window.admit(0x15, 0x80000015L, ControlCommand.class, 16, null));
                assertEquals(0, notifications.outstandingCount());
                assertEquals(0, window.pendingBytes());
                assertEquals(1, admit(window).identity().sequenceNumber());
            }
        }
    }

    @Test
    void refusesSequenceExhaustionWithoutWrappingOrReservingNotifications() {
        try (BoundedNotifications notifications = new BoundedNotifications(2, 1);
                RequestWindow window = new RequestWindow(GENERATION, 2, 32, notifications, () -> 0, 0x7fffffffL)) {
            assertEquals(0x7fffffffL, admit(window).identity().sequenceNumber());
            RequestFailure failure = assertThrows(RequestFailure.class, () -> admit(window));
            assertEquals(RequestFailure.Reason.SEQUENCE_EXHAUSTED, failure.reason());
            assertEquals(1, notifications.outstandingCount());
            assertEquals(1, window.pendingCount());
            assertEquals(16, window.pendingBytes());
        }
    }

    @Test
    void correlatedNegativeGenericNackIsAKnownPeerOutcomeForAnyExpectedResponseType() throws Exception {
        try (RequestWindow window = new RequestWindow(GENERATION, 1, 32, 1, () -> 0)) {
            var handle = window.admit(9, 0x80000009L, BindResponse.class, 32, OPTIONS);
            assertFalse(window.accept(GENERATION, response(0, ControlCommand.Type.GENERIC_NACK, 3)));
            assertFalse(window.accept(GENERATION, response(1, ControlCommand.Type.GENERIC_NACK, 0)));
            var parameters = new OptionalParameters(
                    java.util.List.of(new Tlv(0x1400, new byte[] {1, 2}), new Tlv(0x1400, new byte[] {3})));
            var nack = new Pdu<>(0xfedcba98L, 1, new ControlCommand(ControlCommand.Type.GENERIC_NACK, parameters));
            assertTrue(window.accept(GENERATION, nack));
            PeerNackException failure = assertInstanceOf(
                    PeerNackException.class,
                    handle.terminalOutcome().orElseThrow().failure().orElseThrow());
            assertEquals(handle.identity(), failure.requestIdentity());
            assertEquals(nack, failure.nack());
            assertFalse(handle.cancel());
            assertEquals(0, window.pendingCount());
            ExecutionException notified = assertThrows(
                    ExecutionException.class,
                    () -> handle.result().toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertSame(failure, notified.getCause());
        }
    }

    @Test
    void invocationTimeoutIncludesWorkBeforeAdmissionAcrossSignedClockWrap() {
        AtomicLong clock = new AtomicLong(Long.MAX_VALUE - 50);
        long invoked = clock.get();
        try (RequestWindow window = new RequestWindow(GENERATION, 2, 32, 2, clock::get)) {
            clock.addAndGet(100);
            RequestFailure failure = assertThrows(
                    RequestFailure.class,
                    () -> window.admit(0x15, 0x80000015L, ControlCommand.class, 16, OPTIONS, invoked));
            assertEquals(RequestFailure.Reason.DEADLINE_EXPIRED, failure.reason());
            assertEquals(0, window.pendingCount());
            assertEquals(1, admit(window).identity().sequenceNumber());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "expire,false",
        "expire,true",
        "response,false",
        "response,true",
        "write,false",
        "write,true",
        "cancel,false",
        "cancel,true",
        "failure,false",
        "failure,true",
        "disconnect,false",
        "disconnect,true"
    })
    void dueDeadlineWinsAtEveryTerminalOrWriteTransitionWithoutTimerScheduling(String event, boolean startedWrite) {
        AtomicLong clock = new AtomicLong(Long.MAX_VALUE - 50);
        try (RequestWindow window = new RequestWindow(GENERATION, 1, 16, 1, clock::get)) {
            var handle = admit(window);
            if (startedWrite) {
                assertTrue(window.beginWrite(handle));
            }
            clock.addAndGet(99);
            assertEquals(0, window.expire());
            clock.incrementAndGet();
            switch (event) {
                case "expire" -> assertEquals(1, window.expire());
                case "response" ->
                    assertFalse(window.accept(GENERATION, response(1, ControlCommand.Type.ENQUIRE_LINK_RESPONSE, 0)));
                case "write" -> assertFalse(window.beginWrite(handle));
                case "cancel" -> assertFalse(handle.cancel());
                case "failure" -> assertFalse(window.failWrite(handle, new RuntimeException("write")));
                case "disconnect" -> assertEquals(1, window.disconnect(null));
                default -> throw new IllegalArgumentException("Unknown event");
            }
            RequestFailure failure = assertInstanceOf(
                    RequestFailure.class,
                    handle.terminalOutcome().orElseThrow().failure().orElseThrow());
            assertEquals(RequestFailure.Reason.DEADLINE_EXPIRED, failure.reason());
            assertEquals(
                    startedWrite ? TransmissionCertainty.MAY_HAVE_BEEN_SENT : TransmissionCertainty.NOT_SENT,
                    failure.transmission());
            assertEquals(0, window.expire());
            assertEquals(0, window.pendingCount());
            assertEquals(0, window.pendingBytes());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "CANCELLED,false",
        "CANCELLED,true",
        "WRITE_FAILED,false",
        "WRITE_FAILED,true",
        "DISCONNECTED,false",
        "DISCONNECTED,true"
    })
    void localTerminalOutcomesReleaseCapacityOnceAndRetainTransmissionKnowledge(
            RequestFailure.Reason reason, boolean startWrite) throws Exception {
        try (RequestWindow window = new RequestWindow(GENERATION, 2, 32, 2, () -> 0)) {
            var handle = admit(window);
            RuntimeException transportCause = new RuntimeException("controlled transport failure");
            if (startWrite) {
                assertTrue(window.beginWrite(handle));
                assertFalse(window.beginWrite(handle));
            }
            boolean won =
                    switch (reason) {
                        case CANCELLED -> handle.cancel();
                        case WRITE_FAILED -> window.failWrite(handle, transportCause);
                        case DISCONNECTED -> window.disconnect(transportCause) == 1;
                        default -> throw new IllegalArgumentException("Unsupported test reason");
                    };
            assertTrue(won);
            assertTrue(handle.isDone());
            RequestFailure failure = assertInstanceOf(
                    RequestFailure.class,
                    handle.terminalOutcome().orElseThrow().failure().orElseThrow());
            assertEquals(reason, failure.reason());
            assertEquals(handle.identity(), failure.requestIdentity().orElseThrow());
            assertEquals(
                    startWrite ? TransmissionCertainty.MAY_HAVE_BEEN_SENT : TransmissionCertainty.NOT_SENT,
                    failure.transmission());
            if (reason != RequestFailure.Reason.CANCELLED) {
                assertSame(transportCause, failure.getCause());
            }
            assertEquals(0, window.pendingCount());
            assertEquals(0, window.pendingBytes());
            assertFalse(handle.cancel());
            assertFalse(window.failWrite(handle, transportCause));
            assertFalse(window.beginWrite(handle));
            assertFalse(window.accept(GENERATION, response(1, ControlCommand.Type.ENQUIRE_LINK_RESPONSE, 0)));
            ExecutionException notified = assertThrows(
                    ExecutionException.class,
                    () -> handle.result().toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertSame(failure, notified.getCause());
            if (reason == RequestFailure.Reason.DISCONNECTED) {
                assertEquals(
                        RequestFailure.Reason.CLOSED,
                        assertThrows(RequestFailure.class, () -> admit(window)).reason());
            }
        }
    }

    @Test
    void correlatesGenerationSequenceAndExactCommandWithOutOfOrderAndDuplicateResponses() throws Exception {
        try (RequestWindow window = new RequestWindow(GENERATION, 3, 100, 3, () -> 0)) {
            var first = admit(window);
            var second = admit(window);
            assertFalse(window.accept(UUID.randomUUID(), response(2, ControlCommand.Type.ENQUIRE_LINK_RESPONSE, 0)));
            assertFalse(window.accept(GENERATION, response(3, ControlCommand.Type.ENQUIRE_LINK_RESPONSE, 0)));
            assertFalse(window.accept(GENERATION, response(2, ControlCommand.Type.UNBIND_RESPONSE, 0)));
            assertFalse(window.accept(GENERATION, response(2, ControlCommand.Type.ENQUIRE_LINK, 0)));
            assertEquals(2, window.pendingCount());
            Pdu<ControlCommand> negative = response(2, ControlCommand.Type.ENQUIRE_LINK_RESPONSE, 0x12345678);
            assertTrue(window.accept(GENERATION, negative));
            assertTrue(second.isDone());
            assertEquals(
                    negative, second.terminalOutcome().orElseThrow().response().orElseThrow());
            assertEquals(negative, second.result().toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertFalse(first.isDone());
            assertEquals(1, window.pendingCount());
            assertEquals(16, window.pendingBytes());
            assertFalse(window.accept(GENERATION, negative));
            assertTrue(window.accept(GENERATION, response(1, ControlCommand.Type.ENQUIRE_LINK_RESPONSE, 0)));
            assertEquals(0, window.pendingCount());
            assertEquals(0, window.pendingBytes());
        }
    }

    private static Pdu<ControlCommand> response(long sequence, ControlCommand.Type type, long status) {
        return new Pdu<>(status, sequence, new ControlCommand(type, new OptionalParameters(java.util.List.of())));
    }

    @ParameterizedTest
    @CsvSource({"1,32,2,WINDOW_FULL", "2,31,2,BYTE_LIMIT", "2,32,1,NOTIFICATION_BACKLOG"})
    void rejectsAdmissionAtEachBoundWithoutConsumingAnotherSlot(
            int count, long bytes, int notifications, RequestFailure.Reason reason) {
        try (RequestWindow window = new RequestWindow(GENERATION, count, bytes, notifications, () -> 0)) {
            admit(window);
            RequestFailure failure = assertThrows(RequestFailure.class, () -> admit(window));
            assertEquals(reason, failure.reason());
            assertEquals(GENERATION, failure.generation());
            assertEquals(0, failure.sequenceNumber());
            assertTrue(failure.requestIdentity().isEmpty());
            assertEquals(0x15, failure.requestCommandId());
            assertEquals(TransmissionCertainty.NOT_SENT, failure.transmission());
            assertEquals(1, window.pendingCount());
            assertEquals(16, window.pendingBytes());
        }
    }

    @Test
    void allocatesMonotonicallyWithinAnImmutableGeneration() {
        AtomicLong clock = new AtomicLong();
        try (RequestWindow window = new RequestWindow(GENERATION, 3, 100, 3, clock::get)) {
            var first = admit(window);
            var second = admit(window);
            assertEquals(new RequestIdentity(GENERATION, 1), first.identity());
            assertEquals(new RequestIdentity(GENERATION, 2), second.identity());
            assertEquals(2, window.pendingCount());
            assertEquals(32, window.pendingBytes());
            assertEquals(first, window.pending(1).orElseThrow());
            assertEquals(100, first.deadlineNanos());
        }
    }

    private static RequestHandle<ControlCommand> admit(RequestWindow window) {
        return window.admit(0x15, 0x80000015L, ControlCommand.class, 16, OPTIONS);
    }
}
