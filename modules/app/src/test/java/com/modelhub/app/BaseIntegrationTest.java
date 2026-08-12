package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * M1 集成测试基座：真实 PostgreSQL 16 + Redis 7（TST-04 禁止内存库作为集成证据）。
 * bootstrap 在首个上下文启用，创建 platform-root 管理员；限流阈值取默认 5/60s。
 */
@SpringBootTest(classes = ModelHubApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class BaseIntegrationTest {

    protected static final String ORIGIN = "http://localhost:5173";
    protected static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 100_000);

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("modelhub").withUsername("modelhub").withPassword("modelhub")
            .withReuse(true);

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379).withReuse(true);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> POSTGRES.getJdbcUrl() + "&stringtype=unspecified");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // 审计/锁定走 REQUIRES_NEW 嵌套事务，并发请求需同时持有多个连接；默认 10 会饿死串行化
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 30);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("modelhub.identity.allowed-origins", () -> ORIGIN);
        registry.add("modelhub.identity.client-ip-header", () -> "X-Test-Client-Ip");
        registry.add("modelhub.identity.rate-limit.max-attempts", () -> 5);
        registry.add("modelhub.identity.rate-limit.window-seconds", () -> 5);
        registry.add("modelhub.bootstrap.enabled", () -> true);
        registry.add("modelhub.bootstrap.username", () -> "platform-root");
        registry.add("modelhub.bootstrap.password", () -> "Boot-Strap-1x");
    }

    protected static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    protected TestRestTemplate rest;

    /**
     * httpclient5 默认会按 Retry-After 自动重试 429/503，会把限流响应“消化”成新窗口的成功响应，
     * 导致断言看不到 429；测试客户端必须禁用自动重试，由用例自行决定重试策略。
     */
    @BeforeEach
    void disableHttpClientAutoRetry() {
        rest.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory(
                HttpClients.custom().disableAutomaticRetries().build()));
    }

    /** 一次认证结果：access/refresh/csrf + 用户名与 publicId。 */
    protected record Session(String accessToken, String refreshToken, String csrfToken,
                             String username, String userPublicId) {}

    protected static String unique(String prefix) {
        return prefix + SEQ.incrementAndGet();
    }

    protected Session register(String username, String password) {
        ResponseEntity<String> resp = rest.postForEntity("/api/v1/auth/register",
                json(Map.of("username", username, "password", password)), String.class);
        if (resp.getStatusCode().value() != 201) {
            throw new IllegalStateException("register failed: " + resp.getStatusCode() + " " + resp.getBody());
        }
        return parseSession(resp, ipHeaders());
    }

    protected Session login(String username, String password) {
        ResponseEntity<String> resp = rest.postForEntity("/api/v1/auth/login",
                json(Map.of("username", username, "password", password)), String.class);
        if (resp.getStatusCode().value() == 429) {
            // 测试间窗口串扰保护：等下一个限流窗口后重试一次
            try {
                Thread.sleep(5_200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            resp = rest.postForEntity("/api/v1/auth/login",
                    json(Map.of("username", username, "password", password)), String.class);
        }
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("login failed: " + resp.getStatusCode() + " " + resp.getBody());
        }
        return parseSession(resp, ipHeaders());
    }

    protected ResponseEntity<String> refreshCall(String refreshToken, String csrf, String origin, String ip) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", "mh_refresh=" + refreshToken);
        if (csrf != null) {
            headers.add("X-CSRF-Token", csrf);
        }
        if (origin != null) {
            headers.add("Origin", origin);
        }
        if (ip != null) {
            headers.add("X-Test-Client-Ip", ip);
        }
        return rest.exchange("/api/v1/auth/refresh", HttpMethod.POST, new HttpEntity<>(headers), String.class);
    }

    protected ResponseEntity<String> meCall(String accessToken) {
        HttpHeaders headers = bearer(accessToken);
        return rest.exchange("/api/v1/auth/me", HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    protected static HttpHeaders bearer(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return headers;
    }

    protected static HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders headers = ipHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    /** 每个请求带独立测试 IP，避免限流维度跨用例串扰（client-ip-header 仅测试启用）。 */
    protected static HttpHeaders ipHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Test-Client-Ip", "10.9." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250));
        return headers;
    }

    protected static Session parseSession(ResponseEntity<String> resp, HttpHeaders ignored) {
        try {
            JsonNode data = JSON.readTree(resp.getBody()).path("data");
            String refresh = cookieValue(resp.getHeaders().get("Set-Cookie"), "mh_refresh");
            return new Session(data.path("accessToken").asText(), refresh,
                    data.path("csrfToken").asText(), data.path("user").path("username").asText(),
                    data.path("user").path("publicId").asText());
        } catch (Exception e) {
            throw new IllegalStateException("parse session failed: " + resp.getBody(), e);
        }
    }

    protected static String cookieValue(List<String> setCookies, String name) {
        if (setCookies == null) {
            return null;
        }
        return setCookies.stream().filter(c -> c.startsWith(name + "=")).findFirst()
                .map(c -> c.substring(name.length() + 1, c.indexOf(';')))
                .orElse(null);
    }

    protected static String errorCode(ResponseEntity<String> resp) {
        try {
            return JSON.readTree(resp.getBody()).path("code").asText();
        } catch (Exception e) {
            throw new IllegalStateException("parse error body failed: " + resp.getBody(), e);
        }
    }

    /** 等待进入下一个限流窗口（window=5s），消除跨窗口计数 flaky。 */
    protected static void waitNextRateLimitWindow() throws InterruptedException {
        long now = System.currentTimeMillis() / 1000;
        long sleep = (5 - now % 5) * 1000 + 200;
        Thread.sleep(sleep);
    }
}
