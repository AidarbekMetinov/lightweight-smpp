package kg.aidarbek.smpp.codec;

import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.protocol.Command;

/**
 * One command's body translation, including status-specific layout and field/TLV validation.
 *
 * <p>Implementations must be thread-safe, retain no mutable caller input, return immutable command
 * values and fresh output arrays, and keep metadata stable throughout registration. Bounds apply
 * before allocation; successful decoding accounts for the entire body, including explicitly ignored
 * error bodies. Implementations validate status width and request-zero status during direct calls;
 * the envelope alone validates sequences. Profile selection is explicit and never performs version negotiation.
 *
 * @param <T> immutable command representation
 */
public interface CommandCodec<T extends Command> {
    /**
     * Returns the exact unsigned command ID this codec handles.
     * @return stable unsigned command identity
     */
    long commandId();

    /**
     * Returns the command representation accepted and produced by this codec.
     * @return stable non-null reference class
     */
    Class<T> commandType();

    /**
     * Decodes one complete bounded command body.
     * @param body caller-owned bytes, never modified or retained
     * @param commandStatus unsigned header status; request status must be zero
     * @param profile explicit specification context
     * @param limits allocation and TLV limits to enforce even when invoked directly
     * @return immutable command carrying this codec's command ID
     * @throws IllegalArgumentException for malformed fields, forbidden body layout or exceeded limits
     */
    T decode(byte[] body, long commandStatus, ProtocolProfile profile, PduLimits limits);

    /**
     * Encodes one complete command body after checking its bounded size and outgoing field rules.
     * @param command immutable command matching this codec's ID and representation
     * @param commandStatus unsigned header status; request status must be zero
     * @param profile explicit specification context
     * @param limits allocation and TLV limits to enforce even when invoked directly
     * @return fresh caller-owned bytes within the configured body bound
     * @throws IllegalArgumentException for invalid command data, body layout or exceeded limits
     */
    byte[] encode(T command, long commandStatus, ProtocolProfile profile, PduLimits limits);
}
