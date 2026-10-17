package id.s.hendisantika.vt.service;

import id.s.hendisantika.vt.repository.SlowUserRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

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

    public static final String THREAD_POOL = "thread-pool";
    public static final String VIRTUAL_THREADS = "virtual-threads";

    private static final Duration SNAPSHOT_INTERVAL = Duration.ofMillis(100);

    private final SlowUserRepository repository;

    public ThreadComparisonService(SlowUserRepository repository) {
        this.repository = repository;
    }

    public ComparisonResult compare(int requests, int poolSize) {
        RunResult pool = runWithThreadPool(requests, poolSize);
        RunResult virtual = runWithVirtualThreads(requests);
        return toComparison(pool, virtual);
    }

    /**
     * Runs both strategies side by side, like the two halves of the diagram, and reports progress to
     * {@code listener} every {@link #SNAPSHOT_INTERVAL} until both are finished.
     */
    public ComparisonResult compareLive(int requests, int poolSize, Consumer<LiveSnapshot> listener) {
        RunProgress poolProgress = new RunProgress(THREAD_POOL, requests);
        RunProgress virtualProgress = new RunProgress(VIRTUAL_THREADS, requests);

        try (ExecutorService runner = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<RunResult> poolRun = runner.submit(() -> runWithThreadPool(requests, poolSize, poolProgress));
            Future<RunResult> virtualRun = runner.submit(() -> runWithVirtualThreads(requests, virtualProgress));

            while (!(poolRun.isDone() && virtualRun.isDone())) {
                listener.accept(new LiveSnapshot(poolProgress.snapshot(), virtualProgress.snapshot()));
                sleep(SNAPSHOT_INTERVAL);
            }
            listener.accept(new LiveSnapshot(poolProgress.snapshot(), virtualProgress.snapshot()));
            return toComparison(await(poolRun), await(virtualRun));
        }
    }

    /** Thread pool (old way): {@code poolSize} OS threads, everyone else waits in the queue. */
    public RunResult runWithThreadPool(int requests, int poolSize) {
        return runWithThreadPool(requests, poolSize, new RunProgress(THREAD_POOL, requests));
    }

    public RunResult runWithThreadPool(int requests, int poolSize, RunProgress progress) {
        try (ExecutorService executor = Executors.newFixedThreadPool(poolSize)) {
            return run(THREAD_POOL, requests, poolSize, executor, progress);
        }
    }

    /** Virtual threads (Java 21+): don't pool them, create one per task. */
    public RunResult runWithVirtualThreads(int requests) {
        return runWithVirtualThreads(requests, new RunProgress(VIRTUAL_THREADS, requests));
    }

    public RunResult runWithVirtualThreads(int requests, RunProgress progress) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            return run(VIRTUAL_THREADS, requests, -1, executor, progress);
        }
    }

    private ComparisonResult toComparison(RunResult pool, RunResult virtual) {
        double speedup = virtual.totalMillis() == 0 ? 0 : (double) pool.totalMillis() / virtual.totalMillis();
        return new ComparisonResult(repository.latency().toMillis(), pool, virtual, Math.round(speedup * 10) / 10.0);
    }

    private RunResult run(String strategy, int requests, int threads, ExecutorService executor, RunProgress progress) {
        AtomicLong maxQueueWait = new AtomicLong();
        AtomicInteger queued = new AtomicInteger();
        long thresholdNanos = repository.latency().toNanos() / 2;

        progress.start();
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
                progress.taskStarted();
                try {
                    repository.findById(id);
                } finally {
                    progress.taskFinished();
                }
            }));
        }
        for (Future<?> future : futures) {
            await(future);
        }
        long totalMillis = (System.nanoTime() - start) / 1_000_000;
        progress.finish();

        return new RunResult(strategy, requests, threads, totalMillis,
                maxQueueWait.get() / 1_000_000, progress.peakRunning(), queued.get());
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for tasks", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Task failed", e.getCause());
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reporting progress", e);
        }
    }
}
