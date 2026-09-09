package kg.aidarbek.smpp.codec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Tlv;

/**
 * Immutable registration of typed TLV interpretation by exact profile, command and tag.
 *
 * <p>Unknown or unexpected incoming TLVs yield no typed value and remain available in raw storage.
 * Duplicate registrations fail even when their Java value types differ. Supplied codecs must obey
 * the thread-safety and ownership contract of {@link TlvValueCodec}; immutable registry metadata
 * alone cannot make an arbitrary codec implementation safe. Header status, fields, roles and
 * occurrence rules are validated separately.
 */
public final class TypedTlvRegistry {
    /**
     * A codec registered for one profile/command/tag context; codec equality retains its implementation's contract.
     * Registration is an explicit interpretation opt-in, including for a vendor tag, and does not grant
     * outgoing command permission.
     *
     * @param version non-null specification identity
     * @param commandId exact unsigned command defined by that specification
     * @param tag unsigned tag in 0..65535
     * @param codec non-null codec conforming to the immutable value and thread-safety contracts
     * @param <T> typed value representation
     */
    public record Registration<T>(SmppVersion version, long commandId, int tag, TlvValueCodec<T> codec) {
        /**
         * Validates the registration context.
         *
         * @throws IllegalArgumentException if the command is undefined for the profile or tag is out of range
         */
        public Registration {
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(codec, "codec");
            if (!ProtocolProfile.forVersion(version).definesCommand(commandId)) {
                throw new IllegalArgumentException("Registered command is not defined by the profile");
            }
            if (tag < 0 || tag > 0xffff) {
                throw new IllegalArgumentException("Registered tag must be an unsigned 16-bit value");
            }
        }
    }

    private record Key(SmppVersion version, long commandId, int tag) {}

    private final Map<Key, Registration<?>> registrations;

    /**
     * Copies registration metadata while retaining the supplied conforming codec implementations.
     *
     * @param registrations non-null declarations with no null entries
     * @throws IllegalArgumentException if any profile/command/tag key is repeated
     */
    public TypedTlvRegistry(List<Registration<?>> registrations) {
        Map<Key, Registration<?>> entries = new HashMap<>();
        for (Registration<?> registration : registrations) {
            Key key = new Key(registration.version(), registration.commandId(), registration.tag());
            if (entries.putIfAbsent(key, registration) != null) {
                throw new IllegalArgumentException("Duplicate typed TLV codec registration for command/profile/tag");
            }
        }
        this.registrations = Map.copyOf(entries);
    }

    /**
     * Creates the initial supported interpretations: sc_interface_version on all bind responses in
     * both profiles, and congestion_state on all defined 5.0 operation responses. The version octet
     * is preserved even if unrecognized; its encoding also preserves any unsigned octet, with
     * advertised-version policy left to bind validation. Congestion supports 0..100 and ignores
     * reserved incoming values.
     *
     * @return a registry containing these two parameter interpretations only
     */
    public static TypedTlvRegistry standard() {
        List<Registration<?>> entries = new ArrayList<>();
        TlvValueCodec<Integer> interfaceVersion = new UnsignedByteTlvCodec(255);
        for (SmppVersion version : SmppVersion.values()) {
            for (long command : new long[] {0x80000001L, 0x80000002L, 0x80000009L}) {
                entries.add(new Registration<>(version, command, 0x0210, interfaceVersion));
            }
        }
        TlvValueCodec<Integer> congestion = new UnsignedByteTlvCodec(100);
        for (long command : ProtocolProfile.forVersion(SmppVersion.V5_0).definedCommands()) {
            if ((command & 0x80000000L) != 0) {
                entries.add(new Registration<>(SmppVersion.V5_0, command, 0x0428, congestion));
            }
        }
        return new TypedTlvRegistry(entries);
    }

    /**
     * Adds one explicit context without changing this registry.
     *
     * @param registration non-null additional declaration
     * @return a new immutable registry
     * @throws IllegalArgumentException if the context key is already registered
     */
    public TypedTlvRegistry with(Registration<?> registration) {
        List<Registration<?>> entries = new ArrayList<>(registrations.values());
        entries.add(registration);
        return new TypedTlvRegistry(entries);
    }

    /**
     * Interprets a raw entry only when its exact context is registered. A known field's malformed
     * value length fails at this layer, while an unregistered entry is left uninterpreted.
     *
     * @param version non-null chosen profile
     * @param commandId exact command identity
     * @param parameter non-null raw TLV, never modified
     * @param valueType non-null reference class expected by the caller
     * @param <T> immutable interpreted type
     * @return a supported interpretation, or empty for an unregistered context or unsupported value
     * @throws IllegalArgumentException if a registered codec has a different value type
     * @throws FieldCodecException if a registered field's value structure is malformed
     */
    public <T> Optional<T> decode(SmppVersion version, long commandId, Tlv parameter, Class<T> valueType) {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(parameter, "parameter");
        Objects.requireNonNull(valueType, "valueType");
        Registration<?> registration = registrations.get(new Key(version, commandId, parameter.tag()));
        if (registration == null) {
            return Optional.empty();
        }
        requireType(registration.codec(), valueType);
        return registration.codec().decode(parameter.value()).map(valueType::cast);
    }

    /**
     * Creates an owned raw TLV from a registered supported typed value. The resulting TLV must still
     * pass command occurrence validation and bounded {@link TlvCodec} block encoding.
     *
     * @param version non-null chosen profile
     * @param commandId exact registered command identity
     * @param tag exact registered unsigned tag
     * @param value non-null supported value
     * @param valueType non-null registered reference class
     * @param <T> typed value representation
     * @return a raw TLV owning the codec output bytes
     * @throws IllegalArgumentException if the context, type, value or encoded wire length is invalid
     */
    public <T> Tlv encode(SmppVersion version, long commandId, int tag, T value, Class<T> valueType) {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(valueType, "valueType");
        Registration<?> registration = registrations.get(new Key(version, commandId, tag));
        if (registration == null) {
            throw new IllegalArgumentException("No typed TLV codec registered for command/profile/tag");
        }
        requireType(registration.codec(), valueType);
        return new Tlv(tag, encodeValue(registration.codec(), value));
    }

    private static void requireType(TlvValueCodec<?> codec, Class<?> type) {
        if (!codec.valueType().equals(type)) {
            throw new IllegalArgumentException("Requested value type does not match registered TLV codec");
        }
    }

    private static <T> byte[] encodeValue(TlvValueCodec<T> codec, Object value) {
        return codec.encode(codec.valueType().cast(value));
    }
}
