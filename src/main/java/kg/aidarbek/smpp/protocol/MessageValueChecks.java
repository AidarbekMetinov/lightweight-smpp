package kg.aidarbek.smpp.protocol;

import java.util.Objects;

/** Shared representation invariants for immutable message field values. */
final class MessageValueChecks {
    private MessageValueChecks() {}

    static void octet(int value) {
        if (value < 0 || value > 255) throw new IllegalArgumentException("Message field requires an unsigned octet");
    }

    static void ascii(String value, int maximumOctets) {
        Objects.requireNonNull(value, "value");
        if (value.length() >= maximumOctets)
            throw new IllegalArgumentException("Message ASCII field exceeds its bound");
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == 0 || value.charAt(i) > 127)
                throw new IllegalArgumentException("Message field requires non-NUL ASCII");
        }
    }
}
