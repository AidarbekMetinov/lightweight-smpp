package kg.aidarbek.simulator;

import java.util.ArrayList;
import java.util.List;

/** Checks sampled ownership against the actual endpoint reservations and final retirement. */
final class PressureCriteria {
    private PressureCriteria() {}

    static List<String> evaluate(SimulatorConfig config, PressureSampler.Summary pressure) {
        var failures = new ArrayList<String>();
        var peaks = pressure.sampledPeaks();
        long requests = (long) config.connections() * config.window();
        long replies = (long) config.connections() * (config.window() + 8);
        long bytes =
                config.connections() * Math.max(1_048_576L, (long) config.window() * (config.payloadBytes() + 1024));
        if (peaks.physicalConnections() > config.connections()) failures.add("connection-bound-exceeded");
        if (peaks.observedSessions() > config.connections()) failures.add("observed-session-bound-exceeded");
        if (peaks.pendingRequests() > requests) failures.add("request-count-bound-exceeded");
        if (peaks.pendingRequestBytes() > bytes) failures.add("request-byte-bound-exceeded");
        if (peaks.pendingReplies() > replies) failures.add("reply-count-bound-exceeded");
        if (peaks.retainedReplyBytes() > bytes) failures.add("reply-byte-bound-exceeded");
        if (peaks.pendingDecisions() > requests) failures.add("decision-bound-exceeded");
        if (peaks.retainedStreams() > config.connections()) failures.add("stream-bound-exceeded");
        var latest = pressure.latest();
        if (latest.physicalConnections() != 0
                || latest.pendingRequests() != 0
                || latest.pendingRequestBytes() != 0
                || latest.pendingReplies() != 0
                || latest.retainedReplyBytes() != 0
                || latest.pendingDecisions() != 0
                || latest.retainedStreams() != 0) failures.add("ownership-remains-after-cleanup");
        return List.copyOf(failures);
    }
}
