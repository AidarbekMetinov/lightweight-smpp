package kg.aidarbek.smpp.codec;

import java.util.Objects;
import kg.aidarbek.smpp.profile.MessageDirection;
import kg.aidarbek.smpp.profile.MessageTlvRules;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.OctetString;

/**
 * Explicit vendor-TLV interpretation and outgoing permission for exactly one message context.
 * The supplied codec must meet TlvValueCodec's ownership and thread-safety contract.
 * @param version explicit profile
 * @param commandId exact message request or response identity
 * @param direction direction of the original message request
 * @param tag vendor tag in 0x1400..0x3fff
 * @param codec supported vendor value interpretation
 * @param repeatable whether this extension may repeat
 */
public record MessageTlvExtension(
        SmppVersion version,
        long commandId,
        MessageDirection direction,
        int tag,
        TlvValueCodec<OctetString> codec,
        boolean repeatable) {
    /** Validates exact message direction, vendor tag range and octet interpretation metadata. */
    public MessageTlvExtension {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(codec, "codec");
        MessageTlvRules.permittedTags(version, commandId, direction);
        if (tag < 0x1400 || tag > 0x3fff)
            throw new IllegalArgumentException("Message extension requires a vendor-range tag");
        long request = commandId & 0x7fff_ffffL;
        if ((request == 4 && direction != MessageDirection.SUBMISSION)
                || (request == 5 && direction != MessageDirection.DELIVERY))
            throw new IllegalArgumentException("Extension direction conflicts with its command");
        if (!codec.valueType().equals(OctetString.class))
            throw new IllegalArgumentException("Message extension requires octet values");
    }
}
