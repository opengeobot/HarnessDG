package com.modelhub.app;

import com.github.dockerjava.api.model.HostConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modelhub.artifact.scan.ContentScanner;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * M1/M2 集成测试基座：真实 PostgreSQL 16 + Redis 7 + Gitea（TST-04 禁止内存库作为集成证据）。
 * bootstrap 在首个上下文启用，创建 platform-root 管理员；限流阈值取默认 5/60s。
 * Gitea 管理员凭据 modelhub/ModelHub-Root-1x，供 catalog provisioning Saga 使用。
 */
@SpringBootTest(classes = {ModelHubApplication.class, BaseIntegrationTest.TestContentScannerConfig.class},
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class BaseIntegrationTest {

    protected static final String ORIGIN = "http://localhost:5173";
    protected static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 100_000);

    /**
     * 共享测试网络 modelhub-tcnet（宿主侧预创建，运行测试的 JVM 容器须以
     * --network modelhub-tcnet 接入）：Docker Desktop for Windows 下新建容器的宿主
     * 发布端口转发间歇性不可达（容器内已就绪但映射端口连接被拒/超时），故基础设施
     * 容器统一经内网容器名 + 容器内端口直连，完全绕开发布端口。
     */
    static final String TEST_NET = "modelhub-tcnet";
    protected static final String POSTGRES_HOST = "mh-tc-postgres";
    protected static final String REDIS_HOST = "mh-tc-redis";
    protected static final String GITEA_HOST = "mh-tc-gitea";
    protected static final String MINIO_HOST = "mh-tc-minio";

    /** 接入共享网络并固定容器名（用户自定义网络内嵌 DNS 可按容器名解析）。 */
    protected static Consumer<com.github.dockerjava.api.command.CreateContainerCmd> joinNet(String name) {
        return cmd -> {
            cmd.withName(name);
            HostConfig hc = cmd.getHostConfig() != null ? cmd.getHostConfig() : HostConfig.newHostConfig();
            cmd.withHostConfig(hc.withNetworkMode(TEST_NET));
        };
    }

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("modelhub").withUsername("modelhub").withPassword("modelhub")
            .withCreateContainerCmdModifier(joinNet(POSTGRES_HOST))
            // 默认 JDBC 等待策略探测宿主映射端口，改用日志等待（第 2 次出现才是正式监听，
            // 第 1 次为 initdb 临时单用户模式）；内网连通性由 static 块探测兼容
            .waitingFor(Wait.forLogMessage(".*ready to accept connections.*", 2)
                    .withStartupTimeout(Duration.ofMinutes(3)))
            .withReuse(true);

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withCreateContainerCmdModifier(joinNet(REDIS_HOST))
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(2)))
            .withReuse(true);

    /** MinIO（M2b artifact 阶段真相源）：所有 app 上下文必需（ArtifactConfiguration 无条件建 S3Client）。 */
    static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("docker.m.daocloud.io/minio/minio:latest"))
            .withCommand("server", "/data", "--console-address", ":9001")
            .withEnv("MINIO_ROOT_USER", "modelhub")
            .withEnv("MINIO_ROOT_PASSWORD", "ModelHub-Minio-1x")
            .withExposedPorts(9000, 9001)
            .withCreateContainerCmdModifier(joinNet(MINIO_HOST))
            // HTTP 等待策略依赖宿主映射端口，改用日志等待（API 就绪标志行）
            .waitingFor(Wait.forLogMessage(".*API:.*:9000.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(3)))
            .withReuse(true);

    static final GenericContainer<?> GITEA = new GenericContainer<>(
            DockerImageName.parse("docker.m.daocloud.io/gitea/gitea:1.24.0"))
            .withExposedPorts(3000)
            .withCreateContainerCmdModifier(joinNet(GITEA_HOST))
            // 首次启动默认进入 install 页面（/api/v1/version 返回 404），
            // 必须经 GITEA__ 环境变量完成自动安装；HTTP 就绪经内网轮询（static 块）确认
            .withEnv("GITEA__database__DB_TYPE", "sqlite3")
            .withEnv("GITEA__server__ROOT_URL", "http://localhost:3000/")
            .withEnv("GITEA__security__INSTALL_LOCK", "true")
            .withEnv("GITEA__service__DISABLE_REGISTRATION", "true")
            .waitingFor(Wait.forLogMessage(".*Prepare to run web server.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(3)))
            .withReuse(true);

    static {
        POSTGRES.start();
        REDIS.start();
        GITEA.start();
        MINIO.start();
        awaitIntranetReady();
        try {
            // 以运行用户 git 身份创建管理员，避免 root 写 /data 产生权限问题
            GITEA.execInContainer("sh", "-c",
                    "su git -c \"gitea admin user create --username modelhub "
                            + "--password ModelHub-Root-1x --email modelhub@example.com "
                            + "--admin --must-change-password=false\"");
        } catch (Exception e) {
            throw new IllegalStateException("Gitea 管理员创建失败", e);
        }
    }

    /** 经内网容器名探测 postgres/gitea 就绪（容器内端口，不经宿主发布端口）。 */
    private static void awaitIntranetReady() {
        long deadline = System.currentTimeMillis() + 120_000;
        awaitTcp(POSTGRES_HOST, 5432, deadline);
        awaitHttp("http://" + GITEA_HOST + ":3000/api/v1/version", deadline);
    }

    private static void awaitTcp(String host, int port, long deadline) {
        while (System.currentTimeMillis() < deadline) {
            try (java.net.Socket s = new java.net.Socket()) {
                s.connect(new java.net.InetSocketAddress(host, port), 2_000);
                return;
            } catch (Exception ignored) {
                sleepQuiet();
            }
        }
        throw new IllegalStateException("内网 " + host + ":" + port + " 就绪超时");
    }

    private static void awaitHttp(String url, long deadline) {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        while (System.currentTimeMillis() < deadline) {
            try {
                java.net.http.HttpResponse<String> r = client.send(
                        java.net.http.HttpRequest.newBuilder(java.net.URI.create(url))
                                .timeout(Duration.ofSeconds(3)).GET().build(),
                        java.net.http.HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 200) {
                    return;
                }
            } catch (Exception ignored) {
            }
            sleepQuiet();
        }
        throw new IllegalStateException("内网 " + url + " 就绪超时");
    }

    private static void sleepQuiet() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 共享网络内 JDBC URL（隔离库同样用容器名直连构造）。 */
    protected static String jdbcUrlOf(String host, String database) {
        return "jdbc:postgresql://" + host + ":5432/" + database;
    }

    /**
     * 基础设施覆写钩子（故障注入等隔离上下文用）：子类在 static 块设置，
     * 上下文 refresh 时消费一次后立即清空，防止后续新建上下文误读残留值。
     * 背景：@TestPropertySource 内联属性经 addFirst 加入 Environment，但
     * @DynamicPropertySource 在 refresh 期同样 addFirst 且执行更晚，优先级反而更高，
     * 内联属性无法覆盖动态注册的数据源，只能经由本钩子从源头替换。
     */
    static volatile Map<String, String> infraOverrides = Map.of();

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        Map<String, String> o = infraOverrides;
        infraOverrides = Map.of();
        registry.add("spring.datasource.url",
                () -> o.getOrDefault("spring.datasource.url", jdbcUrlOf(POSTGRES_HOST, "modelhub") + "?stringtype=unspecified"));
        registry.add("spring.datasource.username",
                () -> o.getOrDefault("spring.datasource.username", POSTGRES.getUsername()));
        registry.add("spring.datasource.password",
                () -> o.getOrDefault("spring.datasource.password", POSTGRES.getPassword()));
        // 审计/锁定走 REQUIRES_NEW 嵌套事务，并发请求需同时持有多个连接；默认 10 会饿死串行化
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> 30);
        registry.add("spring.data.redis.host", () -> REDIS_HOST);
        registry.add("spring.data.redis.port", () -> 6379);
        registry.add("modelhub.identity.allowed-origins", () -> ORIGIN);
        registry.add("modelhub.identity.client-ip-header", () -> "X-Test-Client-Ip");
        registry.add("modelhub.identity.rate-limit.max-attempts", () -> 5);
        registry.add("modelhub.identity.rate-limit.window-seconds", () -> 5);
        registry.add("modelhub.bootstrap.enabled", () -> true);
        registry.add("modelhub.bootstrap.username", () -> "platform-root");
        registry.add("modelhub.bootstrap.password", () -> "Boot-Strap-1x");
        registry.add("modelhub.gitea.base-url",
                () -> o.getOrDefault("modelhub.gitea.base-url",
                        "http://" + GITEA_HOST + ":3000"));
        registry.add("modelhub.gitea.username", () -> "modelhub");
        registry.add("modelhub.gitea.password", () -> "ModelHub-Root-1x");
        // 加速 Outbox 收敛，避免用例等待默认 2s 轮询
        registry.add("modelhub.catalog.poll-interval-ms",
                () -> o.getOrDefault("modelhub.catalog.poll-interval-ms", "200"));
        if (o.containsKey("modelhub.catalog.provision-max-retries")) {
            registry.add("modelhub.catalog.provision-max-retries",
                    () -> o.get("modelhub.catalog.provision-max-retries"));
        }
        // artifact：S3Client Bean 无条件创建，所有上下文必须携带凭据；
        // internal 与 public 基址在测试网段同址（05 §2：预签名 URL 对测试客户端可达）
        registry.add("modelhub.artifact.internal-endpoint",
                () -> "http://" + MINIO_HOST + ":9000");
        registry.add("modelhub.artifact.public-base-url",
                () -> "http://" + MINIO_HOST + ":9000");
        registry.add("modelhub.artifact.access-key", () -> "modelhub");
        registry.add("modelhub.artifact.secret-key", () -> "ModelHub-Minio-1x");
        registry.add("modelhub.artifact.bucket", () -> "artifacts");
        registry.add("modelhub.artifact.api-base-url", () -> "http://localhost:8080");
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
            // 注意：UserView 的 publicId 字段用 @JsonProperty("id") 序列化，
            // 故用户 publicId 在响应里是 user.id（非 user.publicId），取错会得到空串导致
            // /admin/users/{publicId} 路由失配（publicId 为空 → NoResourceFoundException 500）。
            return new Session(data.path("accessToken").asText(), refresh,
                    data.path("csrfToken").asText(), data.path("user").path("username").asText(),
                    data.path("user").path("id").asText());
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

    /**
     * 集成测试用内容扫描器：固定返回 clean，让上传状态机扫过 scanning 阶段（05 §6.3）。
     * 生产环境 DefaultContentScanner 为 fail-closed（未配置真实扫描器即拒绝发布），
     * 若测试沿用它会抛异常触发 Job 无限重试，导致上传永远停在 scanning 态而超时。
     * 真实扫描（ClamAV 等）的 rejected/error 分支由 artifact 模块单元测试覆盖，
     * 集成测试关注的是上传/发布/下载链路本身，故此处以 clean 直通。
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class TestContentScannerConfig {
        @Bean
        @Primary
        ContentScanner testContentScanner() {
            return (objectKey, sizeBytes) -> new ContentScanner.ScanResult("clean", 1);
        }
    }
}
