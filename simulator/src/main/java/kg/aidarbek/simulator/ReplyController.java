package kg.aidarbek.simulator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import kg.aidarbek.smpp.endpoint.HandlerResponse;
import kg.aidarbek.smpp.endpoint.IncomingRequest;
import kg.aidarbek.smpp.protocol.Command;

/** Bounded receiver-side fault decisions and stream-local content validation. */
final class ReplyController implements AutoCloseable {
    private final SimulatorConfig config;
    private final Supplier<ContentPlan> content;
    private final DecisionQueue decisions;
    private final Map<UUID, Stream> streams = new LinkedHashMap<>();
    private long received, accepted, rejected, delayed, stalled, disconnected, invalid, capacityRejected;
    private boolean closed;

    public ReplyController(SimulatorConfig config, Supplier<ContentPlan> content) {
        this.config = Objects.requireNonNull(config, "config");
        this.content = Objects.requireNonNull(content, "content");
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
            Stream stream = streams.get(request.session().id());
            if (stream == null) {
                if (streams.size() == config.connections()) {
                    capacityRejected++;
                    return CompletableFuture.completedFuture(new HandlerResponse<>(0x58, negative));
                }
                stream = new Stream(content.get());
                streams.put(request.session().id(), stream);
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
        return switch (decision) {
            case ACCEPT -> CompletableFuture.completedFuture(new HandlerResponse<>(0, success));
            case REJECT ->
                CompletableFuture.completedFuture(
                        new HandlerResponse<>(config.faults().rejectionStatus(), negative));
            case DELAY, STALL ->
                defer(new HandlerResponse<>(0, success), negative, decision == FaultPolicy.Decision.STALL);
        };
    }

    private <R extends Command> CompletionStage<HandlerResponse<R>> defer(
            HandlerResponse<R> response, R negative, boolean stalledDecision) {
        try {
            return decisions.defer(
                    response, System.nanoTime() + config.faults().delay().toNanos(), stalledDecision);
        } catch (RejectedExecutionException full) {
            synchronized (this) {
                capacityRejected++;
            }
            return CompletableFuture.completedFuture(new HandlerResponse<>(0x58, negative));
        }
    }

    public void advance(long now) {
        decisions.advance(now);
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
                streams.values().stream()
                        .mapToInt(stream -> stream.content.incompleteAssemblies())
                        .sum());
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
        private long received;

        Stream(ContentPlan content) {
            this.content = Objects.requireNonNull(content, "content");
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
            int incompleteAssemblies) {}
}
