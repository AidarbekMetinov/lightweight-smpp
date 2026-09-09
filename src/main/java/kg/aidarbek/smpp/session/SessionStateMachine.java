package kg.aidarbek.smpp.session;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.BindMode;
import kg.aidarbek.smpp.protocol.PduHeader;

/**
 * Deterministic, single-owner session lifecycle and permission policy with no network resources.
 *
 * <p>Callers serialize all events. This type is not thread-safe, parses no bytes, authenticates no
 * identity and performs no I/O. A new connection requires a new instance. Only bind and the two
 * possible unbind exchanges have internal request identities; ordinary correlation belongs to the
 * separate request owner. Input errors and rejected events are atomic. Immutable capability snapshots
 * and version results may be retained, but snapshots never authorize a later send without a recheck.
 *
 * <p>An accepted outbound event authorizes its caller's next ordered write. If a bind or unbind
 * response enters CLOSED, the owner must still transmit that accepted final response and finish
 * its bounded flush before closing the connection. Write failure calls {@link #close()}; these
 * methods neither schedule writes nor certify transmission. Incoming values must already have been
 * decoded and validated. Raw unknown or unexpected TLVs remain the codec's responsibility.
 */
public final class SessionStateMachine {
    private static final long RESPONSE_MASK = 0x80000000L;
    private static final long GENERIC_NACK = 0x80000000L;
    private static final long UNBIND = 0x00000006L;
    private static final long UNBIND_RESPONSE = 0x80000006L;
    private static final long OUTBIND = 0x0000000bL;
    private static final long MAX_SEQUENCE = 0x7fffffffL;
    private final EndpointRole role;
    private final SmppVersion configuredVersion;
    private final Set<SmppVersion> acceptedVersions;
    private final boolean requireAdvertisement;
    private final Set<Long> implementedRequests;
    private SessionState state = SessionState.CONNECTING;
    private BindMode bindMode;
    private long bindSequence;
    private int requestedInterfaceVersion;
    private VersionNegotiation negotiation;
    private long localUnbindSequence;
    private long peerUnbindSequence;
    private boolean localUnbindStarted;
    private boolean peerUnbindStarted;

    private SessionStateMachine(
            EndpointRole role,
            SmppVersion configuredVersion,
            Set<SmppVersion> acceptedVersions,
            boolean requireAdvertisement,
            Set<Long> implementedRequests) {
        this.role = role;
        this.configuredVersion = Objects.requireNonNull(configuredVersion, "configuredVersion");
        this.acceptedVersions = role == EndpointRole.MESSAGE_CENTER
                ? VersionNegotiation.validateAcceptedVersions(acceptedVersions, configuredVersion)
                : Set.copyOf(acceptedVersions);
        this.requireAdvertisement = requireAdvertisement;
        this.implementedRequests = Set.copyOf(implementedRequests);
        ProtocolProfile catalogue = ProtocolProfile.forVersion(SmppVersion.V5_0);
        for (long commandId : this.implementedRequests) {
            if (!catalogue.definesCommand(commandId) || (commandId & RESPONSE_MASK) != 0) {
                throw new IllegalArgumentException("Implemented commands must be defined request identities");
            }
        }
    }

    /**
     * Creates an ESME lifecycle with an explicit requested version.
     *
     * @param requestedVersion version the ESME will request
     * @param requireAdvertisement whether a missing advertisement must fail binding
     * @param implementedRequests locally implemented request IDs, copied on construction
     * @return a new connecting lifecycle
     * @throws NullPointerException if a required value or set member is null
     * @throws IllegalArgumentException if an implementation ID is not a defined request
     */
    public static SessionStateMachine esme(
            SmppVersion requestedVersion, boolean requireAdvertisement, Set<Long> implementedRequests) {
        return new SessionStateMachine(
                EndpointRole.ESME,
                requestedVersion,
                Set.of(requestedVersion),
                requireAdvertisement,
                implementedRequests);
    }

    /**
     * Creates a message-center lifecycle with independent acceptance and advertisement policies.
     *
     * @param acceptedVersions explicitly accepted client versions, copied on construction
     * @param advertisedVersion implemented version advertised on every successful bind response
     * @param implementedRequests locally implemented request IDs, copied on construction
     * @return a new connecting lifecycle
     * @throws NullPointerException if a required value or set member is null
     * @throws IllegalArgumentException if the accepted set is empty, exceeds the advertisement,
     *     or an implementation ID is not a defined request
     */
    public static SessionStateMachine messageCenter(
            Set<SmppVersion> acceptedVersions, SmppVersion advertisedVersion, Set<Long> implementedRequests) {
        return new SessionStateMachine(
                EndpointRole.MESSAGE_CENTER, advertisedVersion, acceptedVersions, false, implementedRequests);
    }

