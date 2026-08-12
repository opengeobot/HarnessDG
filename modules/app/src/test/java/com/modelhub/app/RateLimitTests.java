package com.modelhub.app;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 限流（02 §6.2）：用户+IP 双维度固定窗口，超限 429 RATE_LIMITED 且带 Retry-After。
 * window=5s/max=5。用并发突发（6 个请求同一瞬间发出）保证全部落入同一窗口，
 * Lua INCR 原子计数 → 恰好一个请求被拒，避免串行请求因单请求延迟跨越窗口边界的 flaky。
 */
class RateLimitTests extends BaseIntegrationTest {

    @Test
    void login_returns_429_with_retry_after_after_window_limit() throws Exception {
        String ghost = unique("ghost");
        String ip = "10.200.200.1";

        // 突发若恰跨窗口边界（计数被分割）则等下一窗口重试，最多 3 次
        List<ResponseEntity<String>> responses = burstWithRetry(6, () -> loginAttempt(ghost, ip));

        List<ResponseEntity<String>> denied = responses.stream()
                .filter(r -> r.getStatusCode().value() == 429).toList();
        assertThat(denied).hasSize(1);
        assertThat(errorCode(denied.get(0))).isEqualTo("RATE_LIMITED");
        assertThat(denied.get(0).getHeaders().getFirst("Retry-After")).isNotBlank();
        // 其余请求正常走完认证流程（幽灵用户 → 401）
        assertThat(responses.stream().filter(r -> r.getStatusCode().value() == 401).count()).isEqualTo(5);
    }

    @Test
    void register_is_rate_limited_per_ip() throws Exception {
        String ip = "10.200.200.2";

        List<ResponseEntity<String>> responses = burstWithRetry(6,
                () -> registerAttempt(unique("rl"), ip));

        List<ResponseEntity<String>> denied = responses.stream()
                .filter(r -> r.getStatusCode().value() == 429).toList();
        assertThat(denied).hasSize(1);
        assertThat(errorCode(denied.get(0))).isEqualTo("RATE_LIMITED");
        assertThat(responses.stream().filter(r -> r.getStatusCode().value() == 201).count()).isEqualTo(5);
    }

    /** 并发突发发送 n 个请求；若未触发 429（突发跨越窗口边界）则等待下一窗口重试。 */
    private List<ResponseEntity<String>> burstWithRetry(int n,
            java.util.function.Supplier<ResponseEntity<String>> call) throws Exception {
        List<ResponseEntity<String>> responses = List.of();
        for (int attempt = 0; attempt < 3; attempt++) {
            waitNextRateLimitWindow();
            responses = burst(n, call);
            if (responses.stream().anyMatch(r -> r.getStatusCode().value() == 429)) {
                return responses;
            }
        }
        return responses;
    }

    /** 并发突发发送 n 个请求，等待全部完成并收集响应。 */
    private List<ResponseEntity<String>> burst(int n, java.util.function.Supplier<ResponseEntity<String>> call)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        try {
            List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(call::get));
            }
            List<ResponseEntity<String>> out = new ArrayList<>();
            for (Future<ResponseEntity<String>> f : futures) {
                out.add(f.get());
            }
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    private ResponseEntity<String> loginAttempt(String username, String ip) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Test-Client-Ip", ip);
        return rest.postForEntity("/api/v1/auth/login",
                new HttpEntity<>(Map.of("username", username, "password", "Wrong-Password-1"), headers),
                String.class);
    }

    private ResponseEntity<String> registerAttempt(String username, String ip) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Test-Client-Ip", ip);
        return rest.postForEntity("/api/v1/auth/register",
                new HttpEntity<>(Map.of("username", username, "password", "Passw0rd-x"), headers),
                String.class);
    }
}
