package kg.aidarbek.simulator;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import kg.aidarbek.smpp.message.ReceiptTlvs;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.DeliverSm;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.ShortMessage;
import kg.aidarbek.smpp.protocol.SubmitSm;

/** Exact, deliberately small synthetic workload representation. */
final class ReceiptScenarioMessages {
    private ReceiptScenarioMessages() {}

    static SubmitSm submission(int index) {
        if (index < 0 || index >= 10000) throw new IllegalArgumentException("Index outside finite workload");
        return new SubmitSm(
                fields(0, 1, ByteBuffer.allocate(4).putInt(index).array(), new OptionalParameters(List.of())));
    }

    static int submissionIndex(SubmitSm message) {
        ShortMessage fields = message.fields();
        if (fields.esmClass() != 0
                || fields.registeredDelivery() != 1
                || fields.dataCoding() != 4
                || fields.shortMessage().length() != 4
                || !fields.optionalParameters().entries().isEmpty())
            throw new IllegalArgumentException("Expected synthetic binary submission requesting one receipt");
        int index = ByteBuffer.wrap(fields.shortMessage().value()).getInt();
        if (index < 0 || index >= 10000) throw new IllegalArgumentException("Index outside finite workload");
        return index;
    }

    static DeliverSm receipt(String id) {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("Receipt ID is required");
        return new DeliverSm(
                fields(4, 0, new byte[0], ReceiptTlvs.build(Optional.of(id), OptionalInt.of(2), Optional.empty())));
    }

    static String receiptId(DeliverSm message) {
        ShortMessage fields = message.fields();
        if (fields.esmClass() != 4
                || fields.dataCoding() != 4
                || fields.registeredDelivery() != 0
                || fields.shortMessage().length() != 0
                || fields.optionalParameters().entries().size() != 2)
            throw new IllegalArgumentException("Expected the fixture's TLV-only receipt");
        ReceiptTlvs receipt = ReceiptTlvs.read(fields.optionalParameters());
        String id = receipt.messageId().orElse("");
        if (id.isEmpty() || receipt.messageState().orElse(-1) != 2)
            throw new IllegalArgumentException("Expected explicit opaque ID and synthetic DELIVERED state");
        return id;
    }

    private static ShortMessage fields(int esm, int registered, byte[] payload, OptionalParameters parameters) {
        return new ShortMessage(
                "",
                new Address(1, 1, esm == 0 ? "1000" : "2000"),
                new Address(1, 1, esm == 0 ? "2000" : "1000"),
                esm,
                0,
                0,
                "",
                "",
                registered,
                0,
                4,
                0,
                new OctetString(payload),
                parameters);
    }
}
