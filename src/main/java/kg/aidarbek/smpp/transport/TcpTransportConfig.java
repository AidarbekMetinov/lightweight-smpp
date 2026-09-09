package kg.aidarbek.smpp.transport;

/**
 * Explicit per-connection allocation and admission bounds.
 * @param maximumFrameLength maximum complete frame size, at least 16
 * @param ordinaryWriteCount ordinary queued plus in-flight count, nonnegative
 * @param ordinaryWriteBytes ordinary queued plus in-flight bytes, nonnegative
 * @param controlWriteCount control count, at least one
 * @param controlWriteBytes control bytes, at least 16
 * @param sendBufferBytes requested socket send-buffer size, or zero for the JDK default
 */
public record TcpTransportConfig(
        int maximumFrameLength,
        int ordinaryWriteCount,
        long ordinaryWriteBytes,
        int controlWriteCount,
        long controlWriteBytes,
        int sendBufferBytes) {
    /** Creates explicitly configured bounds. */
    public TcpTransportConfig {
        if (maximumFrameLength < 16
                || ordinaryWriteCount < 0
                || ordinaryWriteBytes < 0
                || controlWriteCount < 1
                || controlWriteBytes < 16
                || sendBufferBytes < 0) throw new IllegalArgumentException("Invalid TCP frame or admission bound");
    }

    /**
     * Returns provisional defaults: 1 MiB frames, 32/1 MiB ordinary and 8/64 KiB control capacity.
     * @return independent immutable configuration value
     */
    public static TcpTransportConfig defaults() {
        return new TcpTransportConfig(1048576, 32, 1048576, 8, 65536, 0);
    }
}
