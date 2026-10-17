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
public record ComparisonResult(long dbLatencyMillis, RunResult threadPool, RunResult virtualThreads, double speedup) {
}
