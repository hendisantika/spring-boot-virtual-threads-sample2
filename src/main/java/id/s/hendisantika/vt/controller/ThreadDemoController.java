package id.s.hendisantika.vt.controller;

import id.s.hendisantika.vt.repository.SlowUserRepository;
import id.s.hendisantika.vt.repository.User;
import id.s.hendisantika.vt.service.ComparisonResult;
import id.s.hendisantika.vt.service.ThreadComparisonService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * Project : spring-boot-thread-pool-vs-virtual-thread
 * User: hendisantika
 * Email: hendisantika@gmail.com
 * Telegram : @hendisantika34
 * Date: 17/10/26
 * Time: 14.41
 */
@RestController
@RequestMapping("/api")
public class ThreadDemoController {

    private static final int MAX_REQUESTS = 10_000;
    private static final int MAX_POOL_SIZE = 1_000;
    private static final Duration LIVE_TIMEOUT = Duration.ofMinutes(2);

    private final SlowUserRepository repository;
    private final ThreadComparisonService comparisonService;
    private final boolean virtualThreadsEnabled;

    public ThreadDemoController(SlowUserRepository repository,
                                ThreadComparisonService comparisonService,
                                @Value("${spring.threads.virtual.enabled:false}") boolean virtualThreadsEnabled) {
        this.repository = repository;
        this.comparisonService = comparisonService;
        this.virtualThreadsEnabled = virtualThreadsEnabled;
    }

    /** One request = one ~1 s DB call. Hit it with a load tool to see the Tomcat-level difference. */
    @GetMapping("/users/{id}")
    public User findUser(@PathVariable long id) {
        return repository.findById(id);
    }

    /** Runs the diagram's scenario in-process: 1,000 users, 200 pooled OS threads vs virtual threads. */
    @GetMapping("/compare")
    public ComparisonResult compare(@RequestParam(defaultValue = "1000") int requests,
                                    @RequestParam(defaultValue = "200") int poolSize) {
        validate(requests, poolSize);
        return comparisonService.compare(requests, poolSize);
    }

    /**
     * Same scenario, but both strategies run side by side and progress is streamed as Server-Sent Events:
     * {@code progress} events every 100 ms, then one {@code result} event. Used by the web UI.
     */
    @GetMapping(path = "/compare/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter compareLive(@RequestParam(defaultValue = "1000") int requests,
                                  @RequestParam(defaultValue = "200") int poolSize) {
        validate(requests, poolSize);
        SseEmitter emitter = new SseEmitter(LIVE_TIMEOUT.toMillis());
        Thread.startVirtualThread(() -> {
            try {
                ComparisonResult result = comparisonService.compareLive(requests, poolSize,
                        snapshot -> send(emitter, "progress", snapshot));
                send(emitter, "result", result);
                emitter.complete();
            } catch (RuntimeException e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private static void validate(int requests, int poolSize) {
        if (requests < 1 || requests > MAX_REQUESTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requests must be between 1 and " + MAX_REQUESTS);
        }
        if (poolSize < 1 || poolSize > MAX_POOL_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "poolSize must be between 1 and " + MAX_POOL_SIZE);
        }
    }

    private static void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new UncheckedIOException("Client disconnected", e);
        }
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Thread thread = Thread.currentThread();
        return Map.of(
                "virtualThreadsEnabled", virtualThreadsEnabled,
                "servedBy", thread.toString(),
                "isVirtual", thread.isVirtual(),
                "dbLatencyMillis", repository.latency().toMillis(),
                "dbCallsInFlight", repository.inFlight(),
                "peakDbCallsInFlight", repository.peakInFlight());
    }
}
