package kg.aidarbek.smpp.codec;

import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.profile.MessageDirection;
import kg.aidarbek.smpp.profile.ProtocolProfile;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.AlertNotification;
import kg.aidarbek.smpp.protocol.CancelSm;
import kg.aidarbek.smpp.protocol.CancelSmResponse;
import kg.aidarbek.smpp.protocol.Command;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Outbind;
import kg.aidarbek.smpp.protocol.QuerySm;
import kg.aidarbek.smpp.protocol.QuerySmResponse;
import kg.aidarbek.smpp.protocol.ReplaceSm;
import kg.aidarbek.smpp.protocol.ReplaceSmResponse;
import kg.aidarbek.smpp.protocol.SubmitMulti;
import kg.aidarbek.smpp.protocol.SubmitMultiResponse;

/** Bounded common-operation translation for one immutable command representation. */
final class CommonCommandCodec<T extends Command> implements CommandCodec<T> {
    private final long id;
    private final Class<T> type;
    private final CommonTlvSupport tlvs;

    CommonCommandCodec(long id, Class<T> type, CommonTlvSupport tlvs) {
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
        if ((id & 0x80000000L) != 0 && status != 0 && profile.version() == SmppVersion.V5_0)
            return type.cast(omittedResponse());
        Command command;
        if (id == 3) {
            command = new QuerySm(
                    reader.readCOctetString(65),
                    MessageFields.readAddress(reader, 21),
                    MessageFields.readParameters(reader, limits));
        } else if (id == 7) {
            command = new ReplaceSm(
                    reader.readCOctetString(65),
                    MessageFields.readAddress(reader, 21),
                    reader.readCOctetString(17),
                    reader.readCOctetString(17),
                    reader.readUnsignedByte(),
                    reader.readUnsignedByte(),
                    new OctetString(reader.readOctets(reader.readUnsignedByte())),
                    MessageFields.readParameters(reader, limits));
        } else if (id == 0x80000007L) {
            command = new ReplaceSmResponse(MessageFields.readParameters(reader, limits));
        } else if (id == 0x21) {
            command = MultiFields.read(reader, profile.version(), limits);
        } else if (id == 0x80000021L) {
            command = MultiFields.readResponse(reader, limits);
        } else if (id == 8) {
            command = new CancelSm(
                    reader.readCOctetString(6),
                    reader.readCOctetString(65),
                    MessageFields.readAddress(reader, 21),
                    MessageFields.readAddress(reader, 21),
                    MessageFields.readParameters(reader, limits));
        } else if (id == 0x0b) {
            command = new Outbind(
                    reader.readCOctetString(16),
                    reader.readCOctetString(9),
                    MessageFields.readParameters(reader, limits));
        } else if (id == 0x102) {
            command = new AlertNotification(
                    MessageFields.readAddress(reader, 65),
                    MessageFields.readAddress(reader, 65),
                    MessageFields.readParameters(reader, limits));
        } else if (id == 0x80000008L) {
            command = new CancelSmResponse(MessageFields.readParameters(reader, limits));
        } else {
            QuerySmResponse.Result result = new QuerySmResponse.Result(
                    reader.readCOctetString(65),
                    reader.readCOctetString(17),
                    reader.readUnsignedByte(),
                    reader.readUnsignedByte());
            command = new QuerySmResponse(Optional.of(result), MessageFields.readParameters(reader, limits));
        }
        validate(command, status, profile, false);
        return type.cast(command);
    }

