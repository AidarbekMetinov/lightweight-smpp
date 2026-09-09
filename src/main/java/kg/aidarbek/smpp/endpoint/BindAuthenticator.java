package kg.aidarbek.smpp.endpoint;

import java.net.SocketAddress;
import java.util.concurrent.CompletionStage;
import kg.aidarbek.smpp.protocol.BindRequest;

/**
 * Application authentication, invoked outside transport progress with bounded concurrency.
 * Implementations may complete asynchronously; authentication does not grant protocol capabilities.
 */
@FunctionalInterface
public interface BindAuthenticator {
    /**
     * Authenticates one immutable received bind. Never log its credentials.
     *
     * @param request received credentials, mode and raw requested version
     * @param peer remote socket identity
     * @return non-null asynchronous decision; failure rejects the bind
     */
    CompletionStage<BindDecision> authenticate(BindRequest request, SocketAddress peer);
}
