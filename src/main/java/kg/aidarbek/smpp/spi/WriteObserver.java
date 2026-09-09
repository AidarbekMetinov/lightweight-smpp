package kg.aidarbek.smpp.spi;

/** Internal fast, nonblocking callbacks used by request tracking; application dispatch is separate. */
public interface WriteObserver {
    /**
     * Called at most once immediately before the physical write. Atomically mark possible transmission
     * and return true, or return false to prevent all bytes (for example, if request cancellation won).
     * A true result is conservative: closure or a deadline may still prevent the write.
     * @return whether transmission is still permitted by the owning request or control operation
     */
    boolean beforeWrite();

    /** Local output completed; this does not prove peer receipt or protocol acceptance. */
    void written();

    /**
     * Accepted work failed once; writeStarted distinguishes queued work from a claimed write guard.
     * @param failure terminal local write reason
     */
    void failed(TransportFailure failure);
}
