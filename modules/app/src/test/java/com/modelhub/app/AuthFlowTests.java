package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 认证主流程（02 §6 / 04 §3）：注册、登录、me/ETag、注销、改密、锁定与管理员解锁。
 */
class AuthFlowTests extends BaseIntegrationTest {

    @Test
    void register_returns_201_envelope_cookies_and_namespace() {
        String username = unique("alice");
        HttpHeaders headers = ipHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = rest.postForEntity("/api/v1/auth/register",
                new HttpEntity<>(Map.of("username", username, "password", "Passw0rd-x12"), headers),
                String.class);

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        JsonNode root = json(resp);
        assertThat(root.path("code").asText()).isEqualTo("OK");
        assertThat(root.path("traceId").asText()).isNotBlank();
        JsonNode user = root.path("data").path("user");
        assertThat(user.path("username").asText()).isEqualTo(username);
        assertThat(user.path("namespaceId").asText()).isNotBlank();
        assertThat(root.path("data").path("accessToken").asText()).isNotBlank();

        List<String> cookies = resp.getHeaders().get("Set-Cookie");
        assertThat(cookies).isNotNull();
        String refresh = cookies.stream().filter(c -> c.startsWith("mh_refresh=")).findFirst().orElseThrow();
        String csrf = cookies.stream().filter(c -> c.startsWith("mh_csrf=")).findFirst().orElseThrow();
        assertThat(refresh).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/api/v1/auth");
        assertThat(csrf).doesNotContain("HttpOnly");
        // 两条 Cookie 必须独立输出，禁止逗号折叠
        assertThat(cookies.size()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void register_duplicate_username_conflicts_case_insensitively() {
        String username = unique("bob");
        register(username, "Passw0rd-x12");
        HttpHeaders headers = ipHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = rest.postForEntity("/api/v1/auth/register",
                new HttpEntity<>(Map.of("username", username.toUpperCase(), "password", "Passw0rd-x12"), headers),
                String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(409);
        assertThat(errorCode(resp)).isEqualTo("CONFLICT");
    }

    @Test
    void register_invalid_username_returns_422() {
        HttpHeaders headers = ipHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = rest.postForEntity("/api/v1/auth/register",
                new HttpEntity<>(Map.of("username", "ab", "password", "Passw0rd-x12"), headers), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(422);
        assertThat(errorCode(resp)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void register_unknown_field_rejected_400() {
        HttpHeaders headers = ipHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = rest.postForEntity("/api/v1/auth/register",
                new HttpEntity<>(Map.of("username", unique("carol"), "password", "Passw0rd-x12",
                        "evilField", "1"), headers), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void login_failure_never_reveals_which_part_is_wrong() {
        String username = unique("dave");
        register(username, "Passw0rd-x12");

        HttpHeaders headers = ipHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> wrongPassword = rest.postForEntity("/api/v1/auth/login",
                new HttpEntity<>(Map.of("username", username, "password", "Wrong-Password-1"), headers),
                String.class);
        ResponseEntity<String> unknownUser = rest.postForEntity("/api/v1/auth/login",
                new HttpEntity<>(Map.of("username", unique("ghost"), "password", "Wrong-Password-1"), headers),
                String.class);

        assertThat(wrongPassword.getStatusCode().value()).isEqualTo(401);
        assertThat(unknownUser.getStatusCode().value()).isEqualTo(401);
        assertThat(message(wrongPassword)).isEqualTo(message(unknownUser));
    }

    @Test
    void me_requires_auth_and_supports_etag_conditional_update() throws Exception {
        assertThat(meCall(null).getStatusCode().value()).isEqualTo(401);

        Session s = register(unique("erin"), "Passw0rd-x12");
        ResponseEntity<String> me = meCall(s.accessToken());
        assertThat(me.getStatusCode().value()).isEqualTo(200);
        String etag = me.getHeaders().getETag();
        assertThat(etag).isNotBlank();

        // PATCH 缺 If-Match → 400
        HttpHeaders noMatch = bearer(s.accessToken());
        noMatch.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> missing = rest.exchange("/api/v1/auth/me", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("nickname", "新昵称"), noMatch), String.class);
        assertThat(missing.getStatusCode().value()).isEqualTo(400);

        // 错误 ETag → 412
        HttpHeaders stale = bearer(s.accessToken());
        stale.setContentType(MediaType.APPLICATION_JSON);
        stale.setIfMatch("\"deadbeef\"");
        ResponseEntity<String> conflict = rest.exchange("/api/v1/auth/me", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("nickname", "新昵称"), stale), String.class);
        assertThat(conflict.getStatusCode().value()).isEqualTo(412);
        assertThat(errorCode(conflict)).isEqualTo("PRECONDITION_FAILED");

        // 正确 ETag → 200 且 ETag 变化
        HttpHeaders ok = bearer(s.accessToken());
        ok.setContentType(MediaType.APPLICATION_JSON);
        ok.setIfMatch(etag);
        ResponseEntity<String> updated = rest.exchange("/api/v1/auth/me", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("nickname", "新昵称"), ok), String.class);
        assertThat(updated.getStatusCode().value()).isEqualTo(200);
        assertThat(JSON.readTree(updated.getBody()).path("data").path("nickname").asText()).isEqualTo("新昵称");
        assertThat(updated.getHeaders().getETag()).isNotEqualTo(etag);
    }

    @Test
    void logout_immediately_invalidates_refresh_session() {
        Session s = register(unique("frank"), "Passw0rd-x12");

        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", "mh_refresh=" + s.refreshToken());
        headers.add("X-CSRF-Token", s.csrfToken());
        headers.add("Origin", ORIGIN);
        ResponseEntity<String> logout = rest.exchange("/api/v1/auth/logout", HttpMethod.POST,
                new HttpEntity<>(headers), String.class);
        assertThat(logout.getStatusCode().value()).isEqualTo(204);

        // 注销后立即 refresh 必须失败（不受并发刷新宽限窗保护）
        ResponseEntity<String> after = refreshCall(s.refreshToken(), s.csrfToken(), ORIGIN, null);
        assertThat(after.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void logout_requires_origin_and_csrf_triple_match() {
        Session s = register(unique("gina"), "Passw0rd-x12");

        HttpHeaders evilOrigin = new HttpHeaders();
        evilOrigin.add("Cookie", "mh_refresh=" + s.refreshToken());
        evilOrigin.add("X-CSRF-Token", s.csrfToken());
        evilOrigin.add("Origin", "http://evil.example");
        ResponseEntity<String> badOrigin = rest.exchange("/api/v1/auth/logout", HttpMethod.POST,
                new HttpEntity<>(evilOrigin), String.class);
        assertThat(badOrigin.getStatusCode().value()).isEqualTo(401);
        assertThat(errorCode(badOrigin)).isEqualTo("CSRF_INVALID");

        HttpHeaders noCsrf = new HttpHeaders();
        noCsrf.add("Cookie", "mh_refresh=" + s.refreshToken());
        noCsrf.add("Origin", ORIGIN);
        ResponseEntity<String> badCsrf = rest.exchange("/api/v1/auth/logout", HttpMethod.POST,
                new HttpEntity<>(noCsrf), String.class);
        assertThat(badCsrf.getStatusCode().value()).isEqualTo(401);
        assertThat(errorCode(badCsrf)).isEqualTo("CSRF_INVALID");
    }

    @Test
    void change_password_revokes_all_sessions_and_bumps_auth_version() {
        Session s = register(unique("henry"), "Passw0rd-x12");
        Session second = login(s.username(), "Passw0rd-x12");

        HttpHeaders headers = bearer(s.accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> change = rest.exchange("/api/v1/auth/change-password", HttpMethod.POST,
                new HttpEntity<>(Map.of("currentPassword", "Passw0rd-x12", "newPassword", "NewPassw0rd-xy"), headers),
                String.class);
        assertThat(change.getStatusCode().value()).isEqualTo(204);

        // 旧 access token 因 auth_version 提升立即失效
        assertThat(meCall(s.accessToken()).getStatusCode().value()).isEqualTo(401);
        assertThat(meCall(second.accessToken()).getStatusCode().value()).isEqualTo(401);
        // refresh 会话全部吊销（含过期标记，宽限窗内也不补发）
        assertThat(refreshCall(second.refreshToken(), second.csrfToken(), ORIGIN, null)
                .getStatusCode().value()).isEqualTo(401);
        // 新密码可登录
        assertThat(login(s.username(), "NewPassw0rd-xy").accessToken()).isNotBlank();
    }

    @Test
    void logout_all_invalidates_every_session() {
        String username = unique("iris");
        Session a = register(username, "Passw0rd-x12");
        Session b = login(username, "Passw0rd-x12");

        ResponseEntity<String> resp = rest.exchange("/api/v1/auth/logout-all", HttpMethod.POST,
                new HttpEntity<>(bearer(a.accessToken())), String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(204);

        assertThat(meCall(a.accessToken()).getStatusCode().value()).isEqualTo(401);
        assertThat(refreshCall(b.refreshToken(), b.csrfToken(), ORIGIN, null)
                .getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void locked_account_can_only_be_unlocked_by_platform_admin() throws Exception {
        String username = unique("jack");
        String publicId = register(username, "Passw0rd-x12").userPublicId();

        HttpHeaders headers = ipHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> fail = rest.postForEntity("/api/v1/auth/login",
                    new HttpEntity<>(Map.of("username", username, "password", "Wrong-Password-1"), headers),
                    String.class);
            assertThat(fail.getStatusCode().value()).isEqualTo(401);
        }
        waitNextRateLimitWindow();

        // 锁定后即使密码正确也拒绝
        ResponseEntity<String> locked = rest.postForEntity("/api/v1/auth/login",
                new HttpEntity<>(Map.of("username", username, "password", "Passw0rd-x12"), headers), String.class);
        assertThat(locked.getStatusCode().value()).isEqualTo(401);

        // 普通用户解锁 → 403
        Session nobody = register(unique("kate"), "Passw0rd-x12");
        HttpHeaders forbidden = bearer(nobody.accessToken());
        forbidden.add("Idempotency-Key", java.util.UUID.randomUUID().toString());
        ResponseEntity<String> denied = rest.exchange("/api/v1/admin/users/" + publicId + ":unlock",
                HttpMethod.POST, new HttpEntity<>(forbidden), String.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(403);
        assertThat(errorCode(denied)).isEqualTo("FORBIDDEN");

        // bootstrap platform_admin 解锁 → 200
        Session admin = login("platform-root", "Boot-Strap-1x");
        HttpHeaders ok = bearer(admin.accessToken());
        ok.add("Idempotency-Key", java.util.UUID.randomUUID().toString());
        ResponseEntity<String> unlock = rest.exchange("/api/v1/admin/users/" + publicId + ":unlock",
                HttpMethod.POST, new HttpEntity<>(ok), String.class);
        assertThat(unlock.getStatusCode().value()).isEqualTo(204);

        waitNextRateLimitWindow();
        assertThat(login(username, "Passw0rd-x12").accessToken()).isNotBlank();

        // 不存在的用户 → 404
        HttpHeaders notFound = bearer(admin.accessToken());
        notFound.add("Idempotency-Key", java.util.UUID.randomUUID().toString());
        ResponseEntity<String> missing = rest.exchange(
                "/api/v1/admin/users/00000000-0000-0000-0000-000000000000:unlock",
                HttpMethod.POST, new HttpEntity<>(notFound), String.class);
        assertThat(missing.getStatusCode().value()).isEqualTo(404);
    }

    private static JsonNode json(ResponseEntity<String> resp) {
        try {
            return JSON.readTree(resp.getBody());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String message(ResponseEntity<String> resp) {
        return json(resp).path("message").asText();
    }
}
