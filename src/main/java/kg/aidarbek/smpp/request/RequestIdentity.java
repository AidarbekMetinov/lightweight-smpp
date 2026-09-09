package kg.aidarbek.smpp.request;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable connection generation and locally allocated sequence.
 * @param generation connection identity
 * @param sequenceNumber sequence in 1..0x7fffffff
 */
public record RequestIdentity(UUID generation, long sequenceNumber) {
    /**
     * Validates the immutable identity.
     * @throws NullPointerException for a null generation
     * @throws IllegalArgumentException for an illegal sequence
     */
    public RequestIdentity {
        Objects.requireNonNull(generation, "generation");
        if (sequenceNumber < 1 || sequenceNumber > 0x7fffffffL) {
            throw new IllegalArgumentException("Request sequence must be in 1..0x7fffffff");
        }
    }
}
