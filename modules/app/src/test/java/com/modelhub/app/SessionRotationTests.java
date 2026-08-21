package com.modelhub.app;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Refresh 轮换与重放检测（02 §6.1）：
 * - 每次 refresh 轮换；宽限窗内重用视为并发刷新补发；
 * - 超过宽限窗视为重放攻击，吊销整个 family；
 * - 吊销事实持久化在 PostgreSQL，Redis 清空不影响；Redis 不可用降级本地限流。
 * grace 设为 2s 加速测试。
 */
@TestPropertySource(properties = "modelhub.identity.session.replay-grace-seconds=2")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SessionRotationTests extends BaseIntegrationTest {

    @Autowired
    StringRedisTemplate redis;

    @Test
    @Order(1)
    void refresh_rotates_credentials() {
        Session s = register(unique("rotate"), "Passw0rd-x12");
        ResponseEntity<String> refreshed = refreshCall(s.refreshToken(), s.csrfToken(), ORIGIN, null);
        assertThat(refreshed.getStatusCode().value()).isEqualTo(200);
        Session next = parseSession(refreshed, null);
        assertThat(next.refreshToken()).isNotEqualTo(s.refreshToken());
        assertThat(next.accessToken()).isNotBlank();
        assertThat(meCall(next.accessToken()).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @Order(2)
    void concurrent_refresh_within_grace_window_is_tolerated() {
        Session s = register(unique("conc"), "Passw0rd-x12");
        // 同一旧 token 连续两次（并发客户端场景）：第二次在宽限窗内补发新会话，不吊销 family
        ResponseEntity<String> first = refreshCall(s.refreshToken(), s.csrfToken(), ORIGIN, null);
        ResponseEntity<String> second = refreshCall(s.refreshToken(), s.csrfToken(), ORIGIN, null);
        assertThat(first.getStatusCode().value()).isEqualTo(200);
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        Session latest = parseSession(second, null);
        assertThat(meCall(latest.accessToken()).getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @Order(3)
    void refresh_requires_csrf_and_origin_match() {
        Session s = register(unique("csrf"), "Passw0rd-x12");

        ResponseEntity<String> badCsrf = refreshCall(s.refreshToken(), "wrong-csrf", ORIGIN, null);
        assertThat(badCsrf.getStatusCode().value()).isEqualTo(401);
        assertThat(errorCode(badCsrf)).isEqualTo("CSRF_INVALID");

        ResponseEntity<String> missingCsrf = refreshCall(s.refreshToken(), null, ORIGIN, null);
        assertThat(missingCsrf.getStatusCode().value()).isEqualTo(401);
        assertThat(errorCode(missingCsrf)).isEqualTo("CSRF_INVALID");

        ResponseEntity<String> evilOrigin = refreshCall(s.refreshToken(), s.csrfToken(), "http://evil.example", null);
        assertThat(evilOrigin.getStatusCode().value()).isEqualTo(401);
        assertThat(errorCode(evilOrigin)).isEqualTo("CSRF_INVALID");

        ResponseEntity<String> noOrigin = refreshCall(s.refreshToken(), s.csrfToken(), null, null);
        assertThat(noOrigin.getStatusCode().value()).isEqualTo(401);
        assertThat(errorCode(noOrigin)).isEqualTo("CSRF_INVALID");
    }

    @Test
    @Order(4)
    void replay_after_grace_window_revokes_entire_family() throws Exception {
        Session s = register(unique("replay"), "Passw0rd-x12");
        ResponseEntity<String> rotated = refreshCall(s.refreshToken(), s.csrfToken(), ORIGIN, null);
        assertThat(rotated.getStatusCode().value()).isEqualTo(200);
        Session next = parseSession(rotated, null);

        Thread.sleep(2_500); // 超过 2s 宽限窗

        // 重用已轮换的旧 token → 判定重放攻击，吊销 family
        ResponseEntity<String> replay = refreshCall(s.refreshToken(), s.csrfToken(), ORIGIN, null);
        assertThat(replay.getStatusCode().value()).isEqualTo(401);
        assertThat(replay.getBody()).contains("重用");

        // family 内最新会话同样失效
        ResponseEntity<String> latest = refreshCall(next.refreshToken(), next.csrfToken(), ORIGIN, null);
        assertThat(latest.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @Order(5)
    void redis_flush_does_not_restore_revoked_sessions() throws Exception {
        Session s = register(unique("flush"), "Passw0rd-x12");
        // 先正常轮换使旧会话 revoked
        assertThat(refreshCall(s.refreshToken(), s.csrfToken(), ORIGIN, null)
                .getStatusCode().value()).isEqualTo(200);
        // Redis 全清：限流计数丢失，但会话吊销事实在 PostgreSQL
        Objects.requireNonNull(redis.getConnectionFactory()).getConnection().serverCommands().flushAll();

        Thread.sleep(2_500); // 超出 2s 宽限窗，重放必须走吊销判定而非并发补发

        ResponseEntity<String> replay = refreshCall(s.refreshToken(), s.csrfToken(), ORIGIN, null);
        assertThat(replay.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    @Order(99) // 最后执行：暂停 Redis 验证降级，随后恢复
    void redis_unavailable_degrades_to_local_rate_limiting() throws Exception {
        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
        try {
            // Redis 不可用时登录仍成功（本地保守限流接管）
            String username = unique("degrade");
            rest.postForEntity("/api/v1/auth/register",
                    json(java.util.Map.of("username", username, "password", "Passw0rd-x12")), String.class);
            Session s = login(username, "Passw0rd-x12");
            assertThat(meCall(s.accessToken()).getStatusCode().value()).isEqualTo(200);
        } finally {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
        }
    }
}
