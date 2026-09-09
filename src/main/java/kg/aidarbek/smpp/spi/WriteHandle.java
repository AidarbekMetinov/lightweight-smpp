package kg.aidarbek.smpp.spi;

/** Cancellation of a locally queued frame, independent of request-terminal cancellation. */
@FunctionalInterface
public interface WriteHandle {
    /**
     * Returns true only when cancellation wins before the writer claims the guard; then no bytes are
     * sent and failed(CANCELLED) occurs once. False never proves that peer acceptance occurred.
     * @return true only when this call wins queued cancellation
     */
    boolean cancel();
}
