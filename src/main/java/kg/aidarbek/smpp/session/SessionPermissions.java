package kg.aidarbek.smpp.session;

import java.util.Objects;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;

/**
 * Pure, thread-safe request permissions from SMPP 3.4 section 2.3 and SMPP 5.0 section 2.4.
 *
 * <p>Catalogue permission does not advertise local codecs, application handlers, negotiated field
 * features or peer acceptance. Error responses and crossing unbind requests require lifecycle context
 * and are deliberately not authorized by this request table.
 */
public final class SessionPermissions {
    private SessionPermissions() {}

    /**
     * Tests the request origin and lifecycle context in the selected specification.
     *
     * @param profile explicit specification profile
     * @param origin endpoint issuing the request
     * @param state current lifecycle state
     * @param commandId exact unsigned request identity
     * @return whether the request is permitted by the profile's operation table
     * @throws NullPointerException if profile, origin or state is null
     */
    public static boolean permitsRequest(
            ProtocolProfile profile, EndpointRole origin, SessionState state, long commandId) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(state, "state");
        if (!profile.definesCommand(commandId) || (commandId & 0x80000000L) != 0) {
            return false;
        }
        boolean version5 = profile.version() == SmppVersion.V5_0;
        boolean transmitter = state == SessionState.BOUND_TX || state == SessionState.BOUND_TRX;
        boolean receiver = state == SessionState.BOUND_RX || state == SessionState.BOUND_TRX;
        return switch ((int) commandId) {
            case 0x1, 0x2, 0x9 ->
                origin == EndpointRole.ESME
                        && (state == SessionState.OPEN || (version5 && state == SessionState.OUTBOUND));
            case 0xb -> origin == EndpointRole.MESSAGE_CENTER && state == SessionState.OPEN;
            case 0x3, 0x4, 0x8, 0x21, 0x111, 0x112, 0x113 -> origin == EndpointRole.ESME && transmitter;
            case 0x5, 0x102 -> origin == EndpointRole.MESSAGE_CENTER && receiver;
            case 0x7 ->
                origin == EndpointRole.ESME
                        && (state == SessionState.BOUND_TX || (version5 && state == SessionState.BOUND_TRX));
            case 0x103 -> version5 ? (origin == EndpointRole.ESME ? transmitter : receiver) : transmitter || receiver;
            case 0x6 -> transmitter || receiver;
            case 0x15 ->
                transmitter
                        || receiver
                        || (version5
                                && (state == SessionState.OPEN
                                        || state == SessionState.OUTBOUND
                                        || state == SessionState.BINDING
                                        || state == SessionState.UNBINDING));
            default -> false;
        };
    }
}
