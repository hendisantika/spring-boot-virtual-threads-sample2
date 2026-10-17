package id.s.hendisantika.vt.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by IntelliJ IDEA.
 * Project : spring-boot-thread-pool-vs-virtual-thread
 * User: hendisantika
 * Email: hendisantika@gmail.com
 * Telegram : @hendisantika34
 * Date: 17/10/26
 * Time: 14.41
 */
@SpringBootTest(properties = "demo.db.latency=50ms")
@AutoConfigureMockMvc
class ThreadDemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void findUserReturnsUser() throws Exception {
        mockMvc.perform(get("/api/users/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.name").value("user-7"));
    }

    @Test
    void compareReturnsBothStrategies() throws Exception {
        mockMvc.perform(get("/api/compare").param("requests", "40").param("poolSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threadPool.strategy").value("thread-pool"))
                .andExpect(jsonPath("$.virtualThreads.strategy").value("virtual-threads"))
                .andExpect(jsonPath("$.virtualThreads.peakConcurrent").value(40));
    }

    @Test
    void compareRejectsTooManyRequests() throws Exception {
        mockMvc.perform(get("/api/compare").param("requests", "999999"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void statsShowsVirtualThreadsEnabled() throws Exception {
        mockMvc.perform(get("/api/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.virtualThreadsEnabled").value(true));
    }
}