    /**
     * Returns the current lifecycle state.
     *
     * @return current state
     */
    public SessionState state() {
        return state;
    }

    /**
     * Returns the version decision after a successful wire bind response was evaluated.
     *
     * @return absent before evaluation or after a negative wire bind result
     */
    public Optional<VersionNegotiation> negotiation() {
        return Optional.ofNullable(negotiation);
    }

    /**
     * Records establishment of the connection without creating a socket.
     *
     * @return accepted only from CONNECTING
     */
    public SessionDecision connected() {
        if (state != SessionState.CONNECTING) {
            return SessionDecision.INVALID_STATE;
        }
        state = SessionState.OPEN;
        return SessionDecision.ACCEPTED;
    }

    /**
     * Records a bind request whose fields were independently validated.
     *
     * <p>Only ESME-originated binds are admitted. A message center records even an unaccepted raw
     * requested version so that its negative bind response can terminate that exchange. It cannot
     * subsequently authorize a successful response for that version. Duplicate binds do not replace
     * the original request; their negative protocol replies use {@link #protocolErrorPermission}.
     *
     * @param direction direction relative to the local endpoint
     * @param mode requested bind mode
     * @param requestedVersion raw received or emitted interface-version octet
     * @param sequenceNumber request sequence in 1..0x7fffffff
     * @return admission decision
     * @throws NullPointerException if direction or mode is null
     * @throws IllegalArgumentException if the version is outside 0..255 or sequence outside 1..0x7fffffff
     */
    public SessionDecision bindRequest(
            PduDirection direction, BindMode mode, int requestedVersion, long sequenceNumber) {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(mode, "mode");
        requireSequence(sequenceNumber);
        requireOctet(requestedVersion);
        if (roleOf(direction) != EndpointRole.ESME) {
            return SessionDecision.INVALID_DIRECTION;
        }
        if (!SessionPermissions.permitsRequest(
                ProtocolProfile.forVersion(configuredVersion), roleOf(direction), state, mode.requestCommandId())) {
            return SessionDecision.INVALID_STATE;
        }
        if (!implementedRequests.contains(mode.requestCommandId())) {
            return SessionDecision.COMMAND_NOT_IMPLEMENTED;
        }
        if (role == EndpointRole.ESME && requestedVersion != configuredVersion.interfaceVersion()) {
            return SessionDecision.CAPABILITY_UNAVAILABLE;
        }
        bindMode = mode;
        bindSequence = sequenceNumber;
        requestedInterfaceVersion = requestedVersion;
        state = SessionState.BINDING;
        return SessionDecision.ACCEPTED;
    }

    /**
     * Records a matching bind response, including its raw optional version advertisement.
     *
     * <p>Matching uses command, sequence and endpoint direction; a negative generic_nack is also a
     * terminal bind rejection. A negative wire status closes the lifecycle. An ESME also closes on a
     * version-policy failure while retaining the failed negotiation. A message center refuses an
     * inconsistent proposed success without mutation, allowing a corrected or negative response.
     *
     * @param direction response direction relative to the local endpoint
     * @param response independently decoded response header
     * @param advertisement raw sc_interface_version octet, if present
     * @return accepted binding, terminal rejection, or atomic unexpected-response decision
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if a present advertisement is outside 0..255
     */
    public SessionDecision bindResponse(PduDirection direction, PduHeader response, OptionalInt advertisement) {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(advertisement, "advertisement");
        advertisement.ifPresent(SessionStateMachine::requireOctet);
        boolean negativeAcknowledgement = response.commandId() == GENERIC_NACK && response.commandStatus() != 0;
        if (state != SessionState.BINDING
                || roleOf(direction) != EndpointRole.MESSAGE_CENTER
                || (response.commandId() != bindMode.responseCommandId() && !negativeAcknowledgement)
                || response.sequenceNumber() != bindSequence) {
            return SessionDecision.UNEXPECTED_RESPONSE;
        }
        if (response.commandStatus() != 0) {
            close();
            return SessionDecision.BIND_REJECTED;
        }
        VersionNegotiation evaluated = role == EndpointRole.ESME
                ? VersionNegotiation.forEsme(configuredVersion, advertisement, requireAdvertisement)
                : VersionNegotiation.forMessageCenter(requestedInterfaceVersion, acceptedVersions, configuredVersion);
        if (role == EndpointRole.MESSAGE_CENTER
                && (!advertisement.equals(evaluated.advertisement())
                        || evaluated.effectiveProfile().isEmpty())) {
            return SessionDecision.CAPABILITY_UNAVAILABLE;
        }
        negotiation = evaluated;
        if (evaluated.effectiveProfile().isEmpty()) {
            close();
            return SessionDecision.VERSION_REJECTED;
        }
        state = boundState(bindMode);
        return SessionDecision.ACCEPTED;
    }

