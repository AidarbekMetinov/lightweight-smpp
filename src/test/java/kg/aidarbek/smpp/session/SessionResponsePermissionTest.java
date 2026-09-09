package kg.aidarbek.smpp.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.PduHeader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

final class SessionResponsePermissionTest {
    @ParameterizedTest
    @MethodSource("variants")
    void ordinaryResponsesRequireOppositeDirectionSequenceCommandAndCallerCorrelation(
            SmppVersion version, EndpointRole role) {
        SessionStateMachine machine = bound(version, role);
        PduDirection submit = role == EndpointRole.ESME ? PduDirection.OUTBOUND : PduDirection.INBOUND;
        PduDirection deliver = submit.opposite();
        PduHeader submitRequest = header(4, 0, 20);
        PduHeader deliverRequest = header(5, 0, 20);
        assertEquals(SessionDecision.ACCEPTED, machine.request(submit, submitRequest, SendRequirements.COMMON));
        assertEquals(SessionDecision.ACCEPTED, machine.request(deliver, deliverRequest, SendRequirements.COMMON));
        ResponseContext submitContext = new ResponseContext(submit, submitRequest);
        ResponseContext deliverContext = new ResponseContext(deliver, deliverRequest);
        assertEquals(
                SessionDecision.UNEXPECTED_RESPONSE,
                machine.responsePermission(
                        submit.opposite(), header(0x80000004L, 0, 20), Optional.empty(), SendRequirements.COMMON));
        assertEquals(
                SessionDecision.UNEXPECTED_RESPONSE,
                machine.responsePermission(
                        submit, header(0x80000004L, 0, 20), Optional.of(submitContext), SendRequirements.COMMON));
        assertEquals(
                SessionDecision.UNEXPECTED_RESPONSE,
                machine.responsePermission(
                        submit.opposite(),
                        header(0x80000005L, 0, 20),
                        Optional.of(submitContext),
                        SendRequirements.COMMON));
        assertEquals(
                SessionDecision.UNEXPECTED_RESPONSE,
                machine.responsePermission(
                        submit.opposite(),
                        header(0x80000004L, 0, 21),
                        Optional.of(submitContext),
                        SendRequirements.COMMON));
        assertEquals(
                SessionDecision.UNEXPECTED_RESPONSE,
                machine.responsePermission(
                        submit.opposite(),
                        header(0x80000004L, 0, 20),
                        Optional.of(deliverContext),
                        SendRequirements.COMMON));
        assertEquals(
                SessionDecision.ACCEPTED,
                machine.responsePermission(
                        deliver.opposite(),
                        header(0x80000005L, 0, 20),
                        Optional.of(deliverContext),
                        SendRequirements.COMMON));
        assertEquals(
                SessionDecision.ACCEPTED,
                machine.responsePermission(
                        submit.opposite(),
                        header(0x80000004L, 0xf0000000L, 20),
                        Optional.of(submitContext),
                        SendRequirements.COMMON));
        assertEquals(SessionState.BOUND_TRX, machine.state());
        assertEquals(
                SessionDecision.UNEXPECTED_RESPONSE,
                machine.responsePermission(
                        submit.opposite(), header(0x80000004L, 0, 20), Optional.empty(), SendRequirements.COMMON));
    }

    private static Stream<Arguments> variants() {
        return Stream.of(SmppVersion.values())
                .flatMap(version -> Stream.of(EndpointRole.values()).map(role -> Arguments.of(version, role)));
    }

    private static SessionStateMachine bound(SmppVersion version, EndpointRole role) {
        SessionStateMachine machine = open(version, role);
        PduDirection bind = role == EndpointRole.ESME ? PduDirection.OUTBOUND : PduDirection.INBOUND;
        machine.bindRequest(bind, BindMode.TRANSCEIVER, version.interfaceVersion(), 1);
        machine.bindResponse(bind.opposite(), header(0x80000009L, 0, 1), OptionalInt.of(version.interfaceVersion()));
        return machine;
    }

