package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import kg.aidarbek.smpp.protocol.Address;
import kg.aidarbek.smpp.protocol.DeliverSm;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.ShortMessage;
import kg.aidarbek.smpp.protocol.SubmitSm;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

class ReceiptScenarioMessagesTest {
    @Test
    void submissionRequestsAReceiptAndPreservesTheIndependentBinaryIndex() {
        SubmitSm message = ReceiptScenarioMessages.submission(258);
        assertNotNull(message);
        assertEquals(1, message.fields().registeredDelivery());
        assertEquals(4, message.fields().dataCoding());
        assertEquals(0, message.fields().esmClass());
        assertArrayEquals(
                new byte[] {0, 0, 1, 2}, message.fields().shortMessage().value());
        assertEquals(
                258,
                ReceiptScenarioMessages.submissionIndex(
                        new SubmitSm(fields(0, 1, new byte[] {0, 0, 1, 2}, List.of()))));
    }

    @Test
    void receiptUsesTheOpaqueIdAndSyntheticDeliveredStateWithoutTextInference() {
        DeliverSm receipt = ReceiptScenarioMessages.receipt("000A");
        assertNotNull(receipt);
        assertEquals(4, receipt.fields().esmClass());
        assertEquals(0, receipt.fields().shortMessage().length());
        assertEquals(
                List.of(new Tlv(0x001e, new byte[] {48, 48, 48, 65, 0}), new Tlv(0x0427, new byte[] {2})),
                receipt.fields().optionalParameters().entries());
        assertEquals(
                "000A",
                ReceiptScenarioMessages.receiptId(new DeliverSm(fields(
                        4,
                        0,
                        new byte[0],
                        List.of(new Tlv(0x001e, new byte[] {48, 48, 48, 65, 0}), new Tlv(0x0427, new byte[] {2}))))));
    }

    @Test
    void malformedWorkloadAndReceiptMetadataAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ReceiptScenarioMessages.submission(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> ReceiptScenarioMessages.submissionIndex(new SubmitSm(fields(0, 0, new byte[4], List.of()))));
        assertThrows(
                IllegalArgumentException.class,
                () -> ReceiptScenarioMessages.submissionIndex(new SubmitSm(fields(0, 1, new byte[3], List.of()))));
        assertThrows(IllegalArgumentException.class, () -> ReceiptScenarioMessages.receipt(""));
        assertThrows(
                IllegalArgumentException.class,
                () -> ReceiptScenarioMessages.receiptId(new DeliverSm(fields(
                        4, 0, new byte[0], List.of(new Tlv(0x001e, new byte[] {65, 0}), new Tlv(0x0427, new byte[] {1
                        }))))));
        assertThrows(
                IllegalArgumentException.class,
                () -> ReceiptScenarioMessages.receiptId(new DeliverSm(fields(0, 0, new byte[0], List.of()))));
    }

    private static ShortMessage fields(int esm, int registered, byte[] payload, List<Tlv> tlvs) {
        return new ShortMessage(
                "",
                new Address(1, 1, "1000"),
                new Address(1, 1, "2000"),
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
                new OptionalParameters(tlvs));
    }
}