    @Override
    public byte[] encode(T command, long status, ProtocolProfile profile, PduLimits limits) {
        CommandCodecChecks.context(id, status, profile, limits);
        CommandCodecChecks.command(command, id);
        byte[] encodedTlvs = TlvCodec.encode(parameters(command), limits.maximumTlvLength(), limits.maximumTlvCount());
        validate(command, status, profile, true);
        if (command instanceof QuerySm query) {
            byte[] tlvs = encodedTlvs;
            FieldWriter writer = MessageFields.writer(
                    4L + query.messageId().length() + query.source().value().length() + tlvs.length, limits);
            writer.writeCOctetString(query.messageId(), 65);
            MessageFields.writeAddress(writer, query.source(), 21);
            writer.writeOctets(tlvs);
            return writer.toByteArray();
        }
        if (command instanceof SubmitMulti multi) return MultiFields.write(multi, encodedTlvs, limits);
        if (command instanceof SubmitMultiResponse multi) return MultiFields.writeResponse(multi, encodedTlvs, limits);
        if (command instanceof ReplaceSm replacement) {
            FieldWriter writer = MessageFields.writer(
                    9L
                            + replacement.messageId().length()
                            + replacement.source().value().length()
                            + replacement.scheduleDeliveryTime().length()
                            + replacement.validityPeriod().length()
                            + replacement.shortMessage().length()
                            + encodedTlvs.length,
                    limits);
            writer.writeCOctetString(replacement.messageId(), 65);
            MessageFields.writeAddress(writer, replacement.source(), 21);
            writer.writeCOctetString(replacement.scheduleDeliveryTime(), 17);
            writer.writeCOctetString(replacement.validityPeriod(), 17);
            writer.writeUnsignedByte(replacement.registeredDelivery());
            writer.writeUnsignedByte(replacement.defaultMessageId());
            writer.writeUnsignedByte(replacement.shortMessage().length());
            writer.writeOctets(replacement.shortMessage().value());
            writer.writeOctets(encodedTlvs);
            return writer.toByteArray();
        }
        if (command instanceof CancelSm cancel) {
            FieldWriter writer = MessageFields.writer(
                    8L
                            + cancel.serviceType().length()
                            + cancel.messageId().length()
                            + cancel.source().value().length()
                            + cancel.destination().value().length()
                            + encodedTlvs.length,
                    limits);
            writer.writeCOctetString(cancel.serviceType(), 6);
            writer.writeCOctetString(cancel.messageId(), 65);
            MessageFields.writeAddress(writer, cancel.source(), 21);
            MessageFields.writeAddress(writer, cancel.destination(), 21);
            writer.writeOctets(encodedTlvs);
            return writer.toByteArray();
        }
        if (command instanceof Outbind outbind) {
            FieldWriter writer = MessageFields.writer(
                    2L + outbind.systemId().length() + outbind.password().length() + encodedTlvs.length, limits);
            writer.writeCOctetString(outbind.systemId(), 16);
            writer.writeCOctetString(outbind.password(), 9);
            writer.writeOctets(encodedTlvs);
            return writer.toByteArray();
        }
        if (command instanceof AlertNotification alert) {
            FieldWriter writer = MessageFields.writer(
                    6L + alert.source().value().length() + alert.esme().value().length() + encodedTlvs.length, limits);
            MessageFields.writeAddress(writer, alert.source(), 65);
            MessageFields.writeAddress(writer, alert.esme(), 65);
            writer.writeOctets(encodedTlvs);
            return writer.toByteArray();
        }
        if (command instanceof CancelSmResponse || command instanceof ReplaceSmResponse) {
            MessageFields.writer(encodedTlvs.length, limits);
            return encodedTlvs;
        }
        QuerySmResponse response = (QuerySmResponse) command;
        byte[] tlvs = encodedTlvs;
        if (response.result().isEmpty()) return new byte[0];
        QuerySmResponse.Result result = response.result().orElseThrow();
        FieldWriter writer = MessageFields.writer(
                4L + result.messageId().length() + result.finalDate().length() + tlvs.length, limits);
        writer.writeCOctetString(result.messageId(), 65);
        writer.writeCOctetString(result.finalDate(), 17);
        writer.writeUnsignedByte(result.messageState());
        writer.writeUnsignedByte(result.errorCode());
        writer.writeOctets(tlvs);
        return writer.toByteArray();
    }

    private Command omittedResponse() {
        OptionalParameters empty = new OptionalParameters(List.of());
        if (id == 0x80000003L) return new QuerySmResponse(Optional.empty(), empty);
        if (id == 0x80000021L) return new SubmitMultiResponse(Optional.empty(), empty);
        if (id == 0x80000007L) return new ReplaceSmResponse(empty);
        return new CancelSmResponse(empty);
    }