    private static SessionStateMachine open(SmppVersion version, EndpointRole role) {
        Set<Long> implemented = Set.of(9L, 4L, 5L, 6L, 0x15L);
        SessionStateMachine machine = role == EndpointRole.ESME
                ? SessionStateMachine.esme(version, false, implemented)
                : SessionStateMachine.messageCenter(Set.of(version), version, implemented);
        assertEquals(SessionDecision.ACCEPTED, machine.connected());
        return machine;
    }

    private static PduHeader header(long command, long status, long sequence) {
        return new PduHeader(16, command, status, sequence);
    }

    @ParameterizedTest
    @MethodSource("variants")
    void protocolErrorsCanReplyBeforeBindingButNeverAcknowledgeAnotherResponse(SmppVersion version, EndpointRole role) {
        SessionStateMachine machine = open(version, role);
        for (PduDirection request : PduDirection.values()) {
            ResponseContext invalidSubmit = new ResponseContext(request, header(4, 0, 9));
            assertEquals(
                    SessionDecision.INVALID_STATE,
                    machine.request(request, invalidSubmit.request(), SendRequirements.COMMON));
            assertEquals(
                    SessionDecision.ACCEPTED,
                    machine.protocolErrorPermission(
                            request.opposite(), header(0x80000004L, 4, 9), invalidSubmit, SendRequirements.COMMON));
            ResponseContext unknown = new ResponseContext(request, header(0x99, 0, 0));
            assertEquals(
                    SessionDecision.ACCEPTED,
                    machine.protocolErrorPermission(
                            request.opposite(), header(0x80000000L, 3, 0), unknown, SendRequirements.COMMON));
            assertEquals(
                    SessionDecision.UNEXPECTED_RESPONSE,
                    machine.protocolErrorPermission(
                            request, header(0x80000004L, 4, 9), invalidSubmit, SendRequirements.COMMON));
            assertEquals(
                    SessionDecision.UNEXPECTED_RESPONSE,
                    machine.protocolErrorPermission(
                            request.opposite(), header(0x80000004L, 4, 10), invalidSubmit, SendRequirements.COMMON));
            assertEquals(
                    SessionDecision.UNEXPECTED_RESPONSE,
                    machine.protocolErrorPermission(
                            request.opposite(), header(0x80000004L, 0, 9), invalidSubmit, SendRequirements.COMMON));
            assertEquals(
                    SessionDecision.UNEXPECTED_RESPONSE,
                    machine.protocolErrorPermission(
                            request.opposite(), header(0x80000099L, 3, 0), unknown, SendRequirements.COMMON));
            for (long responseId : new long[] {0x80000004L, 0x80000000L, 0x80000099L}) {
                ResponseContext response = new ResponseContext(request, header(responseId, 3, 9));
                assertEquals(
                        SessionDecision.UNEXPECTED_RESPONSE,
                        machine.protocolErrorPermission(
                                request.opposite(), header(0x80000000L, 3, 9), response, SendRequirements.COMMON));
            }
            assertEquals(SessionState.OPEN, machine.state());
        }
    }

    @ParameterizedTest
    @MethodSource("variants")
    void enquiryResponseUsesTheProfileAndMatchedApplicationResponsesMayDrainDuringUnbind(
            SmppVersion version, EndpointRole role) {
        for (PduDirection request : PduDirection.values()) {
            SessionStateMachine open = open(version, role);
            ResponseContext enquiry = new ResponseContext(request, header(0x15, 0, 3));
            assertEquals(
                    version == SmppVersion.V5_0 ? SessionDecision.ACCEPTED : SessionDecision.INVALID_STATE,
                    open.responsePermission(
                            request.opposite(),
                            header(0x80000015L, 0, 3),
                            Optional.of(enquiry),
                            SendRequirements.COMMON));
        }
        SessionStateMachine machine = bound(version, role);
        PduDirection submit = role == EndpointRole.ESME ? PduDirection.OUTBOUND : PduDirection.INBOUND;
        ResponseContext context = new ResponseContext(submit, header(4, 0, 20));
        machine.request(submit, context.request(), SendRequirements.COMMON);
        machine.request(PduDirection.INBOUND, header(6, 0, 21), SendRequirements.COMMON);
        assertEquals(
                SessionDecision.ACCEPTED,
                machine.responsePermission(
                        submit.opposite(), header(0x80000004L, 0, 20), Optional.of(context), SendRequirements.COMMON));
        assertEquals(SessionState.UNBINDING, machine.state());
        assertEquals(
                SessionDecision.ACCEPTED,
                machine.responsePermission(
                        submit.opposite(), header(0x80000000L, 3, 20), Optional.of(context), SendRequirements.COMMON));
        assertEquals(
                SessionDecision.UNEXPECTED_RESPONSE,
                machine.responsePermission(
                        submit.opposite(), header(0x80000000L, 0, 20), Optional.of(context), SendRequirements.COMMON));
        machine.close();
        assertEquals(
                SessionDecision.INVALID_STATE,
                machine.responsePermission(
                        submit.opposite(), header(0x80000004L, 0, 20), Optional.of(context), SendRequirements.COMMON));
        assertEquals(
                SessionDecision.INVALID_STATE,
                machine.protocolErrorPermission(
                        submit.opposite(), header(0x80000004L, 4, 20), context, SendRequirements.COMMON));
    }

