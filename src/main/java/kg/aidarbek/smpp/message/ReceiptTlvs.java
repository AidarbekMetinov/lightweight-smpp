package kg.aidarbek.smpp.message;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Tlv;

/** Explicit raw receipt-TLV view; unknown states and original parameters remain available. */
public final class ReceiptTlvs {
    private final OptionalParameters parameters;
    private final Optional<String> id;
    private final OptionalInt state;
    private final Optional<OctetString> error;

    private ReceiptTlvs(
            OptionalParameters parameters, Optional<String> id, OptionalInt state, Optional<OctetString> error) {
        this.parameters = parameters;
        this.id = id;
        this.state = state;
        this.error = error;
    }

    /**
     * Reads at most 64 parameters and 65535 value octets, rejecting malformed or duplicate receipt fields.
     *
     * @param parameters original immutable TLVs
     * @return raw receipt view
     */
    public static ReceiptTlvs read(OptionalParameters parameters) {
        Objects.requireNonNull(parameters, "parameters");
        if (parameters.entries().size() > 64) throw new IllegalArgumentException("Receipt TLVs exceed 64 entries");
        int byteCount = 0;
        for (Tlv parameter : parameters.entries()) {
            byteCount += parameter.valueLength();
            if (byteCount > 65535) throw new IllegalArgumentException("Receipt TLVs exceed 65535 value octets");
        }
        Optional<String> id = Optional.empty();
        OptionalInt state = OptionalInt.empty();
        Optional<OctetString> error = Optional.empty();
        for (Tlv parameter : parameters.entries()) {
            byte[] value = parameter.value();
            switch (parameter.tag()) {
                case 0x001e -> {
                    if (id.isPresent() || value.length < 1 || value.length > 65 || value[value.length - 1] != 0)
                        throw new IllegalArgumentException("Duplicate or malformed receipt message ID");
                    StringBuilder text = new StringBuilder();
                    for (int i = 0; i < value.length - 1; i++) {
                        if (value[i] <= 0)
                            throw new IllegalArgumentException("Receipt ID must be one ASCII C-octet string");
                        text.append((char) value[i]);
                    }
                    id = Optional.of(text.toString());
                }
                case 0x0427 -> {
                    if (state.isPresent() || value.length != 1)
                        throw new IllegalArgumentException("Duplicate or malformed receipt state");
                    state = OptionalInt.of(value[0] & 255);
                }
                case 0x0423 -> {
                    if (error.isPresent() || value.length != 3)
                        throw new IllegalArgumentException("Duplicate or malformed receipt network error");
                    error = Optional.of(new OctetString(value));
                }
                default -> {}
            }
        }
        return new ReceiptTlvs(parameters, id, state, error);
    }
    /**
     * Returns all original parameters, including unknown extensions.
     * @return immutable raw TLVs
     */
    public OptionalParameters parameters() {
        return parameters;
    }
    /**
     * Returns the opaque receipt message ID, preserving case and leading zeros.
     * @return raw ID
     */
    public Optional<String> messageId() {
        return id;
    }
    /**
     * Returns the unsigned raw state without classifying unknown values.
     * @return raw state
     */
    public OptionalInt messageState() {
        return state;
    }
    /**
     * Returns the exact three-octet network error value.
     * @return raw error
     */
    public Optional<OctetString> networkErrorCode() {
        return error;
    }
    /**
     * Builds only fields explicitly supplied by the application.
     *
     * @param messageId optional raw ASCII ID
     * @param messageState optional unsigned state octet
     * @param networkErrorCode optional three raw error octets
     * @return immutable receipt TLVs
     */
    public static OptionalParameters build(
            Optional<String> messageId, OptionalInt messageState, Optional<OctetString> networkErrorCode) {
        Objects.requireNonNull(messageId, "messageId");
        Objects.requireNonNull(messageState, "messageState");
        Objects.requireNonNull(networkErrorCode, "networkErrorCode");
        List<Tlv> result = new ArrayList<>();
        messageId.ifPresent(value -> {
            if (value.length() > 64) throw new IllegalArgumentException("Receipt ID exceeds 64 ASCII characters");
            byte[] encoded = new byte[value.length() + 1];
            for (int i = 0; i < value.length(); i++) {
                char character = value.charAt(i);
                if (character < 1 || character > 127)
                    throw new IllegalArgumentException("Receipt ID must contain non-NUL ASCII characters");
                encoded[i] = (byte) character;
            }
            result.add(new Tlv(0x001e, encoded));
        });
        messageState.ifPresent(value -> {
            if (value < 0 || value > 255)
                throw new IllegalArgumentException("Receipt state must fit an unsigned octet");
            result.add(new Tlv(0x0427, new byte[] {(byte) value}));
        });
        networkErrorCode.ifPresent(value -> {
            if (value.length() != 3)
                throw new IllegalArgumentException("Receipt network error must contain three octets");
            result.add(new Tlv(0x0423, value.value()));
        });
        return read(new OptionalParameters(result)).parameters();
    }

    @Override
    public String toString() {
        return "ReceiptTlvs[parameters=" + parameters.entries().size() + "]";
    }
}
