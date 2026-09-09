package kg.aidarbek.smpp.codec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import kg.aidarbek.smpp.profile.MessageDirection;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.MultiDestination;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.SubmitMulti;
import kg.aidarbek.smpp.protocol.SubmitMultiResponse;
import kg.aidarbek.smpp.protocol.UnsuccessfulDestination;

/** Counted submit_multi alternatives and per-destination result layouts. */
final class MultiFields {
    private MultiFields() {}

    static void validate(SubmitMulti command, SmppVersion version, boolean outgoing) {
        MessageFields.validateAddress(command.source(), 21, outgoing);
        if (command.destinations().size() > (version == SmppVersion.V3_4 ? 254 : 255))
            throw new IllegalArgumentException("Destination count exceeds profile limit");
        for (MultiDestination destination : command.destinations()) {
            if (destination instanceof MultiDestination.Sme sme)
                MessageFields.validateAddress(sme.address(), 21, outgoing);
        }
        MessageFields.validateTime(command.scheduleDeliveryTime(), outgoing);
        MessageFields.validateTime(command.validityPeriod(), outgoing);
        if (outgoing) {
            MessageFields.validateFlags(
                    command.esmClass(), command.registeredDelivery(), MessageDirection.SUBMISSION, version);
            if (command.priorityFlag() > 3 || command.replaceIfPresentFlag() > 1)
                throw new IllegalArgumentException("Unsupported message priority or replacement flag");
            if (version == SmppVersion.V3_4
                    && ((command.esmClass() & 3) == 2
                            || command.replaceIfPresentFlag() != 0
                            || command.defaultMessageId() == 255))
                throw new IllegalArgumentException("Unsupported SMPP 3.4 multiple-destination field value");
        }
    }

    static SubmitMulti read(FieldReader reader, SmppVersion version, PduLimits limits) {
        String service = reader.readCOctetString(6);
        Address source = MessageFields.readAddress(reader, 21);
        int count = reader.readUnsignedByte();
        if (count == 0 || count > (version == SmppVersion.V3_4 ? 254 : 255) || count > reader.remaining() / 2)
            throw new FieldCodecException("Invalid or truncated destination count");
        List<MultiDestination> destinations = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int flag = reader.readUnsignedByte();
            destinations.add(
                    switch (flag) {
                        case 1 -> new MultiDestination.Sme(MessageFields.readAddress(reader, 21));
                        case 2 -> new MultiDestination.DistributionList(reader.readCOctetString(21));
                        default ->
                            throw new FieldCodecException("Unknown destination discriminator has no defined layout");
                    });
        }
        return new SubmitMulti(
                service,
                source,
                destinations,
                reader.readUnsignedByte(),
                reader.readUnsignedByte(),
                reader.readUnsignedByte(),
                reader.readCOctetString(17),
                reader.readCOctetString(17),
                reader.readUnsignedByte(),
                reader.readUnsignedByte(),
                reader.readUnsignedByte(),
                reader.readUnsignedByte(),
                new OctetString(reader.readOctets(reader.readUnsignedByte())),
                MessageFields.readParameters(reader, limits));
    }

    static byte[] write(SubmitMulti command, byte[] tlvs, PduLimits limits) {
        long length = 15L
                + command.serviceType().length()
                + command.source().value().length()
                + command.scheduleDeliveryTime().length()
                + command.validityPeriod().length()
                + command.shortMessage().length()
                + tlvs.length;
        for (MultiDestination destination : command.destinations())
            length += switch (destination) {
                case MultiDestination.Sme sme -> 4L + sme.address().value().length();
                case MultiDestination.DistributionList list -> 2L + list.name().length();
            };
        FieldWriter writer = MessageFields.writer(length, limits);
        writer.writeCOctetString(command.serviceType(), 6);
        MessageFields.writeAddress(writer, command.source(), 21);
        writer.writeUnsignedByte(command.destinations().size());
        for (MultiDestination destination : command.destinations()) {
            switch (destination) {
                case MultiDestination.Sme sme -> {
                    writer.writeUnsignedByte(1);
                    MessageFields.writeAddress(writer, sme.address(), 21);
                }
                case MultiDestination.DistributionList list -> {
                    writer.writeUnsignedByte(2);
                    writer.writeCOctetString(list.name(), 21);
                }
            }
        }
        writer.writeUnsignedByte(command.esmClass());
        writer.writeUnsignedByte(command.protocolId());
        writer.writeUnsignedByte(command.priorityFlag());
        writer.writeCOctetString(command.scheduleDeliveryTime(), 17);
        writer.writeCOctetString(command.validityPeriod(), 17);
        writer.writeUnsignedByte(command.registeredDelivery());
        writer.writeUnsignedByte(command.replaceIfPresentFlag());
        writer.writeUnsignedByte(command.dataCoding());
        writer.writeUnsignedByte(command.defaultMessageId());
        writer.writeUnsignedByte(command.shortMessage().length());
        writer.writeOctets(command.shortMessage().value());
        writer.writeOctets(tlvs);
        return writer.toByteArray();
    }

    static SubmitMultiResponse readResponse(FieldReader reader, PduLimits limits) {
        String messageId = reader.readCOctetString(65);
        int count = reader.readUnsignedByte();
        if (count > reader.remaining() / 7) throw new FieldCodecException("Truncated unsuccessful destination count");
        List<UnsuccessfulDestination> unsuccessful = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            unsuccessful.add(
                    new UnsuccessfulDestination(MessageFields.readAddress(reader, 21), reader.readUnsignedInt()));
        return new SubmitMultiResponse(
                Optional.of(new SubmitMultiResponse.Result(messageId, unsuccessful)),
                MessageFields.readParameters(reader, limits));
    }

    static byte[] writeResponse(SubmitMultiResponse command, byte[] tlvs, PduLimits limits) {
        if (command.result().isEmpty()) return new byte[0];
        SubmitMultiResponse.Result result = command.result().orElseThrow();
        long length = 2L + result.messageId().length() + tlvs.length;
        for (UnsuccessfulDestination failure : result.unsuccessful())
            length += 7L + failure.destination().value().length();
        FieldWriter writer = MessageFields.writer(length, limits);
        writer.writeCOctetString(result.messageId(), 65);
        writer.writeUnsignedByte(result.unsuccessful().size());
        for (UnsuccessfulDestination failure : result.unsuccessful()) {
            MessageFields.writeAddress(writer, failure.destination(), 21);
            writer.writeUnsignedInt(failure.status());
        }
        writer.writeOctets(tlvs);
        return writer.toByteArray();
    }
}
