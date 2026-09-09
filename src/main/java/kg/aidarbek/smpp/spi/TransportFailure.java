package kg.aidarbek.smpp.spi;

import java.io.Serial;
import java.util.Objects;

/** Payload-free local transport outcome; protocol response status remains a separate concern. */
public final class TransportFailure extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Local reason, without claiming a remote protocol outcome. */
    public enum Kind {
        /** Explicit closure, or attempted admission outside the started lifetime. */
        CLOSED,
        /** Peer closed between complete frames. */
        EOF,
        /** Invalid frame prefix, over-limit frame, or EOF within a frame. */
        MALFORMED_FRAME,
        /** Connected socket read failed. */
        READ_FAILED,
        /** Connection establishment failed. */
        CONNECT_FAILED,
        /** Connection deadline expired. */
        CONNECT_TIMEOUT,
        /** Physical output failed after the writer claimed the guard. */
        WRITE_FAILED,
        /** Admission, queued work, or the active write exceeded its absolute deadline. */
        WRITE_TIMEOUT,
        /** Queued cancellation won before the writer claimed the guard. */
        CANCELLED,
        /** The internal transmission guard refused the write. */
        REJECTED,
        /** The selected write class has no available count or byte capacity. */
        FULL,
        /** An internal lifecycle, frame, or write observer threw. */
        OBSERVER_FAILED,
        /** Accepting new connections failed unexpectedly. */
        LISTEN_FAILED,
        /** Physical close or a terminal internal callback failed during cleanup. */
        CLEANUP_FAILED
    }

    /** Local failure category retained by Java exception serialization. */
    private final Kind kind;
    /** Conservative writer-claim flag retained by Java exception serialization. */
    private final boolean writeStarted;

    /**
     * Creates a failure whose generated message is only its category; the cause is caller supplied.
     * @param kind local failure category
     * @param writeStarted whether a writer has claimed the guard, conservatively implying ambiguity
     * @param cause optional underlying local cause
     */
    public TransportFailure(Kind kind, boolean writeStarted, Throwable cause) {
        super(Objects.requireNonNull(kind, "kind").name(), cause);
        this.kind = kind;
        this.writeStarted = writeStarted;
    }

    /**
     * Returns the local failure category.
     * @return local reason
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Returns whether the writer claimed the beforeWrite guard, conservatively implying ambiguity.
     * The guard may subsequently refuse transmission; this flag alone is not request certainty.
     * @return whether the writer had claimed this operation when failure was selected
     */
    public boolean writeStarted() {
        return writeStarted;
    }
}
