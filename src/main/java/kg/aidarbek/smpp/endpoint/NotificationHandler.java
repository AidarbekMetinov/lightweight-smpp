package kg.aidarbek.smpp.endpoint;

import java.util.concurrent.CompletionStage;
import kg.aidarbek.smpp.protocol.Command;

/** Focused one-way application hook, invoked under the endpoint's bounded physical handler policy.
 * @param <Q> immutable notification command */
@FunctionalInterface
public interface NotificationHandler<Q extends Command> {
    /** Handles a notification without emitting a response PDU. Concurrent sessions may invoke this hook.
     * The endpoint retains physical capacity until invocation and returned stage finish, including
     * after logical cancellation. A failed stage closes that session; an absent handler ignores input.
     * @param notification immutable received context with cooperative cancellation
     * @return non-null stage for local application completion, with no peer acknowledgement semantics */
    CompletionStage<Void> handle(IncomingNotification<Q> notification);
}
