package kg.aidarbek.smpp.codec;

import java.util.Set;
import kg.aidarbek.smpp.profile.MessageDirection;
import kg.aidarbek.smpp.profile.SmppVersion;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.ShortMessage;

/** Shared standard message wire fields; command-specific policies remain explicit at the caller. */
final class MessageFields {
    private MessageFields() {}

    static void validate(ShortMessage fields, long commandId, SmppVersion version, boolean outgoing) {
        validateAddress(fields.source(), 21, outgoing);
        validateAddress(fields.destination(), 21, outgoing);
        MessageDirection direction = commandId == 4 ? MessageDirection.SUBMISSION : MessageDirection.DELIVERY;
        if (outgoing) validateFlags(fields.esmClass(), fields.registeredDelivery(), direction, version);
        if (outgoing && (fields.priorityFlag() > 3 || fields.replaceIfPresentFlag() > 1))
            throw new IllegalArgumentException("Unsupported message priority or replacement flag");
        if (fields.shortMessage().length() > (version == SmppVersion.V3_4 ? 254 : 255))
            throw new IllegalArgumentException("short_message exceeds profile limit");
        if (outgoing && version == SmppVersion.V3_4 && fields.defaultMessageId() == 255)
            throw new IllegalArgumentException("Predefined message index 255 requires SMPP 5.0");
        validateTime(fields.scheduleDeliveryTime(), outgoing);
        validateTime(fields.validityPeriod(), outgoing);
        if (commandId == 5
                && version == SmppVersion.V3_4
                && (!fields.scheduleDeliveryTime().isEmpty()
                        || !fields.validityPeriod().isEmpty()
                        || (outgoing && (fields.replaceIfPresentFlag() != 0 || fields.defaultMessageId() != 0))))
            throw new IllegalArgumentException("SMPP 3.4 delivery fields must be empty or zero");
    }

    static void validateAddress(Address address, int maximumOctets, boolean outgoing) {
        if (address.value().length() >= maximumOctets
                || (outgoing && (address.ton() > 6 || !supportedNpi(address.npi()))))
            throw new IllegalArgumentException("Unsupported or overlong message address");
    }

    static boolean supportedNpi(int value) {
        return Set.of(0, 1, 3, 4, 6, 8, 9, 10, 14, 18).contains(value);
    }

    static void validateFlags(int esm, int registered, MessageDirection direction, SmppVersion version) {
        int type = esm & 0x3c;
        boolean submission34 = version == SmppVersion.V3_4 && direction == MessageDirection.SUBMISSION;
        if (!(submission34 ? Set.of(0, 8, 16) : Set.of(0, 4, 8, 16, 24, 32)).contains(type))
            throw new IllegalArgumentException("Unsupported esm_class message type");
        if ((registered & 0xe0) != 0 || (version == SmppVersion.V3_4 && (registered & 3) == 3))
            throw new IllegalArgumentException("Unsupported registered_delivery flags");
    }

    static void validateTime(String time, boolean outgoing) {
        if (time.isEmpty()) return;
        if (time.length() != 16) throw new IllegalArgumentException("Message time must contain 16 characters");
        for (int i = 0; i < 15; i++)
            if (time.charAt(i) < '0' || time.charAt(i) > '9')
                throw new IllegalArgumentException("Message time components must be decimal digits");
        char orientation = time.charAt(15);
        boolean relative = orientation == 'R';
        if (!relative && orientation != '+' && orientation != '-')
            throw new IllegalArgumentException("Message time orientation is unsupported");
        int month = component(time, 2), day = component(time, 4);
        if (month > 12
                || day > 31
                || (!relative && (month == 0 || day == 0))
                || component(time, 6) > 23
                || component(time, 8) > 59
                || component(time, 10) > 59
                || (!relative && component(time, 13) > 48))
            throw new IllegalArgumentException("Message time component is outside its range");
        if (relative && outgoing && !time.substring(12, 15).equals("000"))
            throw new IllegalArgumentException("Outgoing relative time requires unused suffix 000");
    }

    private static int component(String value, int offset) {
        return (value.charAt(offset) - '0') * 10 + value.charAt(offset + 1) - '0';
    }

    static Address readAddress(FieldReader reader, int maximumOctets) {
        return new Address(
                reader.readUnsignedByte(), reader.readUnsignedByte(), reader.readCOctetString(maximumOctets));
    }

    static void writeAddress(FieldWriter writer, Address address, int maximumOctets) {
        writer.writeUnsignedByte(address.ton());
        writer.writeUnsignedByte(address.npi());
        writer.writeCOctetString(address.value(), maximumOctets);
    }

    static OptionalParameters readParameters(FieldReader reader, PduLimits limits) {
        if (reader.remaining() > limits.maximumTlvLength())
            throw new FieldCodecException("Message optional parameters exceed configured bound");
        return TlvCodec.decode(
                reader.readOctets(reader.remaining()), limits.maximumTlvLength(), limits.maximumTlvCount());
    }

    static ShortMessage readShortMessage(FieldReader reader, PduLimits limits) {
        String service = reader.readCOctetString(6);
        Address source = readAddress(reader, 21);
        Address destination = readAddress(reader, 21);
        int esm = reader.readUnsignedByte();
        int protocol = reader.readUnsignedByte();
        int priority = reader.readUnsignedByte();
        String schedule = reader.readCOctetString(17);
        String validity = reader.readCOctetString(17);
        int registered = reader.readUnsignedByte();
        int replace = reader.readUnsignedByte();
        int coding = reader.readUnsignedByte();
        int defaultId = reader.readUnsignedByte();
        OctetString octets = new OctetString(reader.readOctets(reader.readUnsignedByte()));
        return new ShortMessage(
                service,
                source,
                destination,
                esm,
                protocol,
                priority,
                schedule,
                validity,
                registered,
                replace,
                coding,
                defaultId,
                octets,
                readParameters(reader, limits));
    }

    static byte[] writeShortMessage(ShortMessage fields, byte[] encodedTlvs, PduLimits limits) {
        FieldWriter writer = writer(
                17L
                        + fields.serviceType().length()
                        + fields.source().value().length()
                        + fields.destination().value().length()
                        + fields.scheduleDeliveryTime().length()
                        + fields.validityPeriod().length()
                        + fields.shortMessage().length()
                        + encodedTlvs.length,
                limits);
        writer.writeCOctetString(fields.serviceType(), 6);
        writeAddress(writer, fields.source(), 21);
        writeAddress(writer, fields.destination(), 21);
        writer.writeUnsignedByte(fields.esmClass());
        writer.writeUnsignedByte(fields.protocolId());
        writer.writeUnsignedByte(fields.priorityFlag());
        writer.writeCOctetString(fields.scheduleDeliveryTime(), 17);
        writer.writeCOctetString(fields.validityPeriod(), 17);
        writer.writeUnsignedByte(fields.registeredDelivery());
        writer.writeUnsignedByte(fields.replaceIfPresentFlag());
        writer.writeUnsignedByte(fields.dataCoding());
        writer.writeUnsignedByte(fields.defaultMessageId());
        writer.writeUnsignedByte(fields.shortMessage().length());
        writer.writeOctets(fields.shortMessage().value());
        writer.writeOctets(encodedTlvs);
        return writer.toByteArray();
    }

    static FieldWriter writer(long length, PduLimits limits) {
        if (length > limits.maximumBodyLength())
            throw new IllegalArgumentException("Message body exceeds configured bound");
        return new FieldWriter((int) length);
    }
}
