package kg.aidarbek.smpp.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
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

final class SessionStateMachineTest {
    private static final Set<Long> IMPLEMENTED = Set.of(1L, 2L, 9L, 6L, 0x15L, 4L, 5L, 0x103L);

    @ParameterizedTest
    @MethodSource("variants")
    void connectsAndBindsEachRoleVersionAndMode(SmppVersion version, EndpointRole role, BindMode mode) {
        SessionStateMachine machine = machine(version, role);
        assertEquals(SessionState.CONNECTING, machine.state());
        assertTrue(machine.negotiation().isEmpty());
        assertEquals(SessionDecision.ACCEPTED, machine.connected());
        assertEquals(SessionState.OPEN, machine.state());
        PduDirection bindDirection = role == EndpointRole.ESME ? PduDirection.OUTBOUND : PduDirection.INBOUND;
        assertEquals(
                SessionDecision.ACCEPTED, machine.bindRequest(bindDirection, mode, version.interfaceVersion(), 17));
        assertEquals(SessionState.BINDING, machine.state());
        assertEquals(
                SessionDecision.ACCEPTED,
                machine.bindResponse(
                        bindDirection.opposite(),
                        header(mode.responseCommandId(), 0, 17),
                        OptionalInt.of(version.interfaceVersion())));
        assertEquals(boundState(mode), machine.state());
        assertEquals(
                version,
                machine.negotiation()
                        .orElseThrow()
                        .effectiveProfile()
                        .orElseThrow()
                        .version());
    }

    private static Stream<Arguments> variants() {
        return Stream.of(SmppVersion.values())
                .flatMap(version -> Stream.of(EndpointRole.values())
                        .flatMap(role -> Stream.of(BindMode.values()).map(mode -> Arguments.of(version, role, mode))));
    }

    private static SessionStateMachine machine(SmppVersion version, EndpointRole role) {
        return role == EndpointRole.ESME
                ? SessionStateMachine.esme(version, false, IMPLEMENTED)
                : SessionStateMachine.messageCenter(Set.of(version), version, IMPLEMENTED);
    }

    private static SessionState boundState(BindMode mode) {
        return switch (mode) {
            case RECEIVER -> SessionState.BOUND_RX;
            case TRANSMITTER -> SessionState.BOUND_TX;
            case TRANSCEIVER -> SessionState.BOUND_TRX;
        };
    }

    private static PduHeader header(long commandId, long status, long sequence) {
        return new PduHeader(16, commandId, status, sequence);
    }

    @ParameterizedTest
    @MethodSource("variants")
    void duplicateBindAndUnexpectedResponsesPreserveThePendingBind(
            SmppVersion version, EndpointRole role, BindMode mode) {
        SessionStateMachine machine = machine(version, role);
        PduDirection request = role == EndpointRole.ESME ? PduDirection.OUTBOUND : PduDirection.INBOUND;
        assertEquals(SessionDecision.INVALID_STATE, machine.bindRequest(request, mode, version.interfaceVersion(), 17));
        machine.connected();
        assertEquals(
                SessionDecision.UNEXPECTED_RESPONSE,
                machine.bindResponse(request.opposite(), header(mode.responseCommandId(), 0, 17), OptionalInt.empty()));
        assertEquals(
                SessionDecision.INVALID_DIRECTION,
                machine.bindRequest(request.opposite(), mode, version.interfaceVersion(), 17));
        assertEquals(SessionState.OPEN, machine.state());
        machine.bindRequest(request, mode, version.interfaceVersion(), 17);
        assertEquals(SessionDecision.INVALID_STATE, machine.connected());
        assertEquals(SessionDecision.INVALID_STATE, machine.bindRequest(request, mode, version.interfaceVersion(), 18));
        for (PduHeader wrong : new PduHeader[] {header(0x80000015L, 0, 17), header(mode.responseCommandId(), 0, 18)}) {
            assertEquals(
                    SessionDecision.UNEXPECTED_RESPONSE,
                    machine.bindResponse(request.opposite(), wrong, OptionalInt.empty()));
            assertEquals(SessionState.BINDING, machine.state());
        }
        assertEquals(
                SessionDecision.UNEXPECTED_RESPONSE,
                machine.bindResponse(request, header(mode.responseCommandId(), 0, 17), OptionalInt.empty()));
        assertEquals(
                SessionDecision.ACCEPTED,
                machine.bindResponse(
                        request.opposite(),
                        header(mode.responseCommandId(), 0, 17),
                        OptionalInt.of(version.interfaceVersion())));
        assertEquals(SessionDecision.INVALID_STATE, machine.bindRequest(request, mode, version.interfaceVersion(), 19));
        assertEquals(boundState(mode), machine.state());
        assertEquals(
                SessionDecision.UNEXPECTED_RESPONSE,
                machine.bindResponse(
                        request.opposite(),
                        header(mode.responseCommandId(), 0, 17),
                        OptionalInt.of(version.interfaceVersion())));
        assertEquals(boundState(mode), machine.state());
    }

