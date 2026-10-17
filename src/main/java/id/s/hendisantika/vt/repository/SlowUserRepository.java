package id.s.hendisantika.vt.repository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Pretends to be a database: every query blocks the calling thread for {@code demo.db.latency}.
 * <p>
 * On a platform thread the OS thread sits idle during the wait. On a virtual thread the
 * {@link Thread#sleep} unmounts it from its carrier, so the carrier can run other work and the
 * virtual thread remounts when the "data comes back".
 */
@Repository
public class SlowUserRepository {

    private final Duration latency;

    // Tip 3 from the diagram: guard shared state with ReentrantLock, and never block while holding it.
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<Long, String> names = new HashMap<>();

    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger peakInFlight = new AtomicInteger();

    public SlowUserRepository(@Value("${demo.db.latency:1s}") Duration latency) {
        this.latency = latency;
    }

    public User findById(long id) {
        int current = inFlight.incrementAndGet();
        peakInFlight.accumulateAndGet(current, Math::max);
        try {
            Thread.sleep(latency); // the slow DB call, done outside the lock
            Thread thread = Thread.currentThread();
            return new User(id, nameFor(id), thread.toString(), thread.isVirtual());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Query interrupted", e);
        } finally {
            inFlight.decrementAndGet();
        }
    }

    private String nameFor(long id) {
        lock.lock();
        try {
            return names.computeIfAbsent(id, key -> "user-" + key);
        } finally {
            lock.unlock();
        }
    }

    public int inFlight() {
        return inFlight.get();
    }

    public int peakInFlight() {
        return peakInFlight.get();
    }

    public void resetPeak() {
        peakInFlight.set(inFlight.get());
    }

    public Duration latency() {
        return latency;
    }
}