    private static OptionalParameters parameters(Command command) {
        return switch (command) {
            case ReplaceSm replacement -> replacement.optionalParameters();
            case ReplaceSmResponse response -> response.optionalParameters();
            case SubmitMulti multi -> multi.optionalParameters();
            case SubmitMultiResponse response -> response.optionalParameters();
            case QuerySm query -> query.optionalParameters();
            case QuerySmResponse response -> response.optionalParameters();
            case CancelSm cancel -> cancel.optionalParameters();
            case CancelSmResponse response -> response.optionalParameters();
            case Outbind outbind -> outbind.optionalParameters();
            case AlertNotification alert -> alert.optionalParameters();
            default -> throw new IllegalArgumentException("Unsupported common command value");
        };
    }

    private void validate(Command command, long status, ProtocolProfile profile, boolean outgoing) {
        int esmClass = 0;
        int shortLength = 0;
        if (command instanceof QuerySm query) {
            MessageFields.validateAddress(query.source(), 21, outgoing);
        } else if (command instanceof ReplaceSm replacement) {
            MessageFields.validateAddress(replacement.source(), 21, outgoing);
            MessageFields.validateTime(replacement.scheduleDeliveryTime(), outgoing);
            MessageFields.validateTime(replacement.validityPeriod(), outgoing);
            if (outgoing) {
                MessageFields.validateFlags(
                        0, replacement.registeredDelivery(), MessageDirection.SUBMISSION, profile.version());
                if (profile.version() == SmppVersion.V3_4 && replacement.defaultMessageId() == 255)
                    throw new IllegalArgumentException("Predefined message index 255 requires SMPP 5.0");
            }
            shortLength = replacement.shortMessage().length();
        } else if (command instanceof SubmitMulti multi) {
            MultiFields.validate(multi, profile.version(), outgoing);
            esmClass = multi.esmClass();
            shortLength = multi.shortMessage().length();
        } else if (command instanceof SubmitMultiResponse response) {
            boolean omitted = status != 0 && profile.version() == SmppVersion.V5_0;
            if (response.result().isEmpty() != omitted)
                throw new IllegalArgumentException(
                        "Multiple-destination response body presence conflicts with status/profile");
            response.result()
                    .ifPresent(result -> result.unsuccessful()
                            .forEach(failure -> MessageFields.validateAddress(failure.destination(), 21, outgoing)));
        } else if (command instanceof CancelSm cancel) {
            MessageFields.validateAddress(cancel.source(), 21, outgoing);
            MessageFields.validateAddress(cancel.destination(), 21, outgoing);
        } else if (command instanceof AlertNotification alert) {
            MessageFields.validateAddress(alert.source(), 65, outgoing);
            MessageFields.validateAddress(alert.esme(), 65, outgoing);
        } else if (command instanceof QuerySmResponse response) {
            boolean omitted = status != 0 && profile.version() == SmppVersion.V5_0;
            if (response.result().isEmpty() != omitted)
                throw new IllegalArgumentException("Query response body presence conflicts with status/profile");
            if (response.result().isPresent()) {
                QuerySmResponse.Result result = response.result().orElseThrow();
                MessageFields.validateTime(result.finalDate(), outgoing);
                if (result.finalDate().endsWith("R"))
                    throw new IllegalArgumentException("Query final_date must be absolute");
                boolean intermediate = result.messageState() == 1
                        || (profile.version() == SmppVersion.V5_0 && result.messageState() == 0);
                if (intermediate && !result.finalDate().isEmpty())
                    throw new IllegalArgumentException("Intermediate message cannot have final_date");
                if (outgoing
                        && (result.messageState() > (profile.version() == SmppVersion.V3_4 ? 8 : 9)
                                || (profile.version() == SmppVersion.V3_4 && result.messageState() == 0)))
                    throw new IllegalArgumentException("Unsupported outgoing query message state");
            }
        }
        if (outgoing
                && status != 0
                && profile.version() == SmppVersion.V5_0
                && !parameters(command).entries().isEmpty())
            throw new IllegalArgumentException("Failed SMPP 5.0 response body must be omitted");
        if (shortLength > (profile.version() == SmppVersion.V3_4 ? 254 : 255))
            throw new IllegalArgumentException("short_message exceeds profile limit");
        tlvs.validate(id, profile.version(), parameters(command), outgoing, esmClass, shortLength);
    }
}
