package kg.aidarbek.smpp.endpoint;

import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

/** Endpoint-level connection or binding failure with credential-free diagnostic metadata. */
public final class EndpointException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    /** Serialized local outcome category. */
    private final Reason reason;
    /** Serialized immutable connection generation. */
    private final UUID sessionId;
    /** Serialized unsigned wire status or -1 when absent. */
    private final long commandStatus;
    /** Serialized raw interface-version octet or -1 when absent. */
    private final int advertisement;

    /** Connection and bind outcomes; none implies retry or message rejection. */
    public enum Reason {
        /** An endpoint admission bound is full. */
        CAPACITY,
        /** The endpoint or session closed before binding. */
        CLOSED,
        /** Explicit cancellation won before the transport was ready to send a bind. */
        CANCELLED,
        /** No complete successful bind was established before its deadline. */
        BIND_TIMEOUT,
        /** The peer returned a negative bind response. */
        BIND_REJECTED,
        /** The peer advertisement did not satisfy the explicit client requirement. */
        VERSION_REJECTED,
        /** A response or frame violated the supported wire contract. */
        PROTOCOL,
        /** A peer returned a negative response to an automatic link enquiry. */
        KEEPALIVE_REJECTED,
        /** TCP connection or transport progress failed. */
        TRANSPORT
    }

    EndpointException(Reason reason, UUID sessionId, long commandStatus, int advertisement) {
        super("Endpoint " + reason + " [session=" + sessionId + ", status=" + commandStatus + ", advertisement="
                + advertisement + "]");
        this.reason = reason;
        this.sessionId = sessionId;
        this.commandStatus = commandStatus;
        this.advertisement = advertisement;
    }

    /** Returns the endpoint outcome.
     * @return structured reason */
    public Reason reason() {
        return reason;
    }
    /** Returns the connection identity.
     * @return immutable session identity */
    public UUID sessionId() {
        return sessionId;
    }
    /** Returns a received negative wire status when available.
     * @return raw status, otherwise empty */
    public OptionalLong commandStatus() {
        return commandStatus < 0 ? OptionalLong.empty() : OptionalLong.of(commandStatus);
    }
    /** Returns the raw bind advertisement when available, including unsupported values.
     * @return raw advertisement */
    public OptionalInt advertisement() {
        return advertisement < 0 ? OptionalInt.empty() : OptionalInt.of(advertisement);
    }
}
