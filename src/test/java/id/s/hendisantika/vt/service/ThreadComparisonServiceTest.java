package id.s.hendisantika.vt.service;

import id.s.hendisantika.vt.repository.SlowUserRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Created by IntelliJ IDEA.
 * Project : spring-boot-thread-pool-vs-virtual-thread
 * User: hendisantika
 * Email: hendisantika@gmail.com
 * Telegram : @hendisantika34
 * Date: 17/10/26
 * Time: 14.41
 */
class ThreadComparisonServiceTest {

    // Scaled-down version of the diagram: 100 users, 20 pooled threads, 200 ms DB wait
    private static final int REQUESTS = 100;
    private static final int POOL_SIZE = 20;
    private static final long LATENCY_MILLIS = 200;

    private final ThreadComparisonService service =
            new ThreadComparisonService(new SlowUserRepository(Duration.ofMillis(LATENCY_MILLIS)));

    @Test
    void threadPoolServesRequestsInWaves() {
        RunResult result = service.runWithThreadPool(REQUESTS, POOL_SIZE);

        long waves = REQUESTS / POOL_SIZE; // 5 waves, like "last user waits ~5 s"
        assertThat(result.totalMillis()).isGreaterThanOrEqualTo(waves * LATENCY_MILLIS);
        assertThat(result.peakConcurrent()).isEqualTo(POOL_SIZE);
        assertThat(result.queuedAtStart()).isEqualTo(REQUESTS - POOL_SIZE);
    }

    @Test
    void virtualThreadsServeAllRequestsAtOnce() {
        RunResult result = service.runWithVirtualThreads(REQUESTS);

        assertThat(result.totalMillis()).isLessThan(2 * LATENCY_MILLIS); // "all done in ~1 s"
        assertThat(result.peakConcurrent()).isEqualTo(REQUESTS);
        assertThat(result.queuedAtStart()).isZero();
    }

    @Test
    void compareReportsSpeedup() {
        ComparisonResult result = service.compare(REQUESTS, POOL_SIZE);

        assertThat(result.dbLatencyMillis()).isEqualTo(LATENCY_MILLIS);
        assertThat(result.speedup()).isGreaterThan(2.0);
    }

    @Test
    void compareLiveStreamsProgressUntilBothRunsFinish() {
        List<LiveSnapshot> snapshots = new CopyOnWriteArrayList<>();

        ComparisonResult result = service.compareLive(REQUESTS, POOL_SIZE, snapshots::add);

        assertThat(snapshots).hasSizeGreaterThan(2);
        LiveSnapshot last = snapshots.getLast();
        assertThat(last.threadPool().finished()).isTrue();
        assertThat(last.threadPool().done()).isEqualTo(REQUESTS);
        assertThat(last.virtualThreads().finished()).isTrue();
        assertThat(last.virtualThreads().done()).isEqualTo(REQUESTS);
        assertThat(snapshots).anySatisfy(snapshot ->
                assertThat(snapshot.threadPool().waiting()).isGreaterThan(0));
        assertThat(result.virtualThreads().peakConcurrent()).isEqualTo(REQUESTS);
        assertThat(result.threadPool().peakConcurrent()).isEqualTo(POOL_SIZE);
    }
}