    @ParameterizedTest
    @MethodSource("variants")
    void ordinaryContextCannotBypassLifecycleOrInventResponsesToNotifications(SmppVersion version, EndpointRole role) {
        SessionStateMachine machine = bound(version, role);
        for (long command : new long[] {1, 2, 9, 6, 0xb, 0x102, 0x99}) {
            ResponseContext context = new ResponseContext(PduDirection.INBOUND, header(command, 0, 20));
            assertEquals(
                    SessionDecision.UNEXPECTED_RESPONSE,
                    machine.responsePermission(
                            PduDirection.OUTBOUND,
                            header(command | 0x80000000L, 0, 20),
                            Optional.of(context),
                            SendRequirements.COMMON));
        }
        for (long invalidSequence : new long[] {0, 0x80000000L, 0xffffffffL}) {
            PduDirection submit = role == EndpointRole.ESME ? PduDirection.OUTBOUND : PduDirection.INBOUND;
            ResponseContext context = new ResponseContext(submit, header(4, 0, invalidSequence));
            assertEquals(
                    SessionDecision.UNEXPECTED_RESPONSE,
                    machine.responsePermission(
                            submit.opposite(),
                            header(0x80000004L, 0, invalidSequence),
                            Optional.of(context),
                            SendRequirements.COMMON));
        }
        assertEquals(SessionState.BOUND_TRX, machine.state());
    }

    @Test
    void responseContextRetainsRawValuesAndHasImmutableValueSemantics() {
        PduHeader raw = header(0xffffffffL, 0xffffffffL, 0xffffffffL);
        ResponseContext context = new ResponseContext(PduDirection.INBOUND, raw);
        assertEquals(raw, context.request());
        assertEquals(context, new ResponseContext(PduDirection.INBOUND, raw));
        assertEquals(context.hashCode(), new ResponseContext(PduDirection.INBOUND, raw).hashCode());
        assertThrows(NullPointerException.class, () -> new ResponseContext(null, raw));
        assertThrows(NullPointerException.class, () -> new ResponseContext(PduDirection.INBOUND, null));
    }