    @ParameterizedTest
    @MethodSource("variants")
    void rejectedBindClosesAndNeverReopens(SmppVersion version, EndpointRole role, BindMode mode) {
        for (long responseCommand : new long[] {mode.responseCommandId(), 0x80000000L}) {
            SessionStateMachine machine = machine(version, role);
            machine.connected();
            PduDirection request = role == EndpointRole.ESME ? PduDirection.OUTBOUND : PduDirection.INBOUND;
            machine.bindRequest(request, mode, version.interfaceVersion(), 17);
            assertEquals(
                    SessionDecision.BIND_REJECTED,
                    machine.bindResponse(
                            request.opposite(), header(responseCommand, 0xf0000000L, 17), OptionalInt.empty()));
            assertEquals(SessionState.CLOSED, machine.state());
            assertTrue(machine.negotiation().isEmpty());
            machine.close();
            machine.close();
            assertEquals(SessionDecision.INVALID_STATE, machine.connected());
            assertEquals(
                    SessionDecision.INVALID_STATE, machine.bindRequest(request, mode, version.interfaceVersion(), 18));
            assertEquals(SessionState.CLOSED, machine.state());
        }
    }

    @Test
    void unacceptableSuccessfulBindVersionsCloseTheEsmeWithoutDowngrade() {
        for (OptionalInt advertisement :
                new OptionalInt[] {OptionalInt.of(0x34), OptionalInt.of(0xff), OptionalInt.empty()}) {
            SessionStateMachine machine = SessionStateMachine.esme(SmppVersion.V5_0, true, IMPLEMENTED);
            machine.connected();
            machine.bindRequest(PduDirection.OUTBOUND, BindMode.TRANSCEIVER, 0x50, 1);
            assertEquals(
                    SessionDecision.VERSION_REJECTED,
                    machine.bindResponse(PduDirection.INBOUND, header(0x80000009L, 0, 1), advertisement));
            assertEquals(SessionState.CLOSED, machine.state());
            assertEquals(advertisement, machine.negotiation().orElseThrow().advertisement());
            assertTrue(machine.negotiation().orElseThrow().effectiveProfile().isEmpty());
        }
    }

