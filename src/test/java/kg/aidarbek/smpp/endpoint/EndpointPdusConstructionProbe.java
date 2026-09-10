package kg.aidarbek.smpp.endpoint;

import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import kg.aidarbek.smpp.codec.PduLimits;
import kg.aidarbek.smpp.session.EndpointRole;

/** Finite manually invoked allocation diagnostic for constructing endpoint wire collaborators. */
public final class EndpointPdusConstructionProbe {
    private static volatile EndpointPdus retained;

    private EndpointPdusConstructionProbe() {}

    /** Measures alternating roles with the same limits; arguments are warmup and measured construction counts. */
    public static void main(String[] arguments) {
        if (arguments.length != 2) throw new IllegalArgumentException("warmup measured");
        int warmup = count(arguments[0]);
        int measured = count(arguments[1]);
        PduLimits limits = new PduLimits(65536, 4096, 128);
        ThreadMXBean allocation = ManagementFactory.getPlatformMXBean(ThreadMXBean.class);
        if (allocation == null || !allocation.isThreadAllocatedMemorySupported())
            throw new IllegalStateException("Calling-thread allocation counters unavailable");
        allocation.setThreadAllocatedMemoryEnabled(true);
        for (int index = 0; index < warmup; index++) construct(index, limits);
        long caller = Thread.currentThread().threadId();
        long before = allocation.getThreadAllocatedBytes(caller);
        long started = System.nanoTime();
        for (int index = 0; index < measured; index++) construct(index, limits);
        long elapsed = System.nanoTime() - started;
        long bytes = allocation.getThreadAllocatedBytes(caller) - before;
        if (bytes < 0 || retained == null) throw new IllegalStateException("Invalid construction measurement");
        retained = null;
        System.out.println("{\"warmup\":" + warmup + ",\"measured\":" + measured
                + ",\"callingThreadAllocatedBytes\":" + bytes + ",\"elapsedNanos\":" + elapsed
                + ",\"roles\":\"alternating ESME and MESSAGE_CENTER\",\"maximumPduLength\":65536,"
                + "\"maximumTlvLength\":4096,\"maximumTlvCount\":128}");
    }

    private static void construct(int index, PduLimits limits) {
        retained = new EndpointPdus(index % 2 == 0 ? EndpointRole.ESME : EndpointRole.MESSAGE_CENTER, limits);
    }

    private static int count(String value) {
        int count = Integer.parseInt(value);
        if (count < 1 || count > 5000) throw new IllegalArgumentException("Construction count must be 1..5000");
        return count;
    }
}
