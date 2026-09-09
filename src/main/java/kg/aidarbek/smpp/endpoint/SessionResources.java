package kg.aidarbek.smpp.endpoint;

/** Immutable sampled ownership counts; these are not transport queue or aggregate endpoint metrics.
 * @param pendingRequests requests reserved in this generation's shared request window, including controls
 * @param pendingRequestBytes encoded request bytes reserved by those requests
 * @param pendingReplies paired application replies awaiting handler, order or write settlement
 * @param retainedReplyBytes received-frame and fallback/ready response bytes reserved by those replies */
public record SessionResources(
        int pendingRequests, long pendingRequestBytes, int pendingReplies, long retainedReplyBytes) {
    /** Rejects negative resource accounting. */
    public SessionResources {
        if (pendingRequests < 0 || pendingRequestBytes < 0 || pendingReplies < 0 || retainedReplyBytes < 0)
            throw new IllegalArgumentException("Resource observations cannot be negative");
    }
}
