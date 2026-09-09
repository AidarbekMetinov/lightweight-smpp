package kg.aidarbek.simulator;

import java.util.Objects;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;

/** One generated message body, before operation-specific short_message/message_payload placement. */
record TrafficContent(int esmClass, int dataCoding, OctetString payload, OptionalParameters parameters) {
    public TrafficContent {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(parameters, "parameters");
        if (esmClass < 0 || esmClass > 255 || dataCoding < 0 || dataCoding > 255 || payload.length() > 65535)
            throw new IllegalArgumentException("Content exceeds wire field bounds");
        if (parameters.entries().size() > 64 || parameters.entries().stream().anyMatch(tag -> tag.tag() == 0x0424))
            throw new IllegalArgumentException(
                    "Content parameters are bounded and payload placement belongs to the operation");
    }
}
