package kg.aidarbek.smpp.request;

import java.util.ArrayList;
import java.util.UUID;
import kg.aidarbek.smpp.protocol.ControlCommand;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.Tlv;

/** A correlated generic_nack: a known peer outcome, never an ambiguous local request failure. */
public final class PeerNackException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    /** Immutable connection identity. */
    private final UUID generation;
    /** Correlated local sequence retained for serialization. */
    private final long sequenceNumber;
    /** Original unsigned peer status. */
    private final long status;
    /** Ordered raw TLV tags stored without nonserializable protocol values. */
    private final int[] tags;
    /** Owned binary TLV values corresponding to tags. */
    private final byte[][] values;

    PeerNackException(RequestIdentity identity, Pdu<ControlCommand> nack) {
        super("Peer generic_nack [generation=" + identity.generation() + ", sequence=" + identity.sequenceNumber()
                + ", status=0x" + Long.toHexString(nack.commandStatus()) + "]");
        generation = identity.generation();
        sequenceNumber = identity.sequenceNumber();
        status = nack.commandStatus();
        var tlvs = nack.command().optionalParameters().entries();
        tags = new int[tlvs.size()];
        values = new byte[tlvs.size()][];
        for (int index = 0; index < tlvs.size(); index++) {
            tags[index] = tlvs.get(index).tag();
            values[index] = tlvs.get(index).value();
        }
    }
    /**
     * Returns the correlated local identity.
     * @return immutable identity
     */
    public RequestIdentity requestIdentity() {
        return new RequestIdentity(generation, sequenceNumber);
    }
    /**
     * Reconstructs the immutable received nack, retaining raw status and ordered TLVs.
     * @return received peer outcome
     */
    public Pdu<ControlCommand> nack() {
        var tlvs = new ArrayList<Tlv>(tags.length);
        for (int index = 0; index < tags.length; index++) {
            tlvs.add(new Tlv(tags[index], values[index]));
        }
        return new Pdu<>(
                status,
                sequenceNumber,
                new ControlCommand(ControlCommand.Type.GENERIC_NACK, new OptionalParameters(tlvs)));
    }
}