    @Test
    void outgoingResponseRequirementsDoNotTreatIncomingRawExtensionsAsNegotiatedSupport() {
        SessionStateMachine machine = SessionStateMachine.esme(SmppVersion.V5_0, false, Set.of(9L, 4L, 5L));
        machine.connected();
        machine.bindRequest(PduDirection.OUTBOUND, BindMode.TRANSCEIVER, 0x50, 1);
        machine.bindResponse(PduDirection.INBOUND, header(0x80000009L, 0, 1), OptionalInt.empty());
        SendRequirements extensions = new SendRequirements(SmppVersion.V5_0, true);
        ResponseContext submit = new ResponseContext(PduDirection.OUTBOUND, header(4, 0, 2));
        ResponseContext deliver = new ResponseContext(PduDirection.INBOUND, header(5, 0, 3));
        assertEquals(
                SessionDecision.ACCEPTED,
                machine.responsePermission(
                        PduDirection.INBOUND, header(0x80000004L, 0, 2), Optional.of(submit), extensions));
        assertEquals(
                SessionDecision.CAPABILITY_UNAVAILABLE,
                machine.responsePermission(
                        PduDirection.OUTBOUND, header(0x80000005L, 0, 3), Optional.of(deliver), extensions));
        assertEquals(
                SessionDecision.ACCEPTED,
                machine.protocolErrorPermission(PduDirection.INBOUND, header(0x80000004L, 4, 2), submit, extensions));
        assertEquals(
                SessionDecision.CAPABILITY_UNAVAILABLE,
                machine.protocolErrorPermission(PduDirection.OUTBOUND, header(0x80000005L, 4, 3), deliver, extensions));
        assertEquals(
                SessionDecision.ACCEPTED,
                machine.responsePermission(
                        PduDirection.OUTBOUND,
                        header(0x80000005L, 0, 3),
                        Optional.of(deliver),
                        SendRequirements.COMMON));
        assertEquals(SessionState.BOUND_TRX, machine.state());
    }

    @Test
    void protocolErrorForDuplicateBindDoesNotConsumeTheOriginalLifecycleRequest() {
        SessionStateMachine machine = open(SmppVersion.V3_4, EndpointRole.MESSAGE_CENTER);
        machine.bindRequest(PduDirection.INBOUND, BindMode.TRANSCEIVER, 0x34, 1);
        ResponseContext duplicate = new ResponseContext(PduDirection.INBOUND, header(9, 0, 2));
        assertEquals(
                SessionDecision.INVALID_STATE,
                machine.bindRequest(PduDirection.INBOUND, BindMode.TRANSCEIVER, 0x34, 2));
        assertEquals(
                SessionDecision.ACCEPTED,
                machine.protocolErrorPermission(
                        PduDirection.OUTBOUND, header(0x80000009L, 5, 2), duplicate, SendRequirements.COMMON));
        assertEquals(SessionState.BINDING, machine.state());
        assertEquals(
                SessionDecision.ACCEPTED,
                machine.bindResponse(PduDirection.OUTBOUND, header(0x80000009L, 0, 1), OptionalInt.of(0x34)));
        assertEquals(SessionState.BOUND_TRX, machine.state());
    }

    @Test
    void errorContextCannotCreateAPendingRequestAndNullArgumentsAreAtomic() {
        SessionStateMachine machine = bound(SmppVersion.V3_4, EndpointRole.ESME);
        ResponseContext unknown = new ResponseContext(PduDirection.OUTBOUND, header(0, 0, 2));
        assertEquals(
                SessionDecision.UNEXPECTED_RESPONSE,
                machine.responsePermission(
                        PduDirection.INBOUND,
                        header(0x80000000L, 3, 2),
                        Optional.of(unknown),
                        SendRequirements.COMMON));
        ResponseContext invalidStatus = new ResponseContext(PduDirection.OUTBOUND, header(4, 3, 2));
        assertEquals(
                SessionDecision.UNEXPECTED_RESPONSE,
                machine.responsePermission(
                        PduDirection.INBOUND,
                        header(0x80000004L, 3, 2),
                        Optional.of(invalidStatus),
                        SendRequirements.COMMON));
        assertThrows(
                NullPointerException.class,
                () -> machine.responsePermission(
                        null, header(0x80000004L, 0, 2), Optional.empty(), SendRequirements.COMMON));
        assertThrows(
                NullPointerException.class,
                () -> machine.responsePermission(
                        PduDirection.INBOUND, null, Optional.empty(), SendRequirements.COMMON));
        assertThrows(
                NullPointerException.class,
                () -> machine.responsePermission(
                        PduDirection.INBOUND, header(0x80000004L, 0, 2), null, SendRequirements.COMMON));
        assertThrows(
                NullPointerException.class,
                () -> machine.responsePermission(
                        PduDirection.INBOUND, header(0x80000004L, 0, 2), Optional.empty(), null));
        assertThrows(
                NullPointerException.class,
                () -> machine.protocolErrorPermission(
                        PduDirection.OUTBOUND, header(0x80000000L, 3, 2), null, SendRequirements.COMMON));
        assertEquals(SessionState.BOUND_TRX, machine.state());
        SessionStateMachine connecting = SessionStateMachine.esme(SmppVersion.V3_4, false, Set.of(9L));
        assertEquals(
                SessionDecision.INVALID_STATE,
                connecting.protocolErrorPermission(
                        PduDirection.INBOUND, header(0x80000000L, 3, 2), unknown, SendRequirements.COMMON));
    }

