package kg.aidarbek.simulator;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Finite single-carrier probe using the real sampler guards and a coordinated blocking observation. */
public final class SamplingProgressProbe {
    private SamplingProgressProbe() {}

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) throw new IllegalArgumentException("scenario and fresh report directory required");
        String scenario = arguments[0];
        if (!scenario.equals("resource")
                && !scenario.equals("pressure")
                && !scenario.equals("resource-baseline")
                && !scenario.equals("pressure-baseline"))
            throw new IllegalArgumentException("Unknown sampling scenario");
        Thread warmup = Thread.ofVirtual().start(() -> {});
        require(warmup.join(Duration.ofSeconds(2)), "The virtual scheduler did not start");
        if (scenario.endsWith("-baseline")) {
            baseline(scenario, Path.of(arguments[1]));
            return;
        }
        Gate gate = new Gate();
        try (ReportWriter writer = new ReportWriter(Path.of(arguments[1]))) {
            Runnable sample;
            if (scenario.equals("resource")) {
                RunEnvironment.Snapshot observation = RunEnvironment.sample();
                ResourceSampler sampler =
                        new ResourceSampler(writer, () -> gate.observe(observation), System::nanoTime);
                sample = sampler::sample;
            } else {
                PressureSampler.Observation observation = new PressureSampler.Observation(0, 0, 0, 0, 0, 0, 0, 0);
                PressureSampler sampler =
                        new PressureSampler(writer, () -> gate.observe(observation), System::nanoTime);
                sample = sampler::sample;
            }
            int before = RunEnvironment.sample().platformThreads();
            SamplingLoop loop = new SamplingLoop(sample, Duration.ofMillis(10));
            Thread releaser = null;
            try {
                loop.start();
                require(gate.entered.await(2, TimeUnit.SECONDS), "The second observation did not block");
                require(!loop.stop(Duration.ofMillis(30)), "A blocked physical observation was reported as retired");
                require(gate.calls.get() == 2, "Samples accumulated behind the blocked observation");
                CompletableFuture<Void> released = new CompletableFuture<>();
                releaser = Thread.ofVirtual().name("sampling-probe-releaser").start(() -> {
                    gate.release.countDown();
                    released.complete(null);
                });
                released.get(2, TimeUnit.SECONDS);
                require(loop.stop(Duration.ofSeconds(2)), "The sampling worker did not retire after release");
                require(loop.failure() == null, "The sampling worker failed");
                require(gate.calls.get() == 2, "A queued observation ran after stop");
                require(loop.skippedSamples() >= 1, "Elapsed observation slots were not counted as skipped");
                Thread observed = gate.worker.get();
                require(!observed.isVirtual(), "Blocking observations must use one dedicated platform worker");
                require(!observed.isAlive(), "The observation worker remains alive after bounded stop");
                require(
                        gate.platformWorkerVisible,
                        "The observation worker was absent from platform-thread accounting");
                require(
                        ManagementFactory.getThreadMXBean().getThreadInfo(observed.threadId()) == null,
                        "The retired observation worker remains in platform-thread accounting");
                System.out.println(scenario
                        + " virtual releaser progressed; one platform sampler retired; platformThreads=" + before + "/"
                        + gate.platformThreads + "/" + RunEnvironment.sample().platformThreads());
            } finally {
                gate.release.countDown();
                require(loop.stop(Duration.ofSeconds(2)), "Probe cleanup could not retire its sampling worker");
                if (releaser != null) require(releaser.join(Duration.ofSeconds(2)), "Probe releaser was not reaped");
            }
        }
    }

    private static void baseline(String scenario, Path directory) throws Exception {
        Gate gate = new Gate();
        try (ReportWriter writer = new ReportWriter(directory)) {
            Runnable baseline;
            if (scenario.equals("resource-baseline")) {
                RunEnvironment.Snapshot observation = RunEnvironment.sample();
                ResourceSampler sampler =
                        new ResourceSampler(writer, () -> gate.observe(observation), System::nanoTime);
                baseline = sampler::baseline;
            } else {
                PressureSampler.Observation observation = new PressureSampler.Observation(0, 0, 0, 0, 0, 0, 0, 0);
                PressureSampler sampler =
                        new PressureSampler(writer, () -> gate.observe(observation), System::nanoTime);
                baseline = sampler::baseline;
            }
            CompletableFuture<Void> completed = new CompletableFuture<>();
            Thread owner = SimulatorTestOwner.start(() -> {
                try {
                    baseline.run();
                    completed.complete(null);
                } catch (RuntimeException failure) {
                    completed.completeExceptionally(failure);
                }
            });
            Thread releaser = null;
            try {
                require(gate.entered.await(2, TimeUnit.SECONDS), "The synchronous baseline did not block");
                CompletableFuture<Void> released = new CompletableFuture<>();
                releaser = Thread.ofVirtual().name("baseline-probe-releaser").start(() -> {
                    gate.release.countDown();
                    released.complete(null);
                });
                try {
                    released.get(2, TimeUnit.SECONDS);
                } catch (java.util.concurrent.TimeoutException failure) {
                    throw new AssertionError(
                            "The virtual releaser could not progress through a synchronous baseline", failure);
                }
                completed.get(2, TimeUnit.SECONDS);
                require(owner.join(Duration.ofSeconds(2)), "The synchronous baseline owner did not retire");
                require(!owner.isVirtual(), "The complete simulator test must use the launcher platform owner kind");
                require(gate.calls.get() == 2, "The synchronous baseline performed unexpected observations");
                require(gate.platformWorkerVisible, "The baseline owner was absent from platform-thread accounting");
                require(
                        ManagementFactory.getThreadMXBean().getThreadInfo(owner.threadId()) == null,
                        "The retired baseline owner remains in platform-thread accounting");
                System.out.println(scenario + " virtual releaser progressed; one platform baseline owner retired");
            } finally {
                gate.release.countDown();
                require(owner.join(Duration.ofSeconds(2)), "Probe cleanup could not retire its baseline owner");
                if (releaser != null) require(releaser.join(Duration.ofSeconds(2)), "Probe releaser was not reaped");
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** Models a blocked file/endpoint observation while retaining the real sampler synchronization. */
    private static final class Gate {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger calls = new AtomicInteger();
        final AtomicReference<Thread> worker = new AtomicReference<>();
        boolean platformWorkerVisible;
        int platformThreads;

        <T> T observe(T value) {
            if (calls.incrementAndGet() == 2) {
                Thread current = Thread.currentThread();
                worker.set(current);
                platformWorkerVisible = ManagementFactory.getThreadMXBean().getThreadInfo(current.threadId()) != null;
                platformThreads = RunEnvironment.sample().platformThreads();
                entered.countDown();
                try {
                    release.await();
                } catch (InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Probe observation interrupted", failure);
                }
            }
            return value;
        }
    }
}