    @Test
    void serverCannotEmitAnUnacceptedVersionOrOmitItsConfiguredAdvertisement() {
        SessionStateMachine machine =
                SessionStateMachine.messageCenter(Set.of(SmppVersion.V3_4), SmppVersion.V5_0, IMPLEMENTED);
        machine.connected();
        machine.bindRequest(PduDirection.INBOUND, BindMode.TRANSMITTER, 0x34, 1);
        for (OptionalInt advertisement :
                new OptionalInt[] {OptionalInt.empty(), OptionalInt.of(0x34), OptionalInt.of(0xff)}) {
            assertEquals(
                    SessionDecision.CAPABILITY_UNAVAILABLE,
                    machine.bindResponse(PduDirection.OUTBOUND, header(0x80000002L, 0, 1), advertisement));
            assertEquals(SessionState.BINDING, machine.state());
            assertTrue(machine.negotiation().isEmpty());
        }
        assertEquals(
                SessionDecision.ACCEPTED,
                machine.bindResponse(PduDirection.OUTBOUND, header(0x80000002L, 0, 1), OptionalInt.of(0x50)));
        assertEquals(
                SmppVersion.V3_4,
                machine.negotiation()
                        .orElseThrow()
                        .effectiveProfile()
                        .orElseThrow()
                        .version());
        for (int requested : new int[] {0x50, 0x33}) {
            SessionStateMachine refusal =
                    SessionStateMachine.messageCenter(Set.of(SmppVersion.V3_4), SmppVersion.V5_0, IMPLEMENTED);
            refusal.connected();
            refusal.bindRequest(PduDirection.INBOUND, BindMode.TRANSMITTER, requested, 1);
            assertEquals(
                    SessionDecision.CAPABILITY_UNAVAILABLE,
                    refusal.bindResponse(PduDirection.OUTBOUND, header(0x80000002L, 0, 1), OptionalInt.of(0x50)));
            assertEquals(SessionState.BINDING, refusal.state());
            assertEquals(
                    SessionDecision.BIND_REJECTED,
                    refusal.bindResponse(PduDirection.OUTBOUND, header(0x80000002L, 0xd, 1), OptionalInt.empty()));
            assertEquals(SessionState.CLOSED, refusal.state());
        }
    }

    @ParameterizedTest
    @MethodSource("variants")
    void closeIsIdempotentAtEveryEstablishedStage(SmppVersion version, EndpointRole role, BindMode mode) {
        for (int stage = 0; stage <= 3; stage++) {
            SessionStateMachine machine = machine(version, role);
            PduDirection request = role == EndpointRole.ESME ? PduDirection.OUTBOUND : PduDirection.INBOUND;
            if (stage >= 1) machine.connected();
            if (stage >= 2) machine.bindRequest(request, mode, version.interfaceVersion(), 17);
            if (stage >= 3)
                machine.bindResponse(
                        request.opposite(),
                        header(mode.responseCommandId(), 0, 17),
                        OptionalInt.of(version.interfaceVersion()));
            machine.close();
            machine.close();
            assertEquals(SessionState.CLOSED, machine.state());
            assertEquals(
                    SessionDecision.UNEXPECTED_RESPONSE,
                    machine.bindResponse(
                            request.opposite(), header(mode.responseCommandId(), 0, 17), OptionalInt.empty()));
            assertEquals(SessionDecision.INVALID_STATE, machine.connected());
        }
    }

    @ParameterizedTest
    @MethodSource("variants")
    void requestCapabilitiesEnforceModeVersionAndCurrentState(SmppVersion version, EndpointRole role, BindMode mode) {
        SessionStateMachine machine = machine(version, role);
        assertTrue(machine.availableRequests(PduDirection.OUTBOUND, SendRequirements.COMMON)
                .isEmpty());
        machine.connected();
        assertEquals(
                SessionDecision.INVALID_STATE,
                machine.request(PduDirection.OUTBOUND, header(4, 0, 2), SendRequirements.COMMON));
        PduDirection bind = role == EndpointRole.ESME ? PduDirection.OUTBOUND : PduDirection.INBOUND;
        machine.bindRequest(bind, mode, version.interfaceVersion(), 17);
        assertEquals(
                SessionDecision.INVALID_STATE,
                machine.request(PduDirection.INBOUND, header(5, 0, 2), SendRequirements.COMMON));
        machine.bindResponse(
                bind.opposite(), header(mode.responseCommandId(), 0, 17), OptionalInt.of(version.interfaceVersion()));
        for (PduDirection direction : PduDirection.values()) {
            boolean esmeOrigin = direction.origin(role) == EndpointRole.ESME;
            Set<Long> expected = new HashSet<>(Set.of(6L, 0x15L));
            if (esmeOrigin && mode != BindMode.RECEIVER) expected.add(4L);
            if (!esmeOrigin && mode != BindMode.TRANSMITTER) expected.add(5L);
            if (version == SmppVersion.V3_4
                    || (esmeOrigin && mode != BindMode.RECEIVER)
                    || (!esmeOrigin && mode != BindMode.TRANSMITTER)) expected.add(0x103L);
            Set<Long> snapshot = machine.availableRequests(direction, SendRequirements.COMMON);
            assertEquals(expected, snapshot);
            assertThrows(UnsupportedOperationException.class, () -> snapshot.add(3L));
            for (long command : new long[] {4, 5, 0x103}) {
                SessionDecision expectedDecision =
                        expected.contains(command) ? SessionDecision.ACCEPTED : SessionDecision.INVALID_STATE;
                assertEquals(
                        expectedDecision, machine.request(direction, header(command, 0, 2), SendRequirements.COMMON));
                assertEquals(boundState(mode), machine.state());
            }
        }
        assertFalse(machine.availableRequests(PduDirection.OUTBOUND, SendRequirements.COMMON)
                .contains(3L));
        Set<Long> previous = machine.availableRequests(PduDirection.OUTBOUND, SendRequirements.COMMON);
        machine.close();
        assertFalse(previous.isEmpty());
        assertTrue(machine.availableRequests(PduDirection.OUTBOUND, SendRequirements.COMMON)
                .isEmpty());
        assertEquals(
                SessionDecision.INVALID_STATE,
                machine.request(PduDirection.OUTBOUND, header(0x15, 0, 2), SendRequirements.COMMON));
    }

