package kg.aidarbek.simulator;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Finite client-side correlation by the opaque ID actually returned by submission. */
final class ReceiptScenarioLedger {
    private final Slot[] slots;
    private final int window;
    private final long timeoutNanos;
    private final Map<String, Slot> known = new HashMap<>();
    private final Map<String, Long> early = new HashMap<>();
    private final Set<String> seenReceipts = new HashSet<>();
    private int attempted;
    private int positive;
    private int negative;
    private int failures;
    private int receipts;
    private int receiptPositive;
    private int receiptNegative;
    private int matched;
    private int earlyReceipts;
    private int duplicates;
    private int unmatched;
    private int missing;
    private int invalidResponses;
    private int active;
    private int peakActive;
    private int peakEarly;
    private int awaitingResponses;
    private boolean closed;

    ReceiptScenarioLedger(int count, int window, long timeoutNanos) {
        if (count < 1
                || count > 10000
                || window < 1
                || window > 32
                || timeoutNanos <= 0
                || timeoutNanos > Long.MAX_VALUE / 4)
            throw new IllegalArgumentException("Finite positive count, window and timeout required");
        slots = new Slot[count];
        this.window = window;
        this.timeoutNanos = timeoutNanos;
    }

    synchronized int admit(long now) {
        if (closed || attempted == slots.length || active == window) return -1;
        int index = attempted++;
        slots[index] = new Slot(now + timeoutNanos);
        peakActive = Math.max(peakActive, ++active);
        awaitingResponses++;
        return index;
    }

    synchronized void submission(int index, long status, String id, long now) {
        Slot slot = slot(index);
        if (slot.responseKnown || slot.settled) {
            invalidResponses++;
            return;
        }
        slot.responseKnown = true;
        awaitingResponses--;
        if (status != 0) {
            negative++;
            settle(slot);
        } else {
            positive++;
            if (!validId(id) || known.containsKey(id)) {
                invalidResponses++;
                settle(slot);
            } else {
                slot.deadline = now + timeoutNanos;
                known.put(id, slot);
                if (early.remove(id) != null) {
                    matched++;
                    settle(slot);
                }
            }
        }
        reconcileUnknown();
    }

    synchronized boolean receipt(String id, long now) {
        receipts++;
        if (!validId(id) || closed) {
            unmatched++;
            receiptNegative++;
            return false;
        }
        if (seenReceipts.contains(id)) {
            duplicates++;
            receiptNegative++;
            return false;
        }
        if (seenReceipts.size() == slots.length) {
            unmatched++;
            receiptNegative++;
            return false;
        }
        seenReceipts.add(id);
        Slot slot = known.get(id);
        if (slot != null && !slot.settled) {
            matched++;
            settle(slot);
        } else if (slot == null && awaitingResponses > 0 && early.size() < window) {
            early.put(id, now + timeoutNanos);
            earlyReceipts++;
            peakEarly = Math.max(peakEarly, early.size());
        } else {
            unmatched++;
            receiptNegative++;
            return false;
        }
        receiptPositive++;
        return true;
    }

    synchronized void failedSubmission(int index) {
        Slot slot = slot(index);
        if (!slot.settled) abandon(slot);
        reconcileUnknown();
    }

    synchronized void expire(long now) {
        for (int index = 0; index < attempted; index++) {
            Slot slot = slots[index];
            if (!slot.settled && now - slot.deadline >= 0) abandon(slot);
        }
        var iterator = early.values().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next() >= 0) {
                unmatched++;
                iterator.remove();
            }
        }
        reconcileUnknown();
    }

    synchronized void finish() {
        closed = true;
        for (int index = 0; index < attempted; index++) if (!slots[index].settled) abandon(slots[index]);
        reconcileUnknown();
    }

    synchronized boolean settled(int index) {
        return slot(index).settled;
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(
                slots.length,
                attempted,
                positive,
                negative,
                failures,
                receipts,
                receiptPositive,
                receiptNegative,
                matched,
                earlyReceipts,
                duplicates,
                unmatched,
                missing,
                invalidResponses,
                active,
                early.size(),
                peakActive,
                peakEarly);
    }

    private void settle(Slot slot) {
        slot.settled = true;
        active--;
    }

    private Slot slot(int index) {
        if (index < 0 || index >= attempted) throw new IllegalArgumentException("Submission was not admitted");
        return slots[index];
    }

    private void abandon(Slot slot) {
        if (slot.responseKnown) missing++;
        else {
            failures++;
            awaitingResponses--;
        }
        settle(slot);
    }

    private void reconcileUnknown() {
        if (awaitingResponses == 0) {
            unmatched += early.size();
            early.clear();
        }
    }

    private static boolean validId(String id) {
        return id != null
                && !id.isEmpty()
                && id.length() <= 64
                && id.chars().allMatch(value -> value > 0 && value < 128);
    }

    private static final class Slot {
        private long deadline;
        private boolean responseKnown;
        private boolean settled;

        private Slot(long deadline) {
            this.deadline = deadline;
        }
    }

    record Snapshot(
            int planned,
            int attempted,
            int positiveSubmissions,
            int negativeSubmissions,
            int submissionFailures,
            int receiptRequests,
            int positiveReceiptDecisions,
            int negativeReceiptDecisions,
            int matched,
            int earlyReceipts,
            int duplicateReceipts,
            int unmatchedReceipts,
            int missingReceipts,
            int invalidSubmissionResponses,
            int active,
            int earlyHeld,
            int peakActive,
            int peakEarly) {
        boolean passed() {
            return attempted == planned
                    && positiveSubmissions + negativeSubmissions == planned
                    && submissionFailures == 0
                    && matched == positiveSubmissions
                    && receiptRequests == positiveSubmissions
                    && negativeReceiptDecisions == 0
                    && duplicateReceipts == 0
                    && unmatchedReceipts == 0
                    && missingReceipts == 0
                    && invalidSubmissionResponses == 0
                    && active == 0
                    && earlyHeld == 0;
        }
    }
}
