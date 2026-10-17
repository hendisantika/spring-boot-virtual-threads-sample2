package id.s.hendisantika.vt.repository;

/**
 * Created by IntelliJ IDEA.
 * Project : spring-boot-thread-pool-vs-virtual-thread
 * User: hendisantika
 * Email: hendisantika@gmail.com
 * Telegram : @hendisantika34
 * Date: 17/10/26
 * Time: 14.40
 */
public record User(long id, String name, String servedBy, boolean virtualThread) {
}
