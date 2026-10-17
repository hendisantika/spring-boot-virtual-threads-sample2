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
/**
 * Where one run stands at a given moment: how many requests are still queued, how many are
 * waiting on the DB right now, and how many already got their answer.
 */
public record ProgressSnapshot(
        String strategy,
        int requests,
        int waiting,
        int running,
        int done,
        long elapsedMillis,
        boolean finished) {
}
