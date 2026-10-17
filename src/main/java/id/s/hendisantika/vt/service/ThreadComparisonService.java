package id.s.hendisantika.vt.service;

import id.s.hendisantika.vt.repository.SlowUserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by IntelliJ IDEA.
 * Project : spring-boot-thread-pool-vs-virtual-thread
 * User: hendisantika
 * Email: hendisantika@gmail.com
 * Telegram : @hendisantika34
 * Date: 17/10/26
 * Time: 14.41
 */
/**
 * Recreates the "Thread pool vs Virtual threads" picture inside one JVM: N users hit an endpoint
 * whose DB call takes ~1 s, served either by a fixed pool of OS threads or by one virtual thread
 * per task.
 */
@Service
public class ThreadComparisonService {

    private final SlowUserRepository repository;

    public ThreadComparisonService(SlowUserRepository repository) {
        this.repository = repository;
    }

    public ComparisonResult compare(int requests, int poolSize) {
        RunResult pool = runWithThreadPool(requests, poolSize);
        RunResult virtual = runWithVirtualThreads(requests);
        double speedup = virtual.totalMillis() == 0 ? 0 : (double) pool.totalMillis() / virtual.totalMillis();
        return new ComparisonResult(repository.latency().toMillis(), pool, virtual, Math.round(speedup * 10) / 10.0);
    }

    /** Thread pool (old way): {@code poolSize} OS threads, everyone else waits in the queue. */
    public RunResult runWithThreadPool(int requests, int poolSize) {
        try (ExecutorService executor = Executors.newFixedThreadPool(poolSize)) {
            return run("thread-pool", requests, poolSize, executor);
        }
    }

    /** Virtual threads (Java 21+): don't pool them, create one per task. */
    public RunResult runWithVirtualThreads(int requests) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            return run("virtual-threads", requests, -1, executor);
        }
    }

    private RunResult run(String strategy, int requests, int threads, ExecutorService executor) {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        AtomicLong maxQueueWait = new AtomicLong();
        AtomicInteger queued = new AtomicInteger();
        long thresholdNanos = repository.latency().toNanos() / 2;

        long start = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>(requests);
        for (int i = 0; i < requests; i++) {
            long id = i;
            long submittedAt = System.nanoTime();
            futures.add(executor.submit(() -> {
                long waited = System.nanoTime() - submittedAt;
                maxQueueWait.accumulateAndGet(waited, Math::max);
                if (waited > thresholdNanos) {
                    queued.incrementAndGet();
                }
                peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                try {
                    repository.findById(id);
                } finally {
                    inFlight.decrementAndGet();
                }
            }));
        }
        for (Future<?> future : futures) {
            await(future);
        }
        long totalMillis = (System.nanoTime() - start) / 1_000_000;

        return new RunResult(strategy, requests, threads, totalMillis,
                maxQueueWait.get() / 1_000_000, peak.get(), queued.get());
    }

    private static void await(Future<?> future) {
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for tasks", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Task failed", e.getCause());
        }
    }
}
