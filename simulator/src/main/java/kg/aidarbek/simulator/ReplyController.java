package kg.aidarbek.simulator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import kg.aidarbek.smpp.endpoint.BoundSession;
import kg.aidarbek.smpp.endpoint.HandlerResponse;
import kg.aidarbek.smpp.endpoint.IncomingRequest;
import kg.aidarbek.smpp.protocol.Command;

/** Bounded receiver-side fault decisions and stream-local content validation. */
final class ReplyController implements AutoCloseable {
    private final SimulatorConfig config;
    private final Supplier<ContentPlan> content;
    private final DecisionQueue decisions;
    private final LongSupplier clock;
    private final Map<UUID, Stream> streams = new LinkedHashMap<>();
    private long received, accepted, rejected, delayed, stalled, disconnected, invalid, capacityRejected;
    private long retiredStreams, retiredIncomplete, cancelledBeforeDecision, streamCapacityRejected;
    private int peakStreams;
    private boolean closed;

    public ReplyController(SimulatorConfig config, Supplier<ContentPlan> content) {
        this(config, content, System::nanoTime);
    }

    ReplyController(SimulatorConfig config, Supplier<ContentPlan> content, LongSupplier clock) {
        this.config = Objects.requireNonNull(config, "config");
        this.content = Objects.requireNonNull(content, "content");
        this.clock = Objects.requireNonNull(clock, "clock");
        decisions = new DecisionQueue(config.connections() * config.window());
    }

    public <Q extends Command, R extends Command> CompletionStage<HandlerResponse<R>> reply(
            IncomingRequest<Q> request, R success, R negative) {
        return reply(request, success, negative, null);
    }

    public <Q extends Command, R extends Command> CompletionStage<HandlerResponse<R>> reply(
            IncomingRequest<Q> request, R success, R negative, Supplier<TrafficContent> body) {
        FaultPolicy.Decision decision;
        boolean disconnect;
        synchronized (this) {
            if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Simulator receiver closed"));
            received++;
            if (request.isCancelled()) {
                cancelledBeforeDecision++;
                return CompletableFuture.failedFuture(new CancellationException("Request owner cancelled"));
            }
            Stream stream = streams.get(request.session().id());
            if (stream == null) {
                if (streams.size() == config.connections()) {
                    capacityRejected++;
                    streamCapacityRejected++;
                    return CompletableFuture.completedFuture(new HandlerResponse<>(0x58, negative));
                }
                stream = new Stream(content.get(), request.session());
                streams.put(request.session().id(), stream);
                peakStreams = Math.max(peakStreams, streams.size());
            }
            stream.received++;
            if (body != null) {
                try {
                    stream.content.validate(body.get());
                } catch (IllegalArgumentException failure) {
                    invalid++;
                    return CompletableFuture.completedFuture(new HandlerResponse<>(1, negative));
                }
            }
            disconnect = config.faults().disconnectAfter() > 0
                    && stream.received == config.faults().disconnectAfter();
            if (disconnect) disconnected++;
            decision = config.faults().decision(request.pdu().sequenceNumber());
            if (!disconnect)
                switch (decision) {
                    case ACCEPT -> accepted++;
                    case REJECT -> rejected++;
                    case DELAY -> delayed++;
                    case STALL -> stalled++;
                }
        }
        if (disconnect) {
            request.session().close();
            return CompletableFuture.failedFuture(new IllegalStateException("Configured disconnect"));
        }
        var response = decision == FaultPolicy.Decision.REJECT
                ? new HandlerResponse<>(config.faults().rejectionStatus(), negative)
                : new HandlerResponse<>(0, success);
        long delay = config.settings().consumerDelay().toNanos()
                + (decision == FaultPolicy.Decision.DELAY
                        ? config.faults().delay().toNanos()
                        : 0);
        if (delay > 0 || decision == FaultPolicy.Decision.STALL)
            return defer(response, negative, decision == FaultPolicy.Decision.STALL, delay, request);
        return CompletableFuture.completedFuture(response);
    }

    private <R extends Command> CompletionStage<HandlerResponse<R>> defer(
            HandlerResponse<R> response, R negative, boolean stalledDecision, long delay, IncomingRequest<?> request) {
        try {
            return decisions.defer(response, clock.getAsLong() + delay, stalledDecision, request::isCancelled);
        } catch (RejectedExecutionException full) {
            synchronized (this) {
                capacityRejected++;
            }
            return CompletableFuture.completedFuture(new HandlerResponse<>(0x58, negative));
        }
    }

    public void advance(long now) {
        decisions.advance(now);
        synchronized (this) {
            streams.values().removeIf(stream -> {
                if (!stream.terminated.isDone() || stream.terminated.isCompletedExceptionally()) return false;
                retiredIncomplete += stream.content.incompleteAssemblies();
                retiredStreams++;
                return true;
            });
        }
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(
                received,
                accepted,
                rejected,
                delayed,
                stalled,
                disconnected,
                invalid,
                capacityRejected,
                streams.size(),
                decisions.pending(),
                retiredIncomplete
                        + streams.values().stream()
                                .mapToLong(stream -> stream.content.incompleteAssemblies())
                                .sum(),
                retiredStreams,
                peakStreams,
                cancelledBeforeDecision,
                streamCapacityRejected,
                decisions.snapshot());
    }

    @Override
    public void close() {
        synchronized (this) {
            closed = true;
        }
        decisions.close();
    }

    private static final class Stream {
        private final ContentPlan content;
        private final CompletableFuture<Void> terminated;
        private long received;

        Stream(ContentPlan content, BoundSession session) {
            this.content = Objects.requireNonNull(content, "content");
            terminated = session.termination().toCompletableFuture();
        }
    }

    public record Snapshot(
            long received,
            long accepted,
            long rejected,
            long delayed,
            long stalled,
            long disconnected,
            long invalidContent,
            long capacityRejected,
            int retainedStreams,
            int pendingDecisions,
            long incompleteAssemblies,
            long retiredStreams,
            int peakRetainedStreams,
            long cancelledBeforeDecision,
            long streamCapacityRejected,
            DecisionQueue.Snapshot decisions) {
        Snapshot(
                long received,
                long accepted,
                long rejected,
                long delayed,
                long stalled,
                long disconnected,
                long invalidContent,
                long capacityRejected,
                int retainedStreams,
                int pendingDecisions,
                long incompleteAssemblies) {
            this(
                    received,
                    accepted,
                    rejected,
                    delayed,
                    stalled,
                    disconnected,
                    invalidContent,
                    capacityRejected,
                    retainedStreams,
                    pendingDecisions,
                    incompleteAssemblies,
                    0,
                    retainedStreams,
                    0,
                    0,
                    new DecisionQueue.Snapshot(0, 0, 0, 0, pendingDecisions, pendingDecisions));
        }
    }
}
