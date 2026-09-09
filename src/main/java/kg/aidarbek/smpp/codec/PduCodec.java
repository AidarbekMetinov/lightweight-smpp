package kg.aidarbek.smpp.codec;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.Pdu;
import kg.aidarbek.smpp.protocol.PduHeader;

/**
 * Immutable full-PDU dispatch independent of network framing and session policy.
 *
 * <p>Registration metadata is copied; supplied codecs must obey {@link CommandCodec}'s stable
 * metadata, ownership and thread-safety contracts. No body codec is invoked until the complete
 * input frame and its header have passed validation. Callers must not concurrently mutate a byte
 * array while decoding it. Commands extend this registry by registration, without changing control codecs.
 */
public final class PduCodec {
    private final Map<Long, CommandCodec<?>> codecs;
    private final PduLimits limits;
    /**
     * Copies unique command registrations and validates their unsigned IDs and command representations.
     * A Java value type can serve several distinct command IDs without ambiguous dispatch.
     * @param codecs non-null command body translations, with no null entries
     * @param limits non-null allocation limits
     * @throws IllegalArgumentException for duplicate IDs or invalid codec metadata
     * @throws NullPointerException for missing arguments or entries
     */
    public PduCodec(Collection<? extends CommandCodec<?>> codecs, PduLimits limits) {
        Objects.requireNonNull(codecs, "codecs");
        this.limits = Objects.requireNonNull(limits, "limits");
        Map<Long, CommandCodec<?>> entries = new HashMap<>();
        for (CommandCodec<?> codec : codecs) {
            Objects.requireNonNull(codec, "codec");
            long commandId = codec.commandId();
            Class<?> type = codec.commandType();
            if (commandId < 0 || commandId > 0xffffffffL || type == null || !Command.class.isAssignableFrom(type)) {
                throw new IllegalArgumentException("Invalid command codec identity or representation metadata");
            }
            if (entries.putIfAbsent(commandId, codec) != null) {
                throw new IllegalArgumentException("Duplicate command codec registration");
            }
        }
        this.codecs = Map.copyOf(entries);
    }

    /**
     * Decodes exactly one complete PDU, preserving numeric response status and correlation sequence.
     * Oversized frames are rejected before copying their body. This method does not consume partial
     * frames, multiple frames, select a peer version, or generate a network error response.
     * @param encoded complete caller-owned frame
     * @param profile explicit profile
     * @return decoded command envelope
     * @throws CommandDispatchException for a command undefined by the profile or lacking a local codec
     * @throws IllegalArgumentException for malformed frames, illegal headers/bodies or invalid codec output
     */
    public Pdu<Command> decode(byte[] encoded, ProtocolProfile profile) {
        Objects.requireNonNull(encoded, "encoded");
        Objects.requireNonNull(profile, "profile");
        if (encoded.length < PduHeader.LENGTH || encoded.length > limits.maximumPduLength()) {
            throw new FieldCodecException("Complete PDU exceeds configured frame bounds");
        }
        PduHeader header = PduHeaderCodec.decode(ByteBuffer.wrap(encoded));
        if (header.commandLength() != encoded.length) {
            throw new FieldCodecException("command_length does not match complete frame length");
        }
        Pdu.validateHeader(header);
        CommandCodec<?> codec = requireCodec(header, profile);
        Command command = codec.decode(
                Arrays.copyOfRange(encoded, PduHeader.LENGTH, encoded.length), header.commandStatus(), profile, limits);
        if (command == null || !codec.commandType().isInstance(command) || command.commandId() != header.commandId()) {
            throw new IllegalArgumentException("Body codec returned a mismatched command representation or identity");
        }
        return new Pdu<>(header.commandStatus(), header.sequenceNumber(), command);
    }

    /**
     * Encodes one command envelope using a registered type-safe body codec. The actual body size is
     * checked before allocating the complete frame, including when an extension violates its bound.
     * @param pdu immutable envelope
     * @param profile explicit profile
     * @return fresh caller-owned complete frame
     * @throws CommandDispatchException for a command undefined by the profile or lacking a local codec
     * @throws IllegalArgumentException for mismatched command data, invalid bodies or exceeded limits
     */
    public byte[] encode(Pdu<? extends Command> pdu, ProtocolProfile profile) {
        Objects.requireNonNull(pdu, "pdu");
        Objects.requireNonNull(profile, "profile");
        PduHeader header =
                new PduHeader(PduHeader.LENGTH, pdu.command().commandId(), pdu.commandStatus(), pdu.sequenceNumber());
        byte[] body = encodeBody(requireCodec(header, profile), pdu, profile);
        if (body == null || body.length > limits.maximumBodyLength()) {
            throw new IllegalArgumentException("Body codec output exceeds configured body bound");
        }
        ByteBuffer frame = ByteBuffer.allocate(PduHeader.LENGTH + body.length);
        PduHeaderCodec.encode(
                new PduHeader(frame.capacity(), pdu.command().commandId(), pdu.commandStatus(), pdu.sequenceNumber()),
                frame);
        frame.put(body);
        return frame.array();
    }

    private CommandCodec<?> requireCodec(PduHeader header, ProtocolProfile profile) {
        boolean defined = profile.definesCommand(header.commandId());
        CommandCodec<?> codec = codecs.get(header.commandId());
        if (!defined || codec == null) {
            throw new CommandDispatchException(header, defined);
        }
        return codec;
    }

    private <T extends Command> byte[] encodeBody(
            CommandCodec<T> codec, Pdu<? extends Command> pdu, ProtocolProfile profile) {
        if (!codec.commandType().isInstance(pdu.command())) {
            throw new IllegalArgumentException("Command representation does not match registered codec");
        }
        return codec.encode(codec.commandType().cast(pdu.command()), pdu.commandStatus(), profile, limits);
    }
}