    @Test
    void missingAdvertisementGatesFieldsAndRetainsRequestedVersionMetadata() {
        SessionStateMachine machine = SessionStateMachine.esme(SmppVersion.V5_0, false, IMPLEMENTED);
        machine.connected();
        machine.bindRequest(PduDirection.OUTBOUND, BindMode.RECEIVER, 0x50, 1);
        machine.bindResponse(PduDirection.INBOUND, header(0x80000001L, 0, 1), OptionalInt.empty());
        assertEquals(0x50, machine.negotiation().orElseThrow().requestedInterfaceVersion());
        assertEquals(
                SessionDecision.ACCEPTED,
                machine.request(PduDirection.OUTBOUND, header(0x103, 0, 2), SendRequirements.COMMON));
        assertEquals(
                SessionDecision.CAPABILITY_UNAVAILABLE,
                machine.request(
                        PduDirection.OUTBOUND, header(0x103, 0, 2), new SendRequirements(SmppVersion.V3_4, true)));
        assertEquals(
                SessionDecision.CAPABILITY_UNAVAILABLE,
                machine.request(
                        PduDirection.OUTBOUND, header(0x103, 0, 2), new SendRequirements(SmppVersion.V5_0, false)));
        assertTrue(machine.availableRequests(PduDirection.OUTBOUND, new SendRequirements(SmppVersion.V3_4, true))
                .isEmpty());
        assertEquals(SessionState.BOUND_RX, machine.state());
    }

    @Test
    void localImplementationDeclarationsAreOwnedAndDoNotInventOtherCodecs() {
        Set<Long> implemented = new HashSet<>(IMPLEMENTED);
        SessionStateMachine machine = SessionStateMachine.esme(SmppVersion.V5_0, false, implemented);
        implemented.clear();
        machine.connected();
        machine.bindRequest(PduDirection.OUTBOUND, BindMode.TRANSMITTER, 0x50, 1);
        machine.bindResponse(PduDirection.INBOUND, header(0x80000002L, 0, 1), OptionalInt.of(0x50));
        assertEquals(
                Set.of(4L, 6L, 0x15L, 0x103L),
                machine.availableRequests(PduDirection.OUTBOUND, SendRequirements.COMMON));
        for (long command : new long[] {3, 7, 8, 0x21, 0x111, 0x112, 0x113, 0x99, -1, 0x100000004L, 0x80000004L}) {
            assertEquals(
                    SessionDecision.COMMAND_NOT_IMPLEMENTED,
                    machine.requestPermission(PduDirection.OUTBOUND, command, SendRequirements.COMMON));
        }
    }

