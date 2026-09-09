package kg.aidarbek.smpp.endpoint;

import java.net.SocketAddress;
import java.util.concurrent.CompletionStage;
import kg.aidarbek.smpp.protocol.Outbind;

/** Optional-service prerequisite for accepting MC credentials before an ESME sends its configured bind. */
@FunctionalInterface
public interface OutbindAuthenticator {
    /** Authenticates one received outbind on bounded off-I/O handler workers.
     * The total bind deadline includes this decision; a late result cannot reopen the connection.
     * Physical capacity remains retained until the invocation and returned stage complete.
     * @param request immutable MC credentials
     * @param peer remote socket identity
     * @return true to send the configured bind once on this socket, false to close without replying */
    CompletionStage<Boolean> authenticate(Outbind request, SocketAddress peer);
}