    /**
     * Aborts the logical lifecycle idempotently without closing caller-owned resources.
     *
     * <p>The endpoint owner must close its connection and settle its separately owned requests.
     */
    public void close() {
        state = SessionState.CLOSED;
        bindMode = null;
        bindSequence = 0;
        localUnbindSequence = 0;
        peerUnbindSequence = 0;
        localUnbindStarted = false;
        peerUnbindStarted = false;
    }

    /**
     * Checks one request without changing state or reserving a sequence or window slot.
     *
     * <p>Implementation declarations describe both processing directions for each operation,
     * including its response support. They must come from actual endpoint composition, not profile
     * catalogue membership. Outbound requirements constrain TLVs and field features. For incoming
     * traffic these requirements are ignored: command validation handles fields, and unknown raw
     * extensions do not negotiate any outgoing capability. A bind still requires explicit metadata
     * through {@link #bindRequest}; its request permission alone does not authorize a bind.
     *
     * @param direction proposed request direction
     * @param commandId exact request command ID
     * @param requirements actual outbound field requirements; non-null and ignored for input
     * @return current permission, including local implementation and version restrictions
     * @throws NullPointerException if direction or requirements is null
     */
    public SessionDecision requestPermission(PduDirection direction, long commandId, SendRequirements requirements) {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(requirements, "requirements");
        if (!implementedRequests.contains(commandId)) {
            return SessionDecision.COMMAND_NOT_IMPLEMENTED;
        }
        boolean crossingUnbind = commandId == UNBIND
                && state == SessionState.UNBINDING
                && (direction == PduDirection.OUTBOUND
                        ? !localUnbindStarted && peerUnbindStarted
                        : !peerUnbindStarted && localUnbindStarted);
        if (!crossingUnbind
                && !SessionPermissions.permitsRequest(currentProfile(), roleOf(direction), state, commandId)) {
            return SessionDecision.INVALID_STATE;
        }
        return direction == PduDirection.INBOUND || permitsRequirements(requirements)
                ? SessionDecision.ACCEPTED
                : SessionDecision.CAPABILITY_UNAVAILABLE;
    }

    /**
     * Returns an immutable snapshot of request permissions for these field requirements.
     *
     * @param direction request direction
     * @param requirements actual outbound field requirements; non-null and ignored for input
     * @return implemented and currently permitted request IDs; every later send must recheck
     * @throws NullPointerException if direction or requirements is null
     */
    public Set<Long> availableRequests(PduDirection direction, SendRequirements requirements) {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(requirements, "requirements");
        Set<Long> permitted = new HashSet<>();
        for (long commandId : implementedRequests) {
            if (requestPermission(direction, commandId, requirements) == SessionDecision.ACCEPTED) {
                permitted.add(commandId);
            }
        }
        return Set.copyOf(permitted);
    }

    /**
     * Records a validated non-bind request. Ordinary traffic does not mutate the lifecycle.
     *
     * <p>Unbind stops admission of new application requests; one crossing unbind in the opposite
     * namespace remains possible. A declared outbind moves 5.0 to OUTBOUND and leaves 3.4 OPEN,
     * following their operation tables. This provides no connector, listener or outbind codec.
     *
     * @param direction request direction
     * @param request independently validated request header
     * @param requirements actual outbound field requirements; non-null and ignored for input
     * @return admission decision, without creating any general pending-request entry
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException for nonzero request status, an invalid request sequence,
     *     a response command, or a bind command requiring the separate metadata method
     */
    public SessionDecision request(PduDirection direction, PduHeader request, SendRequirements requirements) {
        Objects.requireNonNull(request, "request");
        requireSequence(request.sequenceNumber());
        if ((request.commandId() & RESPONSE_MASK) != 0 || request.commandStatus() != 0) {
            throw new IllegalArgumentException("A request must have a request command ID and zero status");
        }
        if (isBind(request.commandId())) {
            throw new IllegalArgumentException("Bind requests require explicit version metadata through bindRequest");
        }
        SessionDecision permission = requestPermission(direction, request.commandId(), requirements);
        if (permission == SessionDecision.ACCEPTED && request.commandId() == UNBIND) {
            if (direction == PduDirection.OUTBOUND) {
                localUnbindSequence = request.sequenceNumber();
                localUnbindStarted = true;
            } else {
                peerUnbindSequence = request.sequenceNumber();
                peerUnbindStarted = true;
            }
            state = SessionState.UNBINDING;
        } else if (permission == SessionDecision.ACCEPTED
                && request.commandId() == OUTBIND
                && currentProfile().version() == SmppVersion.V5_0) {
            state = SessionState.OUTBOUND;
        }
        return permission;
    }