    @ParameterizedTest
    @MethodSource("variants")
    void unusableOriginalSequencesRequireZeroSequenceNackAndCannotAuthorizeIllegalReplies(
            SmppVersion version, EndpointRole role) {
        SessionStateMachine machine = open(version, role);
        for (PduDirection request : PduDirection.values()) {
            for (long originalSequence : new long[] {0x80000000L, 0xffffffffL, 0}) {
                ResponseContext offending = new ResponseContext(request, header(4, 0, originalSequence));
                assertEquals(
                        SessionDecision.ACCEPTED,
                        machine.protocolErrorPermission(
                                request.opposite(), header(0x80000000L, 3, 0), offending, SendRequirements.COMMON));
                for (long responseSequence : new long[] {1, 0x80000000L, 0xffffffffL}) {
                    assertEquals(
                            SessionDecision.UNEXPECTED_RESPONSE,
                            machine.protocolErrorPermission(
                                    request.opposite(),
                                    header(0x80000000L, 3, responseSequence),
                                    offending,
                                    SendRequirements.COMMON));
                }
                assertEquals(
                        SessionDecision.UNEXPECTED_RESPONSE,
                        machine.protocolErrorPermission(
                                request.opposite(),
                                header(0x80000004L, 4, originalSequence),
                                offending,
                                SendRequirements.COMMON));
            }
            for (long usable : new long[] {1, 0x7fffffffL}) {
                ResponseContext offending = new ResponseContext(request, header(4, 0, usable));
                assertEquals(
                        SessionDecision.ACCEPTED,
                        machine.protocolErrorPermission(
                                request.opposite(),
                                header(0x80000000L, 3, usable),
                                offending,
                                SendRequirements.COMMON));
                assertEquals(
                        SessionDecision.UNEXPECTED_RESPONSE,
                        machine.protocolErrorPermission(
                                request.opposite(), header(0x80000000L, 3, 0), offending, SendRequirements.COMMON));
            }
            assertEquals(SessionState.OPEN, machine.state());
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0x80000006L, 0x80000000L})
    void outgoingUnbindExtensionsAreCheckedBeforeTheLifecycleIdentityIsConsumed(long responseCommand) {
        long status = responseCommand == 0x80000000L ? 3 : 0;
        SessionStateMachine machine = SessionStateMachine.esme(SmppVersion.V5_0, false, Set.of(9L, 6L));
        machine.connected();
        machine.bindRequest(PduDirection.OUTBOUND, BindMode.TRANSCEIVER, 0x50, 1);
        machine.bindResponse(PduDirection.INBOUND, header(0x80000009L, 0, 1), OptionalInt.empty());
        machine.request(PduDirection.INBOUND, header(6, 0, 2), SendRequirements.COMMON);
        assertEquals(
                SessionDecision.CAPABILITY_UNAVAILABLE,
                machine.unbindResponse(
                        PduDirection.OUTBOUND,
                        header(responseCommand, status, 2),
                        new SendRequirements(SmppVersion.V5_0, true)));
        assertEquals(SessionState.UNBINDING, machine.state());
        assertThrows(
                NullPointerException.class,
                () -> machine.unbindResponse(PduDirection.OUTBOUND, header(responseCommand, status, 2), null));
        assertEquals(SessionState.UNBINDING, machine.state());
        assertEquals(
                SessionDecision.ACCEPTED,
                machine.unbindResponse(
                        PduDirection.OUTBOUND, header(responseCommand, status, 2), SendRequirements.COMMON));
        assertEquals(SessionState.CLOSED, machine.state());
    }
}
