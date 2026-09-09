package kg.aidarbek.smpp.codec;

import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.protocol.BroadcastSm;
import kg.aidarbek.smpp.protocol.BroadcastSmResponse;
import kg.aidarbek.smpp.protocol.CancelBroadcastSm;
import kg.aidarbek.smpp.protocol.CancelBroadcastSmResponse;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.QueryBroadcastSm;
import kg.aidarbek.smpp.protocol.QueryBroadcastSmResponse;

/** Bounded SMPP 5.0 body translation for one immutable broadcast representation. */
final class BroadcastCommandCodec<T extends Command> implements CommandCodec<T> {
    private final long id;
    private final Class<T> type;
    private final BroadcastTlvSupport tlvs;

    BroadcastCommandCodec(long id, Class<T> type, BroadcastTlvSupport tlvs) {
        this.id = id;
        this.type = type;
        this.tlvs = tlvs;
    }

    @Override
    public long commandId() {
        return id;
    }

    @Override
    public Class<T> commandType() {
        return type;
    }

    @Override
    public T decode(byte[] body, long status, ProtocolProfile profile, PduLimits limits) {
        CommandCodecChecks.context(id, status, profile, limits);
        FieldReader reader = new FieldReader(body, limits.maximumBodyLength());
        if ((id & 0x80000000L) != 0 && status != 0) {
            OptionalParameters empty = new OptionalParameters(List.of());
            MessageResponse omitted = new MessageResponse(Optional.empty(), empty);
            return type.cast(
                    id == 0x80000111L
                            ? new BroadcastSmResponse(omitted)
                            : id == 0x80000112L
                                    ? new QueryBroadcastSmResponse(omitted)
                                    : new CancelBroadcastSmResponse(empty));
        }
        Command command;
        if (id == 0x111)
            command = new BroadcastSm(
                    reader.readCOctetString(6),
                    MessageFields.readAddress(reader, 21),
                    reader.readCOctetString(65),
                    reader.readUnsignedByte(),
                    reader.readCOctetString(17),
                    reader.readCOctetString(17),
                    reader.readUnsignedByte(),
                    reader.readUnsignedByte(),
                    reader.readUnsignedByte(),
                    MessageFields.readParameters(reader, limits));
        else if (id == 0x112)
            command = new QueryBroadcastSm(
                    reader.readCOctetString(65),
                    MessageFields.readAddress(reader, 21),
                    MessageFields.readParameters(reader, limits));
        else if (id == 0x113)
            command = new CancelBroadcastSm(
                    reader.readCOctetString(6),
                    reader.readCOctetString(65),
                    MessageFields.readAddress(reader, 21),
                    MessageFields.readParameters(reader, limits));
        else if (id == 0x80000113L)
            command = new CancelBroadcastSmResponse(MessageFields.readParameters(reader, limits));
        else {
            MessageResponse fields = new MessageResponse(
                    Optional.of(reader.readCOctetString(65)), MessageFields.readParameters(reader, limits));
            command = id == 0x80000111L ? new BroadcastSmResponse(fields) : new QueryBroadcastSmResponse(fields);
        }
        validate(command, status, false);
        return type.cast(command);
    }

