package kg.aidarbek.smpp.message;

import java.util.Objects;

/**
 * Explicit caller namespace plus originator reference. Include source, destination, encoding and
 * provider/connection identity in the namespace wherever they distinguish independent messages.
 *
 * @param namespace opaque caller context, at most 256 UTF-16 units
 * @param reference unsigned originator reference
 */
public record ReassemblyKey(String namespace, int reference) {
    /** Validates finite identity storage; reference reuse remains the caller's responsibility. */
    public ReassemblyKey {
        Objects.requireNonNull(namespace, "namespace");
        if (namespace.length() > 256 || reference < 0 || reference > 65535)
            throw new IllegalArgumentException("Invalid reassembly namespace or reference");
    }

    @Override
    public String toString() {
        return "ReassemblyKey[reference=" + reference + ", namespace=redacted]";
    }
}
