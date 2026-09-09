package kg.aidarbek.smpp.endpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/** Selects a finite contention order without changing callback, transport or capacity behavior. */
final class CarrierContention {
    private CarrierContention() {}

    static ReentrantLock lock(Object owner) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField("lock");
        field.setAccessible(true);
        return (ReentrantLock) field.get(owner);
    }

    static void run(ReentrantLock lock, List<Runnable> work) throws Exception {
        run(java.util.Collections.nCopies(work.size(), lock), work);
    }

    static void run(List<ReentrantLock> locks, List<Runnable> work) throws Exception {
        if (locks.size() != 2 || work.size() != 2)
            throw new IllegalArgumentException("Two controlled callers required");
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> holder = new CompletableFuture<>();
        Thread.ofVirtual().name("probe-contended-lock-holder").start(() -> {
            for (ReentrantLock lock : locks) lock.lock();
            try {
                holding.countDown();
                release.await();
                holder.complete(null);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                holder.completeExceptionally(failure);
            } finally {
                for (ReentrantLock lock : locks.reversed()) lock.unlock();
            }
        });
        require(holding.await(2, TimeUnit.SECONDS), "contention locks were not acquired");
        List<CompletableFuture<Void>> completions = new ArrayList<>();
        List<Thread> callers = new ArrayList<>();
        for (int index = 0; index < work.size(); index++) {
            Runnable action = work.get(index);
            CompletableFuture<Void> finished = new CompletableFuture<>();
            completions.add(finished);
            callers.add(Thread.ofVirtual().name("probe-caller-" + index).start(() -> {
                try {
                    action.run();
                    finished.complete(null);
                } catch (Throwable failure) {
                    finished.completeExceptionally(failure);
                }
            }));
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!queued(locks, callers) && System.nanoTime() - deadline < 0) LockSupport.parkNanos(100_000);
        require(queued(locks, callers), "both callers did not reach their controlled contention");
        release.countDown();
        completions.add(holder);
        CompletableFuture.allOf(completions.toArray(CompletableFuture<?>[]::new))
                .get(3, TimeUnit.SECONDS);
    }

    private static boolean queued(List<ReentrantLock> locks, List<Thread> callers) {
        for (int index = 0; index < locks.size(); index++) {
            if (!locks.get(index).hasQueuedThread(callers.get(index))) return false;
        }
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