    /**
     * Records the response to an internally tracked local or peer unbind request.
     *
     * <p>Crossed requests may share a numeric sequence and require separate directional responses.
     * After the first successful reply, no replacement unbind in that direction is allowed. The
     * final successful reply closes the lifecycle. Any matching negative unbind response or
     * generic_nack also closes; a failed unbind never silently restores application permissions.
     *
     * @param direction response direction
     * @param response independently decoded header
     * @param requirements actual outbound response field requirements; non-null and ignored for input
     * @return accepted response or atomic unexpected-response rejection
     * @throws NullPointerException if any argument is null
     */
    public SessionDecision unbindResponse(PduDirection direction, PduHeader response, SendRequirements requirements) {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(requirements, "requirements");
        long expectedSequence = direction == PduDirection.INBOUND ? localUnbindSequence : peerUnbindSequence;
        boolean negativeAcknowledgement = response.commandId() == GENERIC_NACK && response.commandStatus() != 0;
        if (state != SessionState.UNBINDING
                || expectedSequence == 0
                || response.sequenceNumber() != expectedSequence
                || (response.commandId() != UNBIND_RESPONSE && !negativeAcknowledgement)) {
            return SessionDecision.UNEXPECTED_RESPONSE;
        }
        if (direction == PduDirection.OUTBOUND && !permitsRequirements(requirements)) {
            return SessionDecision.CAPABILITY_UNAVAILABLE;
        }
        if (direction == PduDirection.INBOUND) {
            localUnbindSequence = 0;
        } else {
            peerUnbindSequence = 0;
        }
        if (response.commandStatus() != 0 || (localUnbindSequence == 0 && peerUnbindSequence == 0)) {
            close();
        }
        return SessionDecision.ACCEPTED;
    }

    /**
     * Checks an ordinary response using context from the separate correlation owner.
     *
     * <p>The context must describe a currently outstanding request from this session generation.
     * This method checks its numeric and directional consistency, but does not prove outstanding
     * status, consume the context or prevent reuse. The request owner supplies empty context for
     * unsolicited, duplicate or late responses. Accepted message replies may drain during unbinding;
     * only new requests are stopped. Bind/unbind responses require their lifecycle methods.
     *
     * @param direction response direction
     * @param response independently decoded response header
     * @param context matched original request, empty for an unsolicited response
     * @param requirements actual outbound response field requirements; non-null and ignored for input
     * @return permission without mutating lifecycle or any pending outcome
     * @throws NullPointerException if any argument is null
     */
    public SessionDecision responsePermission(
            PduDirection direction,
            PduHeader response,
            Optional<ResponseContext> context,
            SendRequirements requirements) {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(requirements, "requirements");
        if (context.isEmpty() || !responseMatches(direction, response, context.orElseThrow())) {
            return SessionDecision.UNEXPECTED_RESPONSE;
        }
        ResponseContext original = context.orElseThrow();
        PduHeader request = original.request();
        long command = request.commandId();
        if (isBind(command)
                || command == UNBIND
                || !currentProfile().definesCommand(command)
                || !currentProfile().definesCommand(command | RESPONSE_MASK)
                || request.sequenceNumber() < 1
                || request.sequenceNumber() > MAX_SEQUENCE
                || request.commandStatus() != 0) {
            return SessionDecision.UNEXPECTED_RESPONSE;
        }
        if (!implementedRequests.contains(command)) {
            return SessionDecision.COMMAND_NOT_IMPLEMENTED;
        }
        SessionState permissionState = state == SessionState.UNBINDING ? boundState(bindMode) : state;
        if (!SessionPermissions.permitsRequest(
                currentProfile(), roleOf(original.requestDirection()), permissionState, command)) {
            return SessionDecision.INVALID_STATE;
        }
        return direction == PduDirection.INBOUND || permitsRequirements(requirements)
                ? SessionDecision.ACCEPTED
                : SessionDecision.CAPABILITY_UNAVAILABLE;
    }

