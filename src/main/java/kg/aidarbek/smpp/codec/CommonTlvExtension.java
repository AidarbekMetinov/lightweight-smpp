package kg.aidarbek.smpp.codec;

import java.util.Objects;
import kg.aidarbek.smpp.profile.CommonTlvRules;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.OctetString;

/**
 * Explicit immutable vendor permission for one common command and profile. The supplied codec must
 * satisfy the ownership and thread-safety contract of TlvValueCodec. No session capability follows
 * from this standalone binary composition.
 * @param version explicit specification
 * @param commandId exact implemented common command ID, including response bit when applicable
 * @param tag vendor-range tag in 0x1400..0x3fff
 * @param codec supported raw-octet interpretation
 * @param repeatable whether this tag can repeat in this context
 */
public record CommonTlvExtension(
        SmppVersion version, long commandId, int tag, TlvValueCodec<OctetString> codec, boolean repeatable) {
    /** Validates supported command identity, vendor range and octet-codec metadata. */
    public CommonTlvExtension {
        CommonTlvRules.permittedTags(Objects.requireNonNull(version, "version"), commandId);
        Objects.requireNonNull(codec, "codec");
        if (tag < 0x1400 || tag > 0x3fff)
            throw new IllegalArgumentException("Common extension requires a vendor-range tag");
        if (!OctetString.class.equals(codec.valueType()))
            throw new IllegalArgumentException("Common extension requires octet values");
    }
}
