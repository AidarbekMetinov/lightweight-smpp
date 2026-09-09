package kg.aidarbek.smpp.endpoint;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import kg.aidarbek.smpp.codec.CommandCodec;
import kg.aidarbek.smpp.profile.MessageDirection;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.session.SendRequirements;

/** A typed library-implemented request/response pairing, independent of local handler availability.
 * Library catalogues supply these immutable keys; a sender still checks negotiated profile, role and lifecycle.
 * @param <Q> immutable request representation
 * @param <R> corresponding immutable response representation */
public final class Operation<Q extends Command, R extends Command> {
    private final long commandId;
    private final Class<Q> requestType;
    private final Class<R> responseType;
    private final Function<MessageDirection, List<CommandCodec<?>>> codecs;
    private final BiFunction<Q, ProtocolProfile, R> negative;
    private final Predicate<Q> requestTlvs;
    private final Predicate<R> responseTlvs;
    private final ResponseValidator validator;

    Operation(
            long commandId,
            Class<Q> requestType,
            Class<R> responseType,
            Function<MessageDirection, List<CommandCodec<?>>> codecs,
            BiFunction<Q, ProtocolProfile, R> negative,
            Predicate<Q> requestTlvs,
            Predicate<R> responseTlvs,
            ResponseValidator validator) {
        this.commandId = commandId;
        this.requestType = Objects.requireNonNull(requestType);
        this.responseType = Objects.requireNonNull(responseType);
        this.codecs = Objects.requireNonNull(codecs);
        this.negative = Objects.requireNonNull(negative);
        this.requestTlvs = Objects.requireNonNull(requestTlvs);
        this.responseTlvs = Objects.requireNonNull(responseTlvs);
        this.validator = Objects.requireNonNull(validator);
    }
    /** Returns the outgoing request command identity.
     * @return unsigned request command ID */
    public long commandId() {
        return commandId;
    }
    /** Returns the corresponding operation response identity.
     * @return unsigned paired response command ID */
    public long responseCommandId() {
        return commandId | 0x80000000L;
    }
    /** Returns the immutable request representation.
     * @return request Java type */
    public Class<Q> requestType() {
        return requestType;
    }
    /** Returns the immutable response representation.
     * @return response Java type */
    public Class<R> responseType() {
        return responseType;
    }

    List<CommandCodec<?>> codecs(MessageDirection direction) {
        return codecs.apply(direction).stream()
                .filter(codec -> codec.commandId() == commandId || codec.commandId() == responseCommandId())
                .toList();
    }

    R negative(Q request, ProtocolProfile profile) {
        return negative.apply(request, profile);
    }

    SendRequirements requestRequirements(Q request, SendRequirements extra) {
        requestType.cast(request);
        return new SendRequirements(
                extra.minimumVersion(), extra.usesOptionalParameters() || requestTlvs.test(request));
    }

    SendRequirements responseRequirements(R response, SendRequirements extra) {
        responseType.cast(response);
        return new SendRequirements(
                extra.minimumVersion(), extra.usesOptionalParameters() || responseTlvs.test(response));
    }

    void validateResponse(
            Pdu<? extends Command> request,
            Pdu<? extends Command> response,
            ProtocolProfile profile,
            MessageDirection direction) {
        validator.validate(request, response, profile, direction);
    }
    /** Checks conditions requiring the decoded original request; it owns neither sequence allocation nor deadlines. */
    @FunctionalInterface
    interface ResponseValidator {
        void validate(
                Pdu<? extends Command> request,
                Pdu<? extends Command> response,
                ProtocolProfile profile,
                MessageDirection direction);
    }
}
