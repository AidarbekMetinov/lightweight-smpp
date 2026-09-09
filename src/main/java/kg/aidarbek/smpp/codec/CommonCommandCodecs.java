package kg.aidarbek.smpp.codec;

import java.util.List;
import kg.aidarbek.smpp.protocol.AlertNotification;
import kg.aidarbek.smpp.protocol.CancelSm;
import kg.aidarbek.smpp.protocol.CancelSmResponse;
import kg.aidarbek.smpp.protocol.Outbind;
import kg.aidarbek.smpp.protocol.QuerySm;
import kg.aidarbek.smpp.protocol.QuerySmResponse;
import kg.aidarbek.smpp.protocol.ReplaceSm;
import kg.aidarbek.smpp.protocol.ReplaceSmResponse;
import kg.aidarbek.smpp.protocol.SubmitMulti;
import kg.aidarbek.smpp.protocol.SubmitMultiResponse;

/** Explicit composition of remaining common-operation body codecs. */
public final class CommonCommandCodecs {
    private static final CommonTlvSupport TLVS = new CommonTlvSupport();
    private static final List<CommandCodec<?>> CODECS = codecs(TLVS);

    private CommonCommandCodecs() {}

    /** Returns the ten standard bounded common-operation body codecs.
     * @return immutable codec registrations, without session permissions or negotiated capability */
    public static List<CommandCodec<?>> all() {
        return CODECS;
    }

    private static List<CommandCodec<?>> codecs(CommonTlvSupport tlvs) {
        return List.of(
                new CommonCommandCodec<>(7, ReplaceSm.class, tlvs),
                new CommonCommandCodec<>(0x80000007L, ReplaceSmResponse.class, tlvs),
                new CommonCommandCodec<>(0x21, SubmitMulti.class, tlvs),
                new CommonCommandCodec<>(0x80000021L, SubmitMultiResponse.class, tlvs),
                new CommonCommandCodec<>(8, CancelSm.class, tlvs),
                new CommonCommandCodec<>(0x80000008L, CancelSmResponse.class, tlvs),
                new CommonCommandCodec<>(0x0b, Outbind.class, tlvs),
                new CommonCommandCodec<>(0x102, AlertNotification.class, tlvs),
                new CommonCommandCodec<>(3, QuerySm.class, tlvs),
                new CommonCommandCodec<>(0x80000003L, QuerySmResponse.class, tlvs));
    }

    /** Returns supported standard value interpretations separately from immutable raw retention.
     * @return shared immutable registry with exact profile/command contexts */
    public static TypedTlvRegistry tlvRegistry() {
        return TLVS.registry();
    }

    /** Composes explicit vendor permissions with the standard body contracts.
     * @param extensions copied context declarations; duplicate keys and standard-tag overrides fail
     * @return immutable configured body codecs
     * @throws IllegalArgumentException for duplicate extension contexts */
    public static List<CommandCodec<?>> all(List<CommonTlvExtension> extensions) {
        return codecs(new CommonTlvSupport(extensions));
    }

    /** Composes the same explicit vendor contexts for standalone typed interpretation.
     * @param extensions copied exact command/profile declarations
     * @return immutable configured value registry
     * @throws IllegalArgumentException for duplicate extension contexts */
    public static TypedTlvRegistry tlvRegistry(List<CommonTlvExtension> extensions) {
        return new CommonTlvSupport(extensions).registry();
    }
}
