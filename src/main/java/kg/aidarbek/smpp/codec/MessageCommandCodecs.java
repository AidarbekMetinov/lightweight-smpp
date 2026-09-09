package kg.aidarbek.smpp.codec;

import java.util.List;
import java.util.Objects;
import kg.aidarbek.smpp.profile.MessageDirection;
import kg.aidarbek.smpp.protocol.DataSm;
import kg.aidarbek.smpp.protocol.DataSmResponse;
import kg.aidarbek.smpp.protocol.DeliverSm;
import kg.aidarbek.smpp.protocol.DeliverSmResponse;
import kg.aidarbek.smpp.protocol.SubmitSm;
import kg.aidarbek.smpp.protocol.SubmitSmResponse;

/** Factory for the six immutable message body codecs; data_sm direction is always explicit. */
public final class MessageCommandCodecs {
    private static final MessageTlvSupport SUBMISSION_SUPPORT =
            new MessageTlvSupport(MessageDirection.SUBMISSION, List.of());
    private static final MessageTlvSupport DELIVERY_SUPPORT =
            new MessageTlvSupport(MessageDirection.DELIVERY, List.of());

    private MessageCommandCodecs() {}

    /**
     * Registers the standard message tables for the chosen data request direction.
     *
     * @param dataDirection direction of data_sm requests; responses travel oppositely
     * @return an immutable list of codecs for both explicit protocol profiles
     */
    public static List<CommandCodec<?>> all(MessageDirection dataDirection) {
        return all(dataDirection, List.of());
    }

    /**
     * Registers standard message tables and explicit vendor contexts without changing global registries.
     *
     * @param dataDirection direction of data_sm requests
     * @param extensions explicit vendor permissions and interpretation; copied, duplicate contexts rejected
     * @return immutable configured codecs
     */
    public static List<CommandCodec<?>> all(MessageDirection dataDirection, List<MessageTlvExtension> extensions) {
        MessageTlvSupport support = extensions.isEmpty()
                ? standardSupport(dataDirection)
                : new MessageTlvSupport(dataDirection, extensions);
        return List.of(
                new MessageCommandCodec<>(4, SubmitSm.class, dataDirection, support),
                new MessageCommandCodec<>(5, DeliverSm.class, dataDirection, support),
                new MessageCommandCodec<>(0x103, DataSm.class, dataDirection, support),
                new MessageCommandCodec<>(0x80000004L, SubmitSmResponse.class, dataDirection, support),
                new MessageCommandCodec<>(0x80000005L, DeliverSmResponse.class, dataDirection, support),
                new MessageCommandCodec<>(0x80000103L, DataSmResponse.class, dataDirection, support));
    }

    /**
     * Provides value-level interpretation for the exact message tables. Values are immutable raw
     * octets with validated SMPP structure; protocol fields, companions and request context still
     * require their command codecs and MessageResponseRules. This registry does not parse text.
     * @param dataDirection explicit data request direction
     * @return immutable message-only typed registry; all its values use OctetString
     */
    public static TypedTlvRegistry tlvRegistry(MessageDirection dataDirection) {
        return standardSupport(dataDirection).registry();
    }

    private static MessageTlvSupport standardSupport(MessageDirection direction) {
        return switch (Objects.requireNonNull(direction, "direction")) {
            case SUBMISSION -> SUBMISSION_SUPPORT;
            case DELIVERY -> DELIVERY_SUPPORT;
        };
    }
}
