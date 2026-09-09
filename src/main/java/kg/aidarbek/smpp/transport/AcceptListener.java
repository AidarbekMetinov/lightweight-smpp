package kg.aidarbek.smpp.transport;

import java.net.SocketAddress;
import kg.aidarbek.smpp.spi.FrameTransport;

/** Fast, nonblocking connection admission at the concrete TCP composition boundary. */
@FunctionalInterface
public interface AcceptListener {
    /**
     * Receives an unstarted transport and peer address. Return true to take ownership; false or an
     * exception closes it. Reserve endpoint connection capacity before returning true. No application
     * authentication or other blocking work belongs in this callback. Listener closure racing this
     * provisional handoff may still close it; true transfers ownership only while the listener is open.
     * @param transport unstarted transport with a connected, owned socket
     * @param peer connected peer address
     * @return true to take ownership, false to reject and let the listener finish cleanup
     */
    boolean accept(FrameTransport transport, SocketAddress peer);
}
