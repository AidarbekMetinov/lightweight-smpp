package kg.aidarbek.simulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import kg.aidarbek.smpp.endpoint.BoundSession;
import kg.aidarbek.smpp.endpoint.HandlerResponse;
import kg.aidarbek.smpp.endpoint.IncomingRequest;
import kg.aidarbek.smpp.protocol.DeliverSmResponse;
import kg.aidarbek.smpp.protocol.MessageResponse;
import kg.aidarbek.smpp.protocol.OptionalParameters;
import kg.aidarbek.smpp.protocol.SubmitSm;
import kg.aidarbek.smpp.protocol.SubmitSmResponse;
import kg.aidarbek.smpp.request.RequestHandle;
import kg.aidarbek.smpp.request.RequestOptions;

/** Bounded synthetic acceptance decisions; the owner polls receipt outcomes outside handlers. */
final class ReceiptScenarioServer {
    private final ReceiptScenarioOptions options;
    private final boolean[] seen;
    private final List<Entry> pending = new ArrayList<>();
    private int submissions;
    private int accepted;
    private int rejected;
    private int invalid;
    private int positiveDecisions;
    private int attempts;
    private int requests;
    private int positiveResponses;
    private int negativeResponses;
    private int failures;
    private int peak;
    private int faultSelections;
    private int faultActions;
    private boolean closed;

    ReceiptScenarioServer(ReceiptScenarioOptions options) {
        this.options = options;
        seen = new boolean[options.count()];
    }

    CompletionStage<HandlerResponse<SubmitSmResponse>> submit(IncomingRequest<SubmitSm> incoming) {
        int index;
        try {
            index = ReceiptScenarioMessages.submissionIndex(incoming.pdu().command());
        } catch (IllegalArgumentException malformed) {
            index = -1;
        }
        Entry entry = null;
        long status = 0;
        synchronized (this) {
            submissions++;
            if (closed || index < 0 || index >= seen.length || seen[index] || pending.size() >= options.window()) {
                invalid++;
                status = 0x08;
            } else {
                seen[index] = true;
                if (options.rejectEvery() != 0 && (index + 1) % options.rejectEvery() == 0) {
                    rejected++;
                    status = 0x58;
                } else {
                    accepted++;
                    ReceiptScenarioOptions.Fault fault =
                            accepted == 1 ? options.fault() : ReceiptScenarioOptions.Fault.NONE;
                    if (fault != ReceiptScenarioOptions.Fault.NONE) faultSelections++;
                    entry = new Entry(incoming.session(), "receipt-" + (index + 1), fault);
                    pending.add(entry);
                    peak = Math.max(peak, pending.size());
                }
            }
        }
        if (entry == null) return CompletableFuture.completedFuture(response(status, Optional.empty()));
        if (entry.fault == ReceiptScenarioOptions.Fault.MISSING) {
            synchronized (this) {
                faultActions++;
            }
            decide(entry);
        } else send(entry);
        return entry.decision;
    }

    private void send(Entry entry) {
        synchronized (this) {
            attempts++;
        }
        RequestHandle<DeliverSmResponse> handle;
        try {
            String id = entry.fault == ReceiptScenarioOptions.Fault.MISMATCH ? "wrong-" + entry.id : entry.id;
            handle = entry.session
                    .delivery()
                    .orElseThrow()
                    .send(ReceiptScenarioMessages.receipt(id), new RequestOptions(options.timeout()));
        } catch (RuntimeException failure) {
            synchronized (this) {
                failures++;
            }
            decide(entry);
            return;
        }
        boolean cancel;
        synchronized (this) {
            requests++;
            if (entry.fault == ReceiptScenarioOptions.Fault.MISMATCH || entry.duplicateSent) faultActions++;
            entry.handle = handle;
            cancel = closed;
        }
        if (cancel) handle.cancel();
    }

    void tick() {
        List<Entry> entries;
        synchronized (this) {
            entries = List.copyOf(pending);
        }
        for (Entry entry : entries) {
            RequestHandle<DeliverSmResponse> handle = entry.handle;
            if (handle == null) continue;
            var outcome = handle.terminalOutcome();
            if (outcome.isEmpty()) continue;
            synchronized (this) {
                if (outcome.get().response().isEmpty()) failures++;
                else if (outcome.get().response().orElseThrow().commandStatus() == 0) positiveResponses++;
                else negativeResponses++;
            }
            if (entry.fault == ReceiptScenarioOptions.Fault.DUPLICATE && !entry.duplicateSent) {
                entry.duplicateSent = true;
                entry.handle = null;
                send(entry);
            } else decide(entry);
        }
    }

    private void decide(Entry entry) {
        synchronized (this) {
            if (!pending.remove(entry)) return;
        }
        if (entry.decision.complete(response(0, Optional.of(entry.id)))) {
            synchronized (this) {
                positiveDecisions++;
            }
        }
    }

    void close() {
        List<Entry> entries;
        synchronized (this) {
            closed = true;
            entries = List.copyOf(pending);
        }
        for (Entry entry : entries) {
            RequestHandle<DeliverSmResponse> handle = entry.handle;
            if (handle != null) handle.cancel();
            synchronized (this) {
                failures++;
            }
            decide(entry);
        }
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(
                options.count(),
                submissions,
                accepted,
                rejected,
                invalid,
                positiveDecisions,
                attempts,
                requests,
                positiveResponses,
                negativeResponses,
                failures,
                pending.size(),
                peak,
                options.fault() == ReceiptScenarioOptions.Fault.NONE ? 0 : 1,
                faultSelections,
                faultActions);
    }

    private static HandlerResponse<SubmitSmResponse> response(long status, Optional<String> id) {
        return new HandlerResponse<>(
                status, new SubmitSmResponse(new MessageResponse(id, new OptionalParameters(List.of()))));
    }

    private static final class Entry {
        private final BoundSession session;
        private final String id;
        private final ReceiptScenarioOptions.Fault fault;
        private boolean duplicateSent;
        private final CompletableFuture<HandlerResponse<SubmitSmResponse>> decision = new CompletableFuture<>();
        private volatile RequestHandle<DeliverSmResponse> handle;

        private Entry(BoundSession session, String id, ReceiptScenarioOptions.Fault fault) {
            this.session = session;
            this.id = id;
            this.fault = fault;
        }
    }

    record Snapshot(
            int planned,
            int submissions,
            int acceptedSubmissions,
            int rejectedSubmissions,
            int invalidSubmissions,
            int positiveSubmissionDecisions,
            int receiptAttempts,
            int receiptRequests,
            int positiveReceiptResponses,
            int negativeReceiptResponses,
            int receiptFailures,
            int pendingDecisions,
            int peakDecisions,
            int expectedFaultActions,
            int faultSelections,
            int faultActions) {
        boolean passed() {
            return submissions == planned
                    && acceptedSubmissions + rejectedSubmissions == planned
                    && invalidSubmissions == 0
                    && positiveSubmissionDecisions == acceptedSubmissions
                    && receiptRequests == acceptedSubmissions
                    && positiveReceiptResponses == acceptedSubmissions
                    && negativeReceiptResponses == 0
                    && receiptFailures == 0
                    && pendingDecisions == 0
                    && expectedFaultActions == faultSelections
                    && faultSelections == faultActions;
        }
    }
}
