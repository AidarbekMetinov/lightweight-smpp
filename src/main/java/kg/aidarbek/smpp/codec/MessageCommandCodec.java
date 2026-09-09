package kg.aidarbek.smpp.codec;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import kg.aidarbek.smpp.profile.MessageDirection;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.DataSm;
import kg.aidarbek.smpp.protocol.DataSmResponse;
import kg.aidarbek.smpp.protocol.DeliverSm;
import kg.aidarbek.smpp.protocol.DeliverSmResponse;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.ShortMessage;
import kg.aidarbek.smpp.protocol.SubmitSm;
import kg.aidarbek.smpp.protocol.SubmitSmResponse;

/** Message body translation configured for one command identity and data request direction. */
final class MessageCommandCodec<T extends Command> implements CommandCodec<T> {
    private final long commandId;
    private final Class<T> commandType;
    private final MessageDirection direction;
    private final MessageTlvSupport tlvs;

    MessageCommandCodec(long commandId, Class<T> commandType, MessageDirection direction, MessageTlvSupport tlvs) {
        this.commandId = commandId;
        this.commandType = commandType;
        this.direction = Objects.requireNonNull(direction, "direction");
        this.tlvs = tlvs;
    }

    @Override
    public long commandId() {
        return commandId;
    }

    @Override
    public Class<T> commandType() {
        return commandType;
    }

    @Override
    public T decode(byte[] body, long status, ProtocolProfile profile, PduLimits limits) {
        CommandCodecChecks.context(commandId, status, profile, limits);
        FieldReader reader = new FieldReader(body, limits.maximumBodyLength());
        if ((commandId & 0x80000000L) != 0 && status != 0) {
            if (profile.version() == SmppVersion.V5_0 || body.length == 0)
                return commandType.cast(
                        response(new MessageResponse(Optional.empty(), new OptionalParameters(List.of()))));
            if (commandId == 0x80000004L)
                throw new FieldCodecException("SMPP 3.4 submit error response body must be omitted");
        }
        Command result;
        if (commandId == 4 || commandId == 5) {
            ShortMessage fields = MessageFields.readShortMessage(reader, limits);
            result = commandId == 4 ? new SubmitSm(fields) : new DeliverSm(fields);
        } else if (commandId == 0x103) {
            result = new DataSm(
                    reader.readCOctetString(6),
                    MessageFields.readAddress(reader, 65),
                    MessageFields.readAddress(reader, 65),
                    reader.readUnsignedByte(),
                    reader.readUnsignedByte(),
                    reader.readUnsignedByte(),
                    MessageFields.readParameters(reader, limits));
        } else {
            MessageResponse fields = new MessageResponse(
                    Optional.of(reader.readCOctetString(65)), MessageFields.readParameters(reader, limits));
            result = response(fields);
        }
        validate(result, status, profile, false);
        return commandType.cast(result);
    }

    @Override
    public byte[] encode(T command, long status, ProtocolProfile profile, PduLimits limits) {
        CommandCodecChecks.context(commandId, status, profile, limits);
        CommandCodecChecks.command(command, commandId);
        byte[] encodedTlvs = TlvCodec.encode(parameters(command), limits.maximumTlvLength(), limits.maximumTlvCount());
        validate(command, status, profile, true);
        if (command instanceof SubmitSm submit)
            return MessageFields.writeShortMessage(submit.fields(), encodedTlvs, limits);
        if (command instanceof DeliverSm deliver)
            return MessageFields.writeShortMessage(deliver.fields(), encodedTlvs, limits);
        if (command instanceof DataSm data) {
            FieldWriter writer = MessageFields.writer(
                    10L
                            + data.serviceType().length()
                            + data.source().value().length()
                            + data.destination().value().length()
                            + encodedTlvs.length,
                    limits);
            writer.writeCOctetString(data.serviceType(), 6);
            MessageFields.writeAddress(writer, data.source(), 65);
            MessageFields.writeAddress(writer, data.destination(), 65);
            writer.writeUnsignedByte(data.esmClass());
            writer.writeUnsignedByte(data.registeredDelivery());
            writer.writeUnsignedByte(data.dataCoding());
            writer.writeOctets(encodedTlvs);
            return writer.toByteArray();
        }
        MessageResponse fields = responseFields(command);
        if (fields.messageId().isEmpty()) return new byte[0];
        String messageId =
                fields.messageId().orElseThrow(() -> new IllegalArgumentException("Response body is absent"));
        FieldWriter writer = MessageFields.writer(1L + messageId.length() + encodedTlvs.length, limits);
        writer.writeCOctetString(messageId, 65);
        writer.writeOctets(encodedTlvs);
        return writer.toByteArray();
    }

    private static OptionalParameters parameters(Command command) {
        if (command instanceof SubmitSm submit) return submit.fields().optionalParameters();
        if (command instanceof DeliverSm deliver) return deliver.fields().optionalParameters();
        if (command instanceof DataSm data) return data.optionalParameters();
        return responseFields(command).optionalParameters();
    }

    private void validate(Command command, long status, ProtocolProfile profile, boolean outgoing) {
        if (command instanceof SubmitSm submit) validateShort(submit.fields(), profile, outgoing);
        else if (command instanceof DeliverSm deliver) validateShort(deliver.fields(), profile, outgoing);
        else if (command instanceof DataSm data) {
            MessageFields.validateAddress(data.source(), 65, outgoing);
            MessageFields.validateAddress(data.destination(), 65, outgoing);
            if (outgoing)
                MessageFields.validateFlags(data.esmClass(), data.registeredDelivery(), direction, profile.version());
            tlvs.validate(commandId, profile.version(), data.optionalParameters(), outgoing, data.esmClass(), 0);
        } else {
            MessageResponse fields = responseFields(command);
            if (status == 0 && fields.messageId().isEmpty())
                throw new IllegalArgumentException("Successful message response body is required");
            if (status != 0
                    && fields.messageId().isPresent()
                    && (profile.version() == SmppVersion.V5_0 || commandId == 0x80000004L))
                throw new IllegalArgumentException("Error response body must be omitted");
            if (commandId == 0x80000005L
                    && fields.messageId().isPresent()
                    && !fields.messageId().orElseThrow().isEmpty()
                    && (outgoing || profile.version() == SmppVersion.V3_4))
                throw new IllegalArgumentException("Delivery response message_id must be empty");
            tlvs.validate(commandId, profile.version(), fields.optionalParameters(), outgoing, 0, 0);
        }
    }

    private void validateShort(ShortMessage fields, ProtocolProfile profile, boolean outgoing) {
        MessageFields.validate(fields, commandId, profile.version(), outgoing);
        tlvs.validate(
                commandId,
                profile.version(),
                fields.optionalParameters(),
                outgoing,
                fields.esmClass(),
                fields.shortMessage().length());
    }

    private Command response(MessageResponse fields) {
        if (commandId == 0x80000004L) return new SubmitSmResponse(fields);
        if (commandId == 0x80000005L) return new DeliverSmResponse(fields);
        return new DataSmResponse(fields);
    }

    static MessageResponse responseFields(Command command) {
        if (command instanceof SubmitSmResponse response) return response.fields();
        if (command instanceof DeliverSmResponse response) return response.fields();
        if (command instanceof DataSmResponse response) return response.fields();
        throw new IllegalArgumentException("Expected message response");
    }
}
