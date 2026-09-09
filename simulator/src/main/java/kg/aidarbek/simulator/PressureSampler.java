package kg.aidarbek.simulator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import kg.aidarbek.smpp.endpoint.SessionResources;

/** Bounded sampled request/reply ownership; it does not infer physical transport queue occupancy. */
final class PressureSampler {
    private final ReportWriter writer;
    private final Supplier<Observation> observations;
    private final LongSupplier clock;
    private final long started;
    private Observation initial, baseline, latest, peaks;
    private long samples;
    private volatile Summary published;

    PressureSampler(ReportWriter writer, Supplier<Observation> observations, LongSupplier clock) {
        this.writer = Objects.requireNonNull(writer, "writer");
        this.observations = Objects.requireNonNull(observations, "observations");
        this.clock = Objects.requireNonNull(clock, "clock");
        started = clock.getAsLong();
        sample();
    }

    static Observation observe(int connections, List<SessionResources> sessions, int decisions, int streams) {
        if (sessions.size() > 4096) throw new IllegalArgumentException("Too many observed sessions");
        long requests = 0, requestBytes = 0, replies = 0, replyBytes = 0;
        for (var session : sessions) {
            requests = Math.addExact(requests, session.pendingRequests());
            requestBytes = Math.addExact(requestBytes, session.pendingRequestBytes());
            replies = Math.addExact(replies, session.pendingReplies());
            replyBytes = Math.addExact(replyBytes, session.retainedReplyBytes());
        }
        return new Observation(
                connections, sessions.size(), requests, requestBytes, replies, replyBytes, decisions, streams);
    }

    synchronized void sample() {
        latest = Objects.requireNonNull(observations.get(), "observation");
        if (initial == null) initial = latest;
        peaks = peaks == null
                ? latest
                : new Observation(
                        Math.max(peaks.physicalConnections(), latest.physicalConnections()),
                        Math.max(peaks.observedSessions(), latest.observedSessions()),
                        Math.max(peaks.pendingRequests(), latest.pendingRequests()),
                        Math.max(peaks.pendingRequestBytes(), latest.pendingRequestBytes()),
                        Math.max(peaks.pendingReplies(), latest.pendingReplies()),
                        Math.max(peaks.retainedReplyBytes(), latest.retainedReplyBytes()),
                        Math.max(peaks.pendingDecisions(), latest.pendingDecisions()),
                        Math.max(peaks.retainedStreams(), latest.retainedStreams()));
        samples++;
        try {
            writer.pressure(clock.getAsLong() - started, latest);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        publish();
    }

    synchronized void baseline() {
        sample();
        baseline = latest;
        publish();
    }

    private void publish() {
        published = new Summary(baseline == null ? initial : baseline, latest, peaks, samples, baseline != null);
    }

    Summary summary() {
        return published;
    }

    record Observation(
            int physicalConnections,
            int observedSessions,
            long pendingRequests,
            long pendingRequestBytes,
            long pendingReplies,
            long retainedReplyBytes,
            int pendingDecisions,
            int retainedStreams) {
        Observation {
            if (physicalConnections < 0
                    || physicalConnections > 4096
                    || observedSessions < 0
                    || observedSessions > 4096
                    || pendingRequests < 0
                    || pendingRequestBytes < 0
                    || pendingReplies < 0
                    || retainedReplyBytes < 0
                    || pendingDecisions < 0
                    || pendingDecisions > 65536
                    || retainedStreams < 0
                    || retainedStreams > 4096)
                throw new IllegalArgumentException("Invalid bounded ownership observation");
        }
    }

    record Summary(
            Observation baseline,
            Observation latest,
            Observation sampledPeaks,
            long samples,
            boolean baselineRecorded) {}
}