    @ParameterizedTest
    @MethodSource("variants")
    void eitherEndpointCanUnbindAndNoApplicationRequestIsAcceptedWhileUnbinding(
            SmppVersion version, EndpointRole role, BindMode mode) {
        for (PduDirection request : PduDirection.values()) {
            SessionStateMachine machine = boundMachine(version, role, mode);
            assertEquals(
                    SessionDecision.UNEXPECTED_RESPONSE,
                    machine.unbindResponse(request.opposite(), header(0x80000006L, 0, 31), SendRequirements.COMMON));
            assertEquals(boundState(mode), machine.state());
            assertEquals(SessionDecision.ACCEPTED, machine.request(request, header(6, 0, 31), SendRequirements.COMMON));
            assertEquals(SessionState.UNBINDING, machine.state());
            assertEquals(
                    SessionDecision.INVALID_STATE, machine.request(request, header(6, 0, 32), SendRequirements.COMMON));
            for (PduDirection direction : PduDirection.values()) {
                for (long command : new long[] {4, 5, 0x103}) {
                    assertEquals(
                            SessionDecision.INVALID_STATE,
                            machine.request(direction, header(command, 0, 33), SendRequirements.COMMON));
                }
                assertEquals(
                        version == SmppVersion.V5_0 ? SessionDecision.ACCEPTED : SessionDecision.INVALID_STATE,
                        machine.request(direction, header(0x15, 0, 33), SendRequirements.COMMON));
            }
            assertEquals(
                    SessionDecision.UNEXPECTED_RESPONSE,
                    machine.unbindResponse(request, header(0x80000006L, 0, 31), SendRequirements.COMMON));
            assertEquals(
                    SessionDecision.UNEXPECTED_RESPONSE,
                    machine.unbindResponse(request.opposite(), header(0x80000006L, 0, 30), SendRequirements.COMMON));
            assertEquals(SessionState.UNBINDING, machine.state());
            assertEquals(
                    SessionDecision.ACCEPTED,
                    machine.unbindResponse(request.opposite(), header(0x80000006L, 0, 31), SendRequirements.COMMON));
            assertEquals(SessionState.CLOSED, machine.state());
            assertEquals(
                    SessionDecision.UNEXPECTED_RESPONSE,
                    machine.unbindResponse(request.opposite(), header(0x80000006L, 0, 31), SendRequirements.COMMON));
            machine.close();
            machine.close();
            assertEquals(SessionState.CLOSED, machine.state());
        }
    }

    private static SessionStateMachine boundMachine(SmppVersion version, EndpointRole role, BindMode mode) {
        SessionStateMachine machine = machine(version, role);
        machine.connected();
        PduDirection bind = role == EndpointRole.ESME ? PduDirection.OUTBOUND : PduDirection.INBOUND;
        machine.bindRequest(bind, mode, version.interfaceVersion(), 17);
        machine.bindResponse(
                bind.opposite(), header(mode.responseCommandId(), 0, 17), OptionalInt.of(version.interfaceVersion()));
        return machine;
    }

    @ParameterizedTest
    @MethodSource("variants")
    void crossedUnbindsKeepEqualSequenceNumbersInSeparateDirectionsUntilBothResponses(
            SmppVersion version, EndpointRole role, BindMode mode) {
        for (PduDirection firstRequest : PduDirection.values()) {
            for (PduDirection firstResponse : PduDirection.values()) {
                SessionStateMachine machine = boundMachine(version, role, mode);
                assertEquals(
                        SessionDecision.ACCEPTED,
                        machine.request(firstRequest, header(6, 0, 31), SendRequirements.COMMON));
                assertEquals(
                        SessionDecision.ACCEPTED,
                        machine.request(firstRequest.opposite(), header(6, 0, 31), SendRequirements.COMMON));
                assertEquals(
                        SessionDecision.ACCEPTED,
                        machine.unbindResponse(firstResponse, header(0x80000006L, 0, 31), SendRequirements.COMMON));
                assertEquals(SessionState.UNBINDING, machine.state());
                assertEquals(
                        SessionDecision.UNEXPECTED_RESPONSE,
                        machine.unbindResponse(firstResponse, header(0x80000006L, 0, 31), SendRequirements.COMMON));
                assertEquals(
                        SessionDecision.INVALID_STATE,
                        machine.request(firstResponse.opposite(), header(6, 0, 32), SendRequirements.COMMON));
                assertEquals(
                        SessionDecision.ACCEPTED,
                        machine.unbindResponse(
                                firstResponse.opposite(), header(0x80000006L, 0, 31), SendRequirements.COMMON));
                assertEquals(SessionState.CLOSED, machine.state());
            }
        }
    }

