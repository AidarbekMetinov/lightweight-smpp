package kg.aidarbek.smpp.message;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import kg.aidarbek.smpp.protocol.OctetString;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.Tlv;
import org.junit.jupiter.api.Test;

class ReceiptTlvsTest {
    @Test
    void malformedAmbiguousAndOversizedReceiptFieldsAreRejectedWithoutGuessing() {
        for (Tlv malformed : List.of(
                new Tlv(0x001e, new byte[0]),
                new Tlv(0x001e, new byte[] {65}),
                new Tlv(0x001e, new byte[] {65, 0, 66, 0}),
                new Tlv(0x001e, new byte[] {(byte) 128, 0}),
                new Tlv(0x001e, new byte[66]),
                new Tlv(0x0427, new byte[0]),
                new Tlv(0x0427, new byte[2]),
                new Tlv(0x0423, new byte[2]),
                new Tlv(0x0423, new byte[4]))) {
            assertThrows(
                    IllegalArgumentException.class, () -> ReceiptTlvs.read(new OptionalParameters(List.of(malformed))));
        }
        for (Tlv duplicate :
                List.of(new Tlv(0x001e, new byte[] {0}), new Tlv(0x0427, new byte[] {2}), new Tlv(0x0423, new byte[3])))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ReceiptTlvs.read(new OptionalParameters(List.of(duplicate, duplicate))));
        List<Tlv> oversized = new ArrayList<>();
        for (int i = 0; i < 65; i++) oversized.add(new Tlv(0x1400, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> ReceiptTlvs.read(new OptionalParameters(oversized)));
        assertThrows(
                IllegalArgumentException.class,
                () -> ReceiptTlvs.read(new OptionalParameters(
                        List.of(new Tlv(0x1400, new byte[65535]), new Tlv(0x1401, new byte[1])))));
        for (String id : new String[] {"x".repeat(65), "A\0B", "é"})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ReceiptTlvs.build(Optional.of(id), OptionalInt.empty(), Optional.empty()));
        for (int state : new int[] {-1, 256})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ReceiptTlvs.build(Optional.empty(), OptionalInt.of(state), Optional.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> ReceiptTlvs.build(
                        Optional.empty(), OptionalInt.empty(), Optional.of(new OctetString(new byte[2]))));
        assertThrows(NullPointerException.class, () -> ReceiptTlvs.read(null));
    }

    @Test
    void receiptTlvsPreserveUnsignedStateOpaqueIdentityAndUnknownRawParameters() {
        OptionalParameters raw = new OptionalParameters(List.of(
                new Tlv(0x001e, new byte[] {48, 48, 65, 98, 0}),
                new Tlv(0x0427, new byte[] {(byte) 254}),
                new Tlv(0x0423, new byte[] {3, 0x12, 0x34}),
                new Tlv(0x1400, new byte[] {7, 8})));
        ReceiptTlvs receipt = ReceiptTlvs.read(raw);
        assertEquals("00Ab", receipt.messageId().orElseThrow());
        assertEquals(254, receipt.messageState().orElseThrow());
        assertArrayEquals(
                new byte[] {3, 0x12, 0x34},
                receipt.networkErrorCode().orElseThrow().value());
        assertEquals(raw, receipt.parameters());
        OptionalParameters built =
                ReceiptTlvs.build(receipt.messageId(), receipt.messageState(), receipt.networkErrorCode());
        assertEquals(raw.entries().subList(0, 3), built.entries());
        assertFalse(receipt.toString().contains("00Ab"));
        ReceiptTlvs absent = ReceiptTlvs.read(new OptionalParameters(List.of()));
        assertTrue(absent.messageId().isEmpty());
        assertTrue(absent.messageState().isEmpty());
        assertTrue(absent.networkErrorCode().isEmpty());
        assertTrue(ReceiptTlvs.build(Optional.empty(), OptionalInt.empty(), Optional.empty())
                .entries()
                .isEmpty());
        assertEquals(
                "",
                ReceiptTlvs.read(ReceiptTlvs.build(Optional.of(""), OptionalInt.of(0), Optional.empty()))
                        .messageId()
                        .orElseThrow());
    }
}
