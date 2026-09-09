package kg.aidarbek.smpp.session;

import java.util.Objects;
import kg.aidarbek.smpp.profile.SmppVersion;

/**
 * Immutable semantic requirements supplied by command validation before a send.
 *
 * <p>The session does not inspect message fields. The caller must describe the actual field values,
 * including version-specific flags and optional parameters; command identity alone cannot do this.
 * Instances have ordinary record equality and are safe to share between threads.
 *
 * @param minimumVersion earliest implemented profile supporting every supplied field value
 * @param usesOptionalParameters whether any TLV is present or required
 */
public record SendRequirements(SmppVersion minimumVersion, boolean usesOptionalParameters) {
    /** Common SMPP 3.4-compatible field values without optional parameters. */
    public static final SendRequirements COMMON = new SendRequirements(SmppVersion.V3_4, false);

    /**
     * Validates the explicit feature baseline.
     *
     * @throws NullPointerException if minimumVersion is null
     */
    public SendRequirements {
        Objects.requireNonNull(minimumVersion, "minimumVersion");
    }
}
