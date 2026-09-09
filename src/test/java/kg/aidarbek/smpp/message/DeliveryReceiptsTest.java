package kg.aidarbek.smpp.message;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeliveryReceiptsTest {
    @Test
    void canonicalBuildingRejectsAmbiguousLabelsAndNonTextEdgeSpaces() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DeliveryReceipts.build(Map.of(" id", "a"), ReceiptFormat.FLEXIBLE));
        assertThrows(
                IllegalArgumentException.class,
                () -> DeliveryReceipts.build(Map.of("id", "a ", "stat", "X"), ReceiptFormat.FLEXIBLE));
        assertThrows(
                IllegalArgumentException.class,
                () -> DeliveryReceipts.build(Map.of("id", " a", "stat", "X"), ReceiptFormat.FLEXIBLE));
        assertEquals("text: a ", DeliveryReceipts.build(Map.of("text", " a "), ReceiptFormat.FLEXIBLE));
    }

    @Test
    void providerVariantsRequireAnExplicitPolicyAndNeverInventMissingState() {
        String raw = "ID:Ab00 SUBMIT DATE:202609091020 provider_code:00A Text:  raw id:unchanged";
        assertThrows(IllegalArgumentException.class, () -> DeliveryReceipts.parse(raw, ReceiptFormat.EXAMPLE));
        DeliveryReceipt receipt = DeliveryReceipts.parse(raw, ReceiptFormat.FLEXIBLE);
        assertEquals(raw, receipt.rawText());
        assertEquals("Ab00", receipt.field("id").orElseThrow());
        assertEquals("202609091020", receipt.field("submit date").orElseThrow());
        assertEquals("00A", receipt.field("provider_code").orElseThrow());
        assertEquals("  raw id:unchanged", receipt.field("text").orElseThrow());
        assertTrue(receipt.field("stat").isEmpty());
        assertEquals(
                receipt.fields(),
                DeliveryReceipts.parse(
                                DeliveryReceipts.build(receipt.fields(), ReceiptFormat.FLEXIBLE),
                                ReceiptFormat.FLEXIBLE)
                        .fields());
        assertTrue(DeliveryReceipts.parse("", ReceiptFormat.FLEXIBLE).fields().isEmpty());
        assertEquals("", DeliveryReceipts.build(Map.of(), ReceiptFormat.FLEXIBLE));
        assertEquals(
                "NEW_PROVIDER_STATE",
                DeliveryReceipts.parse("stat:NEW_PROVIDER_STATE", ReceiptFormat.FLEXIBLE)
                        .field("stat")
                        .orElseThrow());
    }

    @Test
    void receiptGrammarAndRetainedFieldsHaveExplicitFiniteBounds() {
        for (String raw :
                new String[] {"garbage", "ID:a id:b", "id:a id:", "a".repeat(33) + ":value", "text:" + "x".repeat(4092)
                })
            assertThrows(IllegalArgumentException.class, () -> DeliveryReceipts.parse(raw, ReceiptFormat.FLEXIBLE));
        assertThrows(IllegalArgumentException.class, () -> DeliveryReceipts.parse("id:a", ReceiptFormat.EXAMPLE));
        Map<String, String> tooMany = new LinkedHashMap<>();
        for (int i = 0; i < 33; i++) tooMany.put("field" + i, "value");
        assertThrows(IllegalArgumentException.class, () -> DeliveryReceipts.build(tooMany, ReceiptFormat.FLEXIBLE));
        assertThrows(
                IllegalArgumentException.class,
                () -> DeliveryReceipts.build(Map.of("note", "contains id:another"), ReceiptFormat.FLEXIBLE));
        assertThrows(
                IllegalArgumentException.class,
                () -> DeliveryReceipts.build(Map.of("ID", "a", "id", "b"), ReceiptFormat.FLEXIBLE));
        assertThrows(
                IllegalArgumentException.class,
                () -> DeliveryReceipts.build(Map.of("text", "x".repeat(4092)), ReceiptFormat.FLEXIBLE));
        assertThrows(NullPointerException.class, () -> DeliveryReceipts.parse(null, ReceiptFormat.FLEXIBLE));
        assertThrows(NullPointerException.class, () -> DeliveryReceipts.parse("id:a", null));
    }

    @Test
    void exampleReceiptPreservesOpaqueIdsDatesCountsStatesAndText() {
        String text =
                "id:00Ab00CD12 sub:001 dlvrd:000 submit date:2609091020 done date:2609091021 stat:MYSTERY err:007 text:Hello id:original";
        DeliveryReceipt receipt = DeliveryReceipts.parse(text, ReceiptFormat.EXAMPLE);
        assertEquals("00Ab00CD12", receipt.field("id").orElseThrow());
        assertEquals("001", receipt.field("sub").orElseThrow());
        assertEquals("2609091020", receipt.field("submit date").orElseThrow());
        assertEquals("MYSTERY", receipt.field("stat").orElseThrow());
        assertEquals("007", receipt.field("err").orElseThrow());
        assertEquals("Hello id:original", receipt.field("text").orElseThrow());
        assertEquals(text, receipt.rawText());
        assertEquals(text, DeliveryReceipts.build(receipt.fields(), ReceiptFormat.EXAMPLE));
        assertFalse(receipt.toString().contains("00Ab00CD12"));
        assertThrows(UnsupportedOperationException.class, () -> receipt.fields().clear());
    }
}