    @ParameterizedTest
    @MethodSource("variants")
    void closeOrNegativeUnbindResultSettlesTheLifecycleWithoutRestoringBoundPermissions(
            SmppVersion version, EndpointRole role, BindMode mode) {
        for (PduDirection direction : PduDirection.values()) {
            for (long command : new long[] {0x80000006L, 0x80000000L}) {
                SessionStateMachine machine = boundMachine(version, role, mode);
                machine.request(direction, header(6, 0, 31), SendRequirements.COMMON);
                machine.request(direction.opposite(), header(6, 0, 32), SendRequirements.COMMON);
                assertEquals(
                        SessionDecision.UNEXPECTED_RESPONSE,
                        machine.unbindResponse(
                                direction.opposite(), header(0x80000000L, 0, 31), SendRequirements.COMMON));
                assertEquals(
                        SessionDecision.ACCEPTED,
                        machine.unbindResponse(
                                direction.opposite(), header(command, 0xf0000000L, 31), SendRequirements.COMMON));
                assertEquals(SessionState.CLOSED, machine.state());
                assertEquals(
                        SessionDecision.UNEXPECTED_RESPONSE,
                        machine.unbindResponse(direction, header(0x80000006L, 0, 32), SendRequirements.COMMON));
            }
            SessionStateMachine machine = boundMachine(version, role, mode);
            machine.request(direction, header(6, 0, 31), SendRequirements.COMMON);
            machine.close();
            machine.close();
            assertEquals(
                    SessionDecision.UNEXPECTED_RESPONSE,
                    machine.unbindResponse(direction.opposite(), header(0x80000006L, 0, 31), SendRequirements.COMMON));
            assertTrue(machine.availableRequests(direction, SendRequirements.COMMON)
                    .isEmpty());
        }
    }

    @ParameterizedTest
    @MethodSource("variants")
    void outbindNotificationHasNoApplicationPermissionAndAllowsTheFollowingBind(
            SmppVersion version, EndpointRole role, BindMode mode) {
        Set<Long> declared = new HashSet<>(IMPLEMENTED);
        declared.add(0xbL);
        SessionStateMachine machine = role == EndpointRole.ESME
                ? SessionStateMachine.esme(version, false, declared)
                : SessionStateMachine.messageCenter(Set.of(version), version, declared);
        machine.connected();
        PduDirection notification = role == EndpointRole.MESSAGE_CENTER ? PduDirection.OUTBOUND : PduDirection.INBOUND;
        assertEquals(
                SessionDecision.ACCEPTED, machine.request(notification, header(0xb, 0, 1), SendRequirements.COMMON));
        assertEquals(version == SmppVersion.V5_0 ? SessionState.OUTBOUND : SessionState.OPEN, machine.state());
        assertEquals(
                SessionDecision.INVALID_STATE, machine.request(notification, header(5, 0, 2), SendRequirements.COMMON));
        assertEquals(
                SessionDecision.ACCEPTED,
                machine.bindRequest(notification.opposite(), mode, version.interfaceVersion(), 1));
        assertEquals(
                SessionDecision.ACCEPTED,
                machine.bindResponse(
                        notification,
                        header(mode.responseCommandId(), 0, 1),
                        OptionalInt.of(version.interfaceVersion())));
        assertEquals(boundState(mode), machine.state());
    }

