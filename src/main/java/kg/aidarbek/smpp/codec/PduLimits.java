package kg.aidarbek.smpp.codec;

import kg.aidarbek.smpp.protocol.PduHeader;

/**
 * Immutable allocation limits used by full-PDU and command-body codecs.
 *
 * @param maximumPduLength maximum complete frame length, including the 16-octet header
 * @param maximumTlvLength maximum aggregate trailing TLV bytes, including TLV headers
 * @param maximumTlvCount maximum number of raw TLV entries, including repetitions
 */
public record PduLimits(int maximumPduLength, int maximumTlvLength, int maximumTlvCount) {
    /**
     * Checks that the frame can hold a header and all subordinate limits are nonnegative.
     * @throws IllegalArgumentException if a limit is invalid or the TLV byte limit exceeds the body limit
     */
    public PduLimits {
        if (maximumPduLength < PduHeader.LENGTH
                || maximumTlvLength < 0
                || maximumTlvLength > maximumPduLength - PduHeader.LENGTH
                || maximumTlvCount < 0) {
            throw new IllegalArgumentException("Invalid PDU/TLV allocation limits");
        }
    }
    /**
     * Returns the maximum body allocation allowed by the complete-frame bound.
     *
     * @return maximumPduLength minus the header length
     */
    public int maximumBodyLength() {
        return maximumPduLength - PduHeader.LENGTH;
    }
}
