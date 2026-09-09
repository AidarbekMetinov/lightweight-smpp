package kg.aidarbek.simulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReceiptScenarioLedgerTest {
    @Test
    void admissionCountsTheWholeRoundTripUntilTheMatchingReceiptArrives() {
        var ledger = new ReceiptScenarioLedger(2, 1, 10);
        assertEquals(0, ledger.admit(0));
        assertEquals(-1, ledger.admit(1));
        ledger.submission(0, 0, "Opaque-000A", 2);
        assertEquals(-1, ledger.admit(3));
        assertTrue(ledger.receipt("Opaque-000A", 4));
        assertEquals(1, ledger.admit(5));
        ledger.submission(1, 0x58, "", 6);
        var result = ledger.snapshot();
        assertEquals(1, result.positiveSubmissions());
        assertEquals(1, result.negativeSubmissions());
        assertEquals(1, result.receiptRequests());
        assertEquals(1, result.matched());
        assertEquals(0, result.active());
        assertTrue(result.passed());
    }

    @Test
    void earlyReceiptIsHeldBoundedlyThenMatchedAgainstTheReturnedOpaqueId() {
        var ledger = new ReceiptScenarioLedger(1, 1, 10);
        ledger.admit(0);
        assertTrue(ledger.receipt("000a", 1));
        assertEquals(1, ledger.snapshot().earlyHeld());
        assertEquals(0, ledger.snapshot().matched());
        ledger.submission(0, 0, "000a", 2);
        var result = ledger.snapshot();
        assertEquals(1, result.earlyReceipts());
        assertEquals(1, result.matched());
        assertEquals(0, result.earlyHeld());
        assertEquals(1, result.peakActive());
        assertEquals(1, result.peakEarly());
        assertTrue(result.passed());
    }

    @Test
    void duplicateReceiptsAreRejectedWithoutReleasingAnotherReservation() {
        var ledger = new ReceiptScenarioLedger(1, 1, 10);
        ledger.admit(0);
        assertTrue(ledger.receipt("id", 1));
        assertFalse(ledger.receipt("id", 2));
        ledger.submission(0, 0, "id", 3);
        assertFalse(ledger.receipt("id", 4));
        var result = ledger.snapshot();
        assertEquals(1, result.matched());
        assertEquals(2, result.duplicateReceipts());
        assertEquals(2, result.negativeReceiptDecisions());
        assertEquals(0, result.active());
        assertFalse(result.passed());
    }

    @Test
    void mismatchedOpaqueIdsAreNotNormalizedAndMissingReceiptsExpire() {
        var ledger = new ReceiptScenarioLedger(1, 1, 10);
        ledger.admit(0);
        ledger.submission(0, 0, "000A", 1);
        assertFalse(ledger.receipt("000a", 2));
        ledger.expire(11);
        assertEquals(1, ledger.snapshot().unmatchedReceipts());
        assertEquals(1, ledger.snapshot().missingReceipts());
        assertEquals(0, ledger.snapshot().active());
        assertFalse(ledger.snapshot().passed());
    }

    @Test
    void earlyMismatchIsDetectedWhenAllAdmittedResponsesBecomeKnown() {
        var ledger = new ReceiptScenarioLedger(1, 1, 10);
        ledger.admit(0);
        assertTrue(ledger.receipt("wrong", 1));
        ledger.submission(0, 0, "returned", 2);
        assertEquals(1, ledger.snapshot().unmatchedReceipts());
        assertEquals(0, ledger.snapshot().earlyHeld());
        ledger.finish();
        assertEquals(1, ledger.snapshot().missingReceipts());
        assertEquals(0, ledger.snapshot().active());
        assertFalse(ledger.snapshot().passed());
    }

    @Test
    void earlyUnknownStorageAndTotalAdmissionHaveIndependentFiniteBounds() {
        var ledger = new ReceiptScenarioLedger(2, 1, 10);
        ledger.admit(0);
        assertTrue(ledger.receipt("one", 1));
        assertFalse(ledger.receipt("excess", 2));
        assertEquals(1, ledger.snapshot().earlyHeld());
        assertEquals(1, ledger.snapshot().peakEarly());
        ledger.failedSubmission(0);
        assertEquals(1, ledger.admit(3));
        ledger.submission(1, 0x58, "", 4);
        assertEquals(-1, ledger.admit(5));
        ledger.finish();
        assertEquals(1, ledger.snapshot().submissionFailures());
        assertEquals(0, ledger.snapshot().earlyHeld());
        assertEquals(0, ledger.snapshot().active());
    }

    @Test
    void deadlinesWrapSafelyAndExpiryAndFinishDoNotSettleTwice() {
        long start = Long.MAX_VALUE - 5;
        var ledger = new ReceiptScenarioLedger(2, 2, 10);
        ledger.admit(start);
        ledger.admit(start);
        ledger.submission(0, 0, "accepted", start);
        ledger.expire(start + 9);
        assertEquals(2, ledger.snapshot().active());
        ledger.expire(start + 10);
        ledger.expire(start + 20);
        ledger.finish();
        assertEquals(1, ledger.snapshot().missingReceipts());
        assertEquals(1, ledger.snapshot().submissionFailures());
        assertEquals(0, ledger.snapshot().active());
    }

    @Test
    void repeatedOrEmptyPositiveIdsCannotCorrelateTwoSubmissions() {
        var ledger = new ReceiptScenarioLedger(3, 3, 10);
        for (int i = 0; i < 3; i++) ledger.admit(0);
        ledger.submission(0, 0, "id", 1);
        ledger.submission(1, 0, "id", 1);
        ledger.submission(2, 0, "", 1);
        assertTrue(ledger.receipt("id", 2));
        assertEquals(3, ledger.snapshot().positiveSubmissions());
        assertEquals(2, ledger.snapshot().invalidSubmissionResponses());
        assertEquals(1, ledger.snapshot().matched());
        assertEquals(0, ledger.snapshot().active());
        assertFalse(ledger.snapshot().passed());
    }

    @Test
    void nonsensicalBoundsAndUnadmittedResponseIndicesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ReceiptScenarioLedger(0, 1, 10));
        assertThrows(IllegalArgumentException.class, () -> new ReceiptScenarioLedger(10001, 1, 10));
        assertThrows(IllegalArgumentException.class, () -> new ReceiptScenarioLedger(1, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new ReceiptScenarioLedger(1, 33, 10));
        assertThrows(IllegalArgumentException.class, () -> new ReceiptScenarioLedger(1, 1, 0));
        var ledger = new ReceiptScenarioLedger(1, 1, 10);
        assertThrows(IllegalArgumentException.class, () -> ledger.submission(0, 0, "id", 0));
    }
}
