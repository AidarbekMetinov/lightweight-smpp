package kg.aidarbek.smpp.spi;

/** Internal, fast, nonblocking lifecycle and ordered frame callbacks; never called under a transport lock. */
public interface FrameListener {
    /** Called before any frames, after connecting or adopting an already connected transport. */
    void connected();

    /**
     * Receives one complete frame whose byte array belongs to the listener.
     * @param frame independently owned complete frame
     */
    void frame(byte[] frame);

    /**
     * Receives the single terminal connection reason; may race an already running frame callback.
     * @param failure terminal local connection reason
     */
    void closed(TransportFailure failure);
}
