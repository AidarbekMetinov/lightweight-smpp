package kg.aidarbek.smpp.endpoint;

import java.util.concurrent.CompletionStage;
import kg.aidarbek.smpp.protocol.Command;

/**
 * One typed application acceptance decision, invoked on bounded owned workers outside transport progress and locks.
 * Invocation starts follow each session's receive order; asynchronous stages may overlap. A registry may serve
 * several sessions concurrently. Complete acceptance only after application-required storage or other work succeeds.
 * Throwing, returning null, exceptional completion and deadline expiry produce a paired system-error response.
 * @param <Q> immutable incoming request representation
 * @param <R> corresponding immutable response representation
 */
@FunctionalInterface
public interface RequestHandler<Q extends Command, R extends Command> {
    /** Handles one peer request; protocol acceptance does not imply handset delivery.
     * Expiry/closure sets cooperative cancellation and suppresses late output without interrupting application code.
     * Capacity remains retained until this invocation and its returned stage physically finish.
     * @param request immutable PDU, session and decision deadline/cancellation context
     * @return non-null asynchronous response body/status decision */
    CompletionStage<HandlerResponse<R>> handle(IncomingRequest<Q> request);
}
