package kg.aidarbek.simulator;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiFunction;
import java.util.function.LongFunction;

/** Single-owner routing and local concurrency quotas, without protocol admission or retry ownership. */
final class SessionTrafficSource implements LongFunction<PendingCall> {
    private final LoadPlan.Model model;
    private final int connections;
    private final int window;
    private final int[] pending;
    private final BiFunction<Integer, Long, PendingCall> sender;
    private int nextSlot;

    SessionTrafficSource(
            LoadPlan.Model model, int connections, int window, BiFunction<Integer, Long, PendingCall> sender) {
        this.model = Objects.requireNonNull(model, "model");
        if (connections < 1 || connections > 4096 || window < 1 || (long) connections * window > 65536)
            throw new IllegalArgumentException("Connections/window exceed the finite simulator work limit");
        this.connections = connections;
        this.window = window;
        pending = new int[connections];
        this.sender = Objects.requireNonNull(sender, "sender");
    }

    @Override
    public PendingCall apply(long index) {
        if (model == LoadPlan.Model.ARRIVAL_RATE)
            return sender.apply((int) Math.floorMod(index, (long) connections), index);
        for (int offset = 0; offset < connections; offset++) {
            int slot = (nextSlot + offset) % connections;
            if (pending[slot] == window) continue;
            nextSlot = (slot + 1) % connections;
            pending[slot]++;
            try {
                return new ObservedCall(slot, Objects.requireNonNull(sender.apply(slot, index), "call"));
            } catch (RuntimeException | Error failure) {
                pending[slot]--;
                throw failure;
            }
        }
        throw new RejectedExecutionException("All local session concurrency slots remain occupied");
    }

    /** Retains a local slot through cancellation until the owner's poll observes an actual terminal outcome. */
    private final class ObservedCall implements PendingCall {
        private final int slot;
        private final PendingCall call;
        private boolean released;

        ObservedCall(int slot, PendingCall call) {
            this.slot = slot;
            this.call = call;
        }

        @Override
        public Optional<Completion> poll() {
            Optional<Completion> result = call.poll();
            if (result.isPresent() && !released) {
                released = true;
                pending[slot]--;
            }
            return result;
        }

        @Override
        public boolean mayHaveBeenSent() {
            return call.mayHaveBeenSent();
        }

        @Override
        public void cancel() {
            call.cancel();
        }
    }
}