    @Override
    public byte[] encode(T command, long status, ProtocolProfile profile, PduLimits limits) {
        CommandCodecChecks.context(id, status, profile, limits);
        CommandCodecChecks.command(command, id);
        if (!type.isInstance(command)) throw new IllegalArgumentException("Incorrect broadcast command representation");
        byte[] tlvs = TlvCodec.encode(parameters(command), limits.maximumTlvLength(), limits.maximumTlvCount());
        validate(command, status, true);
        if ((id & 0x80000000L) != 0 && status != 0) return new byte[0];
        if (command instanceof BroadcastSm broadcast) {
            FieldWriter writer = MessageFields.writer(
                    11L
                            + broadcast.serviceType().length()
                            + broadcast.source().value().length()
                            + broadcast.messageId().length()
                            + broadcast.scheduleDeliveryTime().length()
                            + broadcast.validityPeriod().length()
                            + tlvs.length,
                    limits);
            writer.writeCOctetString(broadcast.serviceType(), 6);
            MessageFields.writeAddress(writer, broadcast.source(), 21);
            writer.writeCOctetString(broadcast.messageId(), 65);
            writer.writeUnsignedByte(broadcast.priorityFlag());
            writer.writeCOctetString(broadcast.scheduleDeliveryTime(), 17);
            writer.writeCOctetString(broadcast.validityPeriod(), 17);
            writer.writeUnsignedByte(broadcast.replaceIfPresentFlag());
            writer.writeUnsignedByte(broadcast.dataCoding());
            writer.writeUnsignedByte(broadcast.defaultMessageId());
            writer.writeOctets(tlvs);
            return writer.toByteArray();
        }
        if (command instanceof QueryBroadcastSm query) {
            FieldWriter writer = MessageFields.writer(
                    4L + query.messageId().length() + query.source().value().length() + tlvs.length, limits);
            writer.writeCOctetString(query.messageId(), 65);
            MessageFields.writeAddress(writer, query.source(), 21);
            writer.writeOctets(tlvs);
            return writer.toByteArray();
        }
        if (command instanceof CancelBroadcastSm cancel) {
            FieldWriter writer = MessageFields.writer(
                    5L
                            + cancel.serviceType().length()
                            + cancel.messageId().length()
                            + cancel.source().value().length()
                            + tlvs.length,
                    limits);
            writer.writeCOctetString(cancel.serviceType(), 6);
            writer.writeCOctetString(cancel.messageId(), 65);
            MessageFields.writeAddress(writer, cancel.source(), 21);
            writer.writeOctets(tlvs);
            return writer.toByteArray();
        }
        if (command instanceof CancelBroadcastSmResponse) {
            return tlvs;
        }
        MessageResponse fields = responseFields(command);
        String messageId = fields.messageId().orElseThrow();
        FieldWriter writer = MessageFields.writer(1L + messageId.length() + tlvs.length, limits);
        writer.writeCOctetString(messageId, 65);
        writer.writeOctets(tlvs);
        return writer.toByteArray();
    }

    private void validate(Command command, long status, boolean outgoing) {
        if (status == 0) tlvs.validate(command, outgoing);
        if (command instanceof BroadcastSm broadcast) {
            MessageFields.validateAddress(broadcast.source(), 21, outgoing);
            MessageFields.validateTime(broadcast.scheduleDeliveryTime(), outgoing);
            MessageFields.validateTime(broadcast.validityPeriod(), outgoing);
            if (outgoing && (broadcast.priorityFlag() > 4 || broadcast.replaceIfPresentFlag() > 1))
                throw new IllegalArgumentException("Unsupported outgoing broadcast priority or replacement flag");
        } else if (command instanceof QueryBroadcastSm query) {
            MessageFields.validateAddress(query.source(), 21, outgoing);
        } else if (command instanceof CancelBroadcastSm cancel) {
            MessageFields.validateAddress(cancel.source(), 21, outgoing);
        } else {
            if (!(command instanceof CancelBroadcastSmResponse)
                    && responseFields(command).messageId().isPresent() != (status == 0))
                throw new IllegalArgumentException("Broadcast response body presence conflicts with status");
            if (status != 0 && !parameters(command).entries().isEmpty())
                throw new IllegalArgumentException("Failed SMPP 5.0 broadcast response must omit its body");
        }
    }

    static MessageResponse responseFields(Command command) {
        if (command instanceof BroadcastSmResponse response) return response.fields();
        if (command instanceof QueryBroadcastSmResponse response) return response.fields();
        throw new IllegalArgumentException("Expected broadcast response with message identity");
    }

    static OptionalParameters parameters(Command command) {
        return switch (command) {
            case BroadcastSm broadcast -> broadcast.optionalParameters();
            case QueryBroadcastSm query -> query.optionalParameters();
            case CancelBroadcastSm cancel -> cancel.optionalParameters();
            case CancelBroadcastSmResponse response -> response.optionalParameters();
            default -> responseFields(command).optionalParameters();
        };
    }
}
