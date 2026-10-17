package id.s.hendisantika.vt.service;

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
 * Outcome of firing {@code requests} simulated DB calls at one executor.
 *
 * @param strategy          "thread-pool" or "virtual-threads"
 * @param requests          how many users arrived at once
 * @param threads           OS threads in the pool, or -1 for "one virtual thread per task"
 * @param totalMillis       until the last user got an answer
 * @param maxQueueWaitMillis longest time a request waited before a thread picked it up
 * @param peakConcurrent    most DB calls in flight at the same moment
 * @param queuedAtStart     requests that could not start immediately
 */
public record RunResult(
        String strategy,
        int requests,
        int threads,
        long totalMillis,
        long maxQueueWaitMillis,
        int peakConcurrent,
        int queuedAtStart) {
}
