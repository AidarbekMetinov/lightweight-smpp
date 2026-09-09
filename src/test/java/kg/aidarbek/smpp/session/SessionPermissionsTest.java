package kg.aidarbek.smpp.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class SessionPermissionsTest {
    @ParameterizedTest
    @EnumSource(SmppVersion.class)
    void submissionRequiresBoundEsmeTransmitterOrTransceiver(SmppVersion version) {
        ProtocolProfile profile = ProtocolProfile.forVersion(version);
        for (EndpointRole origin : EndpointRole.values()) {
            for (SessionState state : SessionState.values()) {
                boolean expected = origin == EndpointRole.ESME
                        && (state == SessionState.BOUND_TX || state == SessionState.BOUND_TRX);
                assertEquals(
                        expected,
                        SessionPermissions.permitsRequest(profile, origin, state, 0x00000004L),
                        version + " " + origin + " " + state);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(SmppVersion.class)
    void dataAndReplacementFollowTheirOwnVersionTables(SmppVersion version) {
        ProtocolProfile profile = ProtocolProfile.forVersion(version);
        for (EndpointRole origin : EndpointRole.values()) {
            Set<SessionState> dataStates = version == SmppVersion.V3_4
                    ? Set.of(SessionState.BOUND_TX, SessionState.BOUND_RX, SessionState.BOUND_TRX)
                    : origin == EndpointRole.ESME
                            ? Set.of(SessionState.BOUND_TX, SessionState.BOUND_TRX)
                            : Set.of(SessionState.BOUND_RX, SessionState.BOUND_TRX);
            Set<SessionState> replaceStates = origin == EndpointRole.MESSAGE_CENTER
                    ? Set.of()
                    : version == SmppVersion.V3_4
                            ? Set.of(SessionState.BOUND_TX)
                            : Set.of(SessionState.BOUND_TX, SessionState.BOUND_TRX);
            for (SessionState state : SessionState.values()) {
                assertEquals(
                        dataStates.contains(state),
                        SessionPermissions.permitsRequest(profile, origin, state, 0x00000103L),
                        "data_sm " + version + " " + origin + " " + state);
                assertEquals(
                        replaceStates.contains(state),
                        SessionPermissions.permitsRequest(profile, origin, state, 0x00000007L),
                        "replace_sm " + version + " " + origin + " " + state);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(SmppVersion.class)
    void remainingRequestMatrixKeepsControlAndApplicationStatesSeparate(SmppVersion version) {
        ProtocolProfile profile = ProtocolProfile.forVersion(version);
        Set<SessionState> transmit = Set.of(SessionState.BOUND_TX, SessionState.BOUND_TRX);
        Set<SessionState> receive = Set.of(SessionState.BOUND_RX, SessionState.BOUND_TRX);
        Set<SessionState> bound = Set.of(SessionState.BOUND_RX, SessionState.BOUND_TX, SessionState.BOUND_TRX);
        Set<SessionState> bind = version == SmppVersion.V3_4
                ? Set.of(SessionState.OPEN)
                : Set.of(SessionState.OPEN, SessionState.OUTBOUND);
        Set<SessionState> enquire = version == SmppVersion.V3_4
                ? bound
                : Set.of(
                        SessionState.OPEN,
                        SessionState.OUTBOUND,
                        SessionState.BINDING,
                        SessionState.BOUND_RX,
                        SessionState.BOUND_TX,
                        SessionState.BOUND_TRX,
                        SessionState.UNBINDING);
        for (EndpointRole origin : EndpointRole.values()) {
            for (SessionState state : SessionState.values()) {
                for (long command : new long[] {0x1, 0x2, 0x9}) {
                    assertPermission(
                            profile, origin, state, command, origin == EndpointRole.ESME && bind.contains(state));
                }
                for (long command : new long[] {0x3, 0x8, 0x21}) {
                    assertPermission(
                            profile, origin, state, command, origin == EndpointRole.ESME && transmit.contains(state));
                }
                for (long command : new long[] {0x5, 0x102}) {
                    assertPermission(
                            profile,
                            origin,
                            state,
                            command,
                            origin == EndpointRole.MESSAGE_CENTER && receive.contains(state));
                }
                for (long command : new long[] {0x111, 0x112, 0x113}) {
                    assertPermission(
                            profile,
                            origin,
                            state,
                            command,
                            version == SmppVersion.V5_0 && origin == EndpointRole.ESME && transmit.contains(state));
                }
                assertPermission(
                        profile,
                        origin,
                        state,
                        0xb,
                        origin == EndpointRole.MESSAGE_CENTER && state == SessionState.OPEN);
                assertPermission(profile, origin, state, 0x6, bound.contains(state));
                assertPermission(profile, origin, state, 0x15, enquire.contains(state));
                for (long command : new long[] {-1, 0, 0x80000004L, 0x80000000L, 0x100000004L, 0x99}) {
                    assertFalse(SessionPermissions.permitsRequest(profile, origin, state, command));
                }
            }
        }
    }

    @Test
    void nullContextIsRejected() {
        ProtocolProfile profile = ProtocolProfile.forVersion(SmppVersion.V3_4);
        assertThrows(
                NullPointerException.class,
                () -> SessionPermissions.permitsRequest(null, EndpointRole.ESME, SessionState.OPEN, 4));
        assertThrows(
                NullPointerException.class,
                () -> SessionPermissions.permitsRequest(profile, null, SessionState.OPEN, 4));
        assertThrows(
                NullPointerException.class,
                () -> SessionPermissions.permitsRequest(profile, EndpointRole.ESME, null, 4));
    }

    private static void assertPermission(
            ProtocolProfile profile, EndpointRole origin, SessionState state, long command, boolean expected) {
        assertEquals(
                expected,
                SessionPermissions.permitsRequest(profile, origin, state, command),
                profile.version() + " " + origin + " " + state + " " + Long.toHexString(command));
    }
}