    @Test
    void declarationsAndMalformedEventsAreRejectedBeforeMutation() {
        for (long command : new long[] {-1, 0, 0x80000004L, 0x100000004L, 0x99}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SessionStateMachine.esme(SmppVersion.V5_0, false, Set.of(command)));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> SessionStateMachine.messageCenter(Set.of(), SmppVersion.V5_0, IMPLEMENTED));
        assertThrows(
                IllegalArgumentException.class,
                () -> SessionStateMachine.messageCenter(Set.of(SmppVersion.V5_0), SmppVersion.V3_4, IMPLEMENTED));
        assertThrows(NullPointerException.class, () -> SessionStateMachine.esme(null, false, IMPLEMENTED));
        assertThrows(NullPointerException.class, () -> SessionStateMachine.esme(SmppVersion.V3_4, false, null));
        Set<SmppVersion> accepted = new HashSet<>(Set.of(SmppVersion.V3_4));
        SessionStateMachine machine = SessionStateMachine.messageCenter(accepted, SmppVersion.V5_0, IMPLEMENTED);
        accepted.clear();
        machine.connected();
        for (long sequence : new long[] {0, -1, 0x80000000L, 0xffffffffL, 0x100000000L}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> machine.bindRequest(PduDirection.INBOUND, BindMode.RECEIVER, 0x34, sequence));
            assertEquals(SessionState.OPEN, machine.state());
        }
        for (int version : new int[] {-1, 256}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> machine.bindRequest(PduDirection.INBOUND, BindMode.RECEIVER, version, 1));
            assertEquals(SessionState.OPEN, machine.state());
        }
        for (PduHeader malformed :
                new PduHeader[] {header(4, 0, 0), header(4, 3, 1), header(0x80000004L, 0, 1), header(9, 0, 1)}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> machine.request(PduDirection.INBOUND, malformed, SendRequirements.COMMON));
            assertEquals(SessionState.OPEN, machine.state());
        }
        assertThrows(NullPointerException.class, () -> machine.bindRequest(null, BindMode.RECEIVER, 0x34, 1));
        assertThrows(NullPointerException.class, () -> machine.bindRequest(PduDirection.INBOUND, null, 0x34, 1));
        assertThrows(NullPointerException.class, () -> machine.requestPermission(null, 4, SendRequirements.COMMON));
        assertThrows(NullPointerException.class, () -> machine.availableRequests(PduDirection.OUTBOUND, null));
        assertThrows(NullPointerException.class, () -> PduDirection.INBOUND.origin(null));
        assertEquals(
                SessionDecision.ACCEPTED,
                machine.bindRequest(PduDirection.INBOUND, BindMode.RECEIVER, 0x34, 0x7fffffffL));
        assertThrows(
                IllegalArgumentException.class,
                () -> machine.bindResponse(
                        PduDirection.OUTBOUND, header(0x80000001L, 0, 0x7fffffffL), OptionalInt.of(256)));
        assertEquals(SessionState.BINDING, machine.state());
        assertEquals(
                SessionDecision.ACCEPTED,
                machine.bindResponse(PduDirection.OUTBOUND, header(0x80000001L, 0, 0x7fffffffL), OptionalInt.of(0x50)));
        assertEquals(SessionState.BOUND_RX, machine.state());
    }

    @Test
    void rawIncomingExtensionsDoNotAuthorizeOutgoingFeatures() {
        SessionStateMachine machine = SessionStateMachine.esme(SmppVersion.V5_0, false, IMPLEMENTED);
        machine.connected();
        machine.bindRequest(PduDirection.OUTBOUND, BindMode.TRANSCEIVER, 0x50, 1);
        machine.bindResponse(PduDirection.INBOUND, header(0x80000009L, 0, 1), OptionalInt.empty());
        SendRequirements extensions = new SendRequirements(SmppVersion.V5_0, true);
        assertEquals(SessionDecision.ACCEPTED, machine.request(PduDirection.INBOUND, header(5, 0, 2), extensions));
        assertEquals(
                SessionDecision.CAPABILITY_UNAVAILABLE,
                machine.request(PduDirection.OUTBOUND, header(4, 0, 2), extensions));
        assertEquals(SessionState.BOUND_TRX, machine.state());
    }
}
