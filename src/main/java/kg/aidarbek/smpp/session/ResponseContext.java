package kg.aidarbek.smpp.session;

import java.util.Objects;
import kg.aidarbek.smpp.protocol.PduHeader;

/**
 * Immutable original-request context supplied by the owner of correlation or protocol-error handling.
 *
 * <p>This value is not a pending-request token or proof of correlation. The request owner must verify
 * session generation, outstanding status and uniqueness before authorizing an ordinary response,
 * and must settle its own pending result afterward. For protocol errors, the header is the offending
 * PDU and may itself contain invalid command, status or sequence values. An unusable original
 * sequence allows only a negative generic_nack with sequence zero. A completely undecodable header
 * cannot supply this context: the command's request/response bit must be known, not invented.
 * No bytes or resources are retained. Instances have record equality and are safe to share between threads.
 *
 * @param requestDirection original direction relative to the same local endpoint
 * @param request independently decoded original header
 */
public record ResponseContext(PduDirection requestDirection, PduHeader request) {
    /**
     * Requires both context values while retaining all raw header values.
     *
     * @throws NullPointerException if either value is null
     */
    public ResponseContext {
        Objects.requireNonNull(requestDirection, "requestDirection");
        Objects.requireNonNull(request, "request");
    }
}
