package id.s.hendisantika.vt.service;

/**
 * Created by IntelliJ IDEA.
 * Project : spring-boot-thread-pool-vs-virtual-thread
 * User: hendisantika
 * Email: hendisantika@gmail.com
 * Telegram : @hendisantika34
 * Date: 17/10/26
 * Time: 14.52
 */
public record LiveSnapshot(ProgressSnapshot threadPool, ProgressSnapshot virtualThreads) {
}
