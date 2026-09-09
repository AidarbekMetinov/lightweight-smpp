package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import kg.aidarbek.smpp.message.DeliveryReceipts;
import kg.aidarbek.smpp.message.MessageSegments;
import kg.aidarbek.smpp.message.ReceiptFormat;
import kg.aidarbek.smpp.message.ReceiptTlvs;
import kg.aidarbek.smpp.protocol.OctetString;
import org.junit.jupiter.api.Test;

class HelperContentPlansTest {
    @Test
    void replacementRequiresOriginalMetadataOutsideTheseHelperScenarios() {
        for (String variant : HelperContentPlans.names()) {
            assertFalse(
                    HelperContentPlans.supports(variant, "replace"),
                    variant + " cannot declare its encoding or receipt flags in replace_sm");
        }
    }

    @Test
    void operationCompatibilityRejectsReceiptSubmissionBeforeAnyEndpointStarts() {
        assertEquals(
                List.of("gsm7", "ucs2", "sar", "receipt", "receipt-flexible", "receipt-tlv"),
                HelperContentPlans.names());
        for (String variant : HelperContentPlans.names()) {
            assertTrue(HelperContentPlans.supports(variant, "deliver"));
            assertTrue(HelperContentPlans.supports(variant, "data"));
            assertFalse(HelperContentPlans.supports(variant, "query"));
            assertFalse(HelperContentPlans.supports(variant, "cancel"));
            assertFalse(HelperContentPlans.supports(variant, "none"));
            boolean text = variant.equals("gsm7") || variant.equals("ucs2");
            assertEquals(text || variant.equals("sar"), HelperContentPlans.supports(variant, "submit"));
            assertEquals(text || variant.equals("sar"), HelperContentPlans.supports(variant, "multi"));
        }
        assertFalse(HelperContentPlans.supports("unknown", "deliver"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> HelperContentPlans.names().clear());
        assertThrows(NullPointerException.class, () -> HelperContentPlans.supports(null, "submit"));
        assertThrows(NullPointerException.class, () -> HelperContentPlans.supports("gsm7", null));
    }

    @Test
    void receiptVariantsExerciseRawTextAndTlvPoliciesWithoutInventedFields() {
        ContentPlan example = HelperContentPlans.create("receipt", 5, 0xabcd);
        TrafficContent receipt = example.next(0);
        assertEquals(4, receipt.esmClass());
        assertEquals(1, receipt.dataCoding());
        String raw = new String(receipt.payload().value(), StandardCharsets.US_ASCII);
        assertEquals(
                "id:000000000000abcd sub:001 dlvrd:001 submit date:2609090000 done date:2609090001 stat:DELIVRD err:000 text:xxxxx",
                raw);
        example.validate(receipt);
        assertEquals(
                "xxxxx",
                DeliveryReceipts.parse(raw, ReceiptFormat.EXAMPLE).field("text").orElseThrow());
        assertThrows(
                IllegalArgumentException.class,
                () -> example.validate(new TrafficContent(0, 1, receipt.payload(), receipt.parameters())));

        ContentPlan flexible = HelperContentPlans.create("receipt-flexible", 100, 0xabcd);
        TrafficContent providerReceipt = flexible.next(0);
        var parsed = DeliveryReceipts.parse(
                new String(providerReceipt.payload().value(), StandardCharsets.US_ASCII), ReceiptFormat.FLEXIBLE);
        assertEquals("PROVIDER_PENDING", parsed.field("stat").orElseThrow());
        assertEquals("00A", parsed.field("provider_code").orElseThrow());
        assertTrue(parsed.field("dlvrd").isEmpty());
        flexible.validate(providerReceipt);

        ContentPlan tlv = HelperContentPlans.create("receipt-tlv", 0, 0xabcd);
        TrafficContent tlvReceipt = tlv.next(0);
        assertEquals(0, tlvReceipt.payload().length());
        assertEquals(4, tlvReceipt.esmClass());
        var fields = ReceiptTlvs.read(tlvReceipt.parameters());
        assertEquals("000000000000abcd", fields.messageId().orElseThrow());
        assertEquals(2, fields.messageState().orElseThrow());
        assertArrayEquals(
                new byte[] {3, 0, 0}, fields.networkErrorCode().orElseThrow().value());
        tlv.validate(tlvReceipt);
        assertThrows(IllegalArgumentException.class, () -> HelperContentPlans.create("receipt", 21, 0));
        assertThrows(IllegalArgumentException.class, () -> HelperContentPlans.create("receipt-flexible", 2001, 0));
        assertThrows(IllegalArgumentException.class, () -> HelperContentPlans.create("receipt-tlv", 1, 0));
    }

    @Test
    void sarUsesOneBoundedFixturePerStreamAndReportsMissingParts() {
        ContentPlan source = HelperContentPlans.create("sar", 161, 0x1234);
        TrafficContent first = source.next(0);
        TrafficContent second = source.next(1);
        assertEquals(first, source.next(2));
        assertEquals(
                2,
                MessageSegments.fromSar(first.payload(), first.parameters())
                        .orElseThrow()
                        .total());
        assertTrue(first.payload().length() <= 153);
        assertArrayEquals(
                new byte[] {0x12, 0x34}, first.parameters().entries().getFirst().value());
        ContentPlan receiver = HelperContentPlans.create("sar", 161, 0x1234);
        ContentPlan otherStream = HelperContentPlans.create("sar", 161, 0x1234);
        assertEquals(0, receiver.incompleteAssemblies());
        receiver.validate(second);
        otherStream.validate(first);
        assertEquals(1, receiver.incompleteAssemblies());
        receiver.validate(second);
        assertEquals(1, receiver.incompleteAssemblies());
        assertThrows(
                IllegalArgumentException.class,
                () -> receiver.validate(new TrafficContent(
                        0, 0, new OctetString(new byte[first.payload().length()]), first.parameters())));
        receiver.validate(first);
        assertEquals(0, receiver.incompleteAssemblies());
        receiver.validate(first);
        assertEquals(0, receiver.incompleteAssemblies());
        assertEquals(1, otherStream.incompleteAssemblies());
        assertThrows(IllegalArgumentException.class, () -> HelperContentPlans.create("sar", 160, 0));
        assertThrows(IllegalArgumentException.class, () -> HelperContentPlans.create("sar", 39016, 0));
    }

    @Test
    void textFixturesUseExplicitEncodingAndValidateExactReceivedContent() {
        ContentPlan gsm = HelperContentPlans.create("gsm7", 6, 0);
        TrafficContent first = gsm.next(0);
        assertArrayEquals(
                HexFormat.of().parseHex("4101107b1b14"), first.payload().value());
        assertEquals(0, first.esmClass());
        assertEquals(0, first.dataCoding());
        assertEquals(first, gsm.next(Long.MAX_VALUE));
        gsm.validate(first);
        assertThrows(
                IllegalArgumentException.class,
                () -> gsm.validate(new TrafficContent(0, 0, new OctetString(new byte[6]), first.parameters())));

        ContentPlan ucs2 = HelperContentPlans.create("ucs2", 8, 0);
        TrafficContent second = ucs2.next(0);
        assertArrayEquals(
                HexFormat.of().parseHex("004103a904166f22"), second.payload().value());
        assertEquals(8, second.dataCoding());
        ucs2.validate(second);
        assertThrows(IllegalArgumentException.class, () -> HelperContentPlans.create("ucs2", 7, 0));
        assertThrows(IllegalArgumentException.class, () -> HelperContentPlans.create("gsm7", 65536, 0));
        assertThrows(IllegalArgumentException.class, () -> HelperContentPlans.create("gsm7", -1, 0));
        assertThrows(IllegalArgumentException.class, () -> HelperContentPlans.create("unknown", 1, 0));
    }
}