    /**
     * Checks a negative protocol response to an offending request, including before binding.
     *
     * <p>The caller must already have established the protocol error. The offending PDU need not
     * have been admitted. A usable original sequence (1..0x7fffffff) must be echoed. An unusable
     * original sequence permits only a nonzero-status generic_nack with sequence zero; no high-bit
     * reply sequence is authorized. Responses travel in the opposite direction. A response is never
     * treated as an offending request, preventing nack loops. A completely undecodable header has
     * no context here; callers must not fabricate a request identity when even the command direction
     * bit is unknown. This is not a substitute for lifecycle completion or ordinary correlation.
     *
     * @param direction response direction
     * @param response proposed negative response header
     * @param offending original received or emitted request context
     * @param requirements actual outbound response field requirements; non-null and ignored for input
     * @return permission without accepting the offending request or changing lifecycle state
     * @throws NullPointerException if any argument is null
     */
    public SessionDecision protocolErrorPermission(
            PduDirection direction, PduHeader response, ResponseContext offending, SendRequirements requirements) {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(offending, "offending");
        Objects.requireNonNull(requirements, "requirements");
        if (response.commandStatus() == 0 || !responseMatches(direction, response, offending)) {
            return SessionDecision.UNEXPECTED_RESPONSE;
        }
        if (state == SessionState.CONNECTING || state == SessionState.CLOSED) {
            return SessionDecision.INVALID_STATE;
        }
        return direction == PduDirection.INBOUND || permitsRequirements(requirements)
                ? SessionDecision.ACCEPTED
                : SessionDecision.CAPABILITY_UNAVAILABLE;
    }

    private boolean responseMatches(PduDirection direction, PduHeader response, ResponseContext context) {
        PduHeader request = context.request();
        boolean genericNack = response.commandId() == GENERIC_NACK && response.commandStatus() != 0;
        boolean paired = response.commandId() != GENERIC_NACK
                && response.commandId() == (request.commandId() | RESPONSE_MASK)
                && currentProfile().definesCommand(response.commandId());
        boolean usableSequence = request.sequenceNumber() >= 1 && request.sequenceNumber() <= MAX_SEQUENCE;
        boolean sequenceMatches = usableSequence
                ? response.sequenceNumber() == request.sequenceNumber()
                : genericNack && response.sequenceNumber() == 0;
        return direction == context.requestDirection().opposite()
                && (request.commandId() & RESPONSE_MASK) == 0
                && (paired || genericNack)
                && sequenceMatches;
    }

    private static SessionState boundState(BindMode mode) {
        return switch (mode) {
            case RECEIVER -> SessionState.BOUND_RX;
            case TRANSMITTER -> SessionState.BOUND_TX;
            case TRANSCEIVER -> SessionState.BOUND_TRX;
        };
    }

    private static boolean isBind(long command) {
        return command == BindMode.RECEIVER.requestCommandId()
                || command == BindMode.TRANSMITTER.requestCommandId()
                || command == BindMode.TRANSCEIVER.requestCommandId();
    }

    private ProtocolProfile currentProfile() {
        if (negotiation != null && negotiation.effectiveProfile().isPresent()) {
            return negotiation.effectiveProfile().orElseThrow();
        }
        if (state == SessionState.BINDING && requestedInterfaceVersion == 0x34) {
            return ProtocolProfile.forVersion(SmppVersion.V3_4);
        }
        return ProtocolProfile.forVersion(configuredVersion);
    }

    private boolean permitsRequirements(SendRequirements requirements) {
        return negotiation != null
                ? negotiation.permits(requirements)
                : !requirements.usesOptionalParameters()
                        && requirements.minimumVersion().interfaceVersion()
                                <= currentProfile().version().interfaceVersion();
    }

    private EndpointRole roleOf(PduDirection direction) {
        return direction.origin(role);
    }

    private static void requireSequence(long sequence) {
        if (sequence < 1 || sequence > MAX_SEQUENCE) {
            throw new IllegalArgumentException("Request sequence must be in 1..0x7fffffff");
        }
    }

    private static void requireOctet(int value) {
        if (value < 0 || value > 0xff) {
            throw new IllegalArgumentException("Interface version must be an unsigned octet");
        }
    }
}
