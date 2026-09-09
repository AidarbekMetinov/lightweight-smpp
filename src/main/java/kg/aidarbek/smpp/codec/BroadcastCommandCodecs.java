package kg.aidarbek.smpp.codec;

import java.util.List;
import kg.aidarbek.smpp.protocol.BroadcastSm;
import kg.aidarbek.smpp.protocol.BroadcastSmResponse;
import kg.aidarbek.smpp.protocol.CancelBroadcastSm;
import kg.aidarbek.smpp.protocol.CancelBroadcastSmResponse;
import kg.aidarbek.smpp.protocol.QueryBroadcastSm;
import kg.aidarbek.smpp.protocol.QueryBroadcastSmResponse;

/** Explicit SMPP 5.0 broadcast body-codec composition. */
public final class BroadcastCommandCodecs {
    private static final BroadcastTlvSupport TLVS = new BroadcastTlvSupport();
    private static final List<CommandCodec<?>> CODECS = codecs(TLVS);

    private static List<CommandCodec<?>> codecs(BroadcastTlvSupport tlvs) {
        return List.of(
                new BroadcastCommandCodec<>(0x111, BroadcastSm.class, tlvs),
                new BroadcastCommandCodec<>(0x80000111L, BroadcastSmResponse.class, tlvs),
                new BroadcastCommandCodec<>(0x112, QueryBroadcastSm.class, tlvs),
                new BroadcastCommandCodec<>(0x80000112L, QueryBroadcastSmResponse.class, tlvs),
                new BroadcastCommandCodec<>(0x113, CancelBroadcastSm.class, tlvs),
                new BroadcastCommandCodec<>(0x80000113L, CancelBroadcastSmResponse.class, tlvs));
    }

    private BroadcastCommandCodecs() {}
    /** Composes explicit vendor permissions while preserving all standard broadcast contracts.
     * @param extensions copied exact-profile/command vendor registrations
     * @return immutable configured codecs */
    public static List<CommandCodec<?>> all(List<BroadcastTlvExtension> extensions) {
        return codecs(new BroadcastTlvSupport(extensions));
    }
    /** Composes the same vendor contexts for independent typed interpretation.
     * @param extensions copied exact-profile/command vendor registrations
     * @return immutable configured registry */
    public static TypedTlvRegistry tlvRegistry(List<BroadcastTlvExtension> extensions) {
        return new BroadcastTlvSupport(extensions).registry();
    }
    /** Returns exact supported broadcast interpretations separately from raw retained TLVs.
     * @return immutable SMPP 5.0 context registry */
    public static TypedTlvRegistry tlvRegistry() {
        return TLVS.registry();
    }
    /** Returns immutable registrations for the three request/response pairs.
     * @return bounded body codecs, independent of roles or service availability */
    public static List<CommandCodec<?>> all() {
        return CODECS;
    }
}
