package id.s.hendisantika.vt.service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by IntelliJ IDEA.
 * Project : spring-boot-thread-pool-vs-virtual-thread
 * User: hendisantika
 * Email: hendisantika@gmail.com
 * Telegram : @hendisantika34
 * Date: 17/10/26
 * Time: 14.52
 */
/**
 * Live counters for one run, safe to read from another thread while the run is in progress.
 */
public class RunProgress {

    private final String strategy;
    private final int requests;

    private final AtomicInteger started = new AtomicInteger();
    private final AtomicInteger running = new AtomicInteger();
    private final AtomicInteger done = new AtomicInteger();
    private final AtomicInteger peakRunning = new AtomicInteger();

    private volatile long startNanos;
    private volatile long endNanos;

    public RunProgress(String strategy, int requests) {
        this.strategy = strategy;
        this.requests = requests;
    }

    void start() {
        startNanos = System.nanoTime();
    }

    void finish() {
        endNanos = System.nanoTime();
    }

    void taskStarted() {
        started.incrementAndGet();
        peakRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
    }

    void taskFinished() {
        running.decrementAndGet();
        done.incrementAndGet();
    }

    int peakRunning() {
        return peakRunning.get();
    }

    public ProgressSnapshot snapshot() {
        long end = endNanos;
        boolean finished = end != 0;
        long elapsedNanos = startNanos == 0 ? 0 : (finished ? end : System.nanoTime()) - startNanos;
        int doneNow = done.get();
        int runningNow = running.get();
        int waiting = Math.max(0, requests - doneNow - runningNow);
        return new ProgressSnapshot(strategy, requests, waiting, runningNow, doneNow,
                elapsedNanos / 1_000_000, finished);
    }
}
