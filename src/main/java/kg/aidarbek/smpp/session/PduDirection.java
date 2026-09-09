package kg.aidarbek.smpp.session;

import java.util.Objects;

/** Direction relative to the endpoint owning the state machine, independent of numeric sequence. */
public enum PduDirection {
    /** The local endpoint sends the PDU. */
    OUTBOUND,
    /** The local endpoint receives the PDU. */
    INBOUND;

    /**
     * Returns the opposite wire direction.
     *
     * @return direction of a corresponding response
     */
    public PduDirection opposite() {
        return this == OUTBOUND ? INBOUND : OUTBOUND;
    }

    /**
     * Resolves the SMPP role originating a PDU in this direction.
     *
     * @param localRole role of the owning endpoint
     * @return actual origin role
     * @throws NullPointerException if localRole is null
     */
    public EndpointRole origin(EndpointRole localRole) {
        Objects.requireNonNull(localRole, "localRole");
        return this == OUTBOUND
                ? localRole
                : localRole == EndpointRole.ESME ? EndpointRole.MESSAGE_CENTER : EndpointRole.ESME;
    }
}
