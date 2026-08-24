package com.modelhub.app;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
 * 业务指标（07 §5.2）：登录失败/限流触发/鉴权拒绝 Counter 递增，
 * /actuator/prometheus 暴露 modelhub_ 指标（含 DB 轮询 Gauge）。
 * 鉴权拒绝只统计 FORBIDDEN（防枚举 404 不计入）。
 */
class BusinessMetricsTest extends CatalogTestSupport {

    @Autowired
    private MeterRegistry registry;

    @Test
    void failedLoginIncrementsCounterAndPrometheusExposesModelhubMetrics() throws Exception {
        Session s = newUser("bmf");
        double before = registry.counter(BusinessMetrics.LOGIN_FAILURES).count();

        ResponseEntity<String> resp = badLogin(s.username(), randomIp());
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
        assertThat(registry.counter(BusinessMetrics.LOGIN_FAILURES).count()).isGreaterThan(before);

        // /actuator/prometheus 需认证（仅 health/info 匿名）：携带 Bearer 抓取
        ResponseEntity<String> scrape = rest.exchange("/actuator/prometheus", HttpMethod.GET,
                new HttpEntity<>(bearer(s.accessToken())), String.class);
        assertThat(scrape.getStatusCode().value()).isEqualTo(200);
        assertThat(scrape.getBody()).contains("modelhub_auth_login_failures_total");
        assertThat(scrape.getBody()).contains("modelhub_outbox_pending");
        assertThat(scrape.getBody()).contains("modelhub_outbox_pending_oldest_age_seconds");
        assertThat(scrape.getBody()).contains("modelhub_jobs_queued");
        assertThat(scrape.getBody()).contains("modelhub_upload_sessions_active");
    }

    @Test
    void rateLimitDenialIncrementsCounter() throws Exception {
        double before = registry.counter(BusinessMetrics.RATE_LIMIT_TRIGGERS).count();
        String ghost = unique("bmghost");
        String ip = "10.202.202.1";

        // 并发突发 6 次（max=5/window=5s）：若突发跨窗口边界未触发 429，等待下一窗口重试（RateLimitTests 模式）
        List<ResponseEntity<String>> responses = List.of();
        for (int attempt = 0; attempt < 3; attempt++) {
            waitNextRateLimitWindow();
            responses = burst(6, () -> badLogin(ghost, ip));
            if (responses.stream().anyMatch(r -> r.getStatusCode().value() == 429)) {
                break;
            }
        }

        assertThat(responses.stream().filter(r -> r.getStatusCode().value() == 429).count()).isEqualTo(1);
        assertThat(registry.counter(BusinessMetrics.RATE_LIMIT_TRIGGERS).count()).isGreaterThan(before);
    }

    @Test
    void authzDenialCountsOnlyForbiddenDenials() throws Exception {
        Session owner = newUser("bmdown");
        Session member = newUser("bmdmem");
        String org = createOrg(owner, unique("bmo").toLowerCase());
        assertThat(addMember(owner, org, member.userPublicId(), "member").getStatusCode().value()).isEqualTo(201);
        String orgNs = orgNamespaceId(org);
        String repoId = dataNode(createRepo(owner, orgNs, unique("BmOrg"), "organization", null, null))
                .path("id").asText();
        awaitLifecycleHttp(owner.accessToken(), repoId, "active");

        double before = registry.counter(BusinessMetrics.AUTHZ_DENIALS).count();
        // 组织 member 对非自建仓库无 WRITE：有关系 → 403（FORBIDDEN 拒绝路径）
        long version = dataNode(repoDetail(member.accessToken(), repoId)).path("version").asLong();
        ResponseEntity<String> patch = patchRepo(member.accessToken(), repoId,
                Map.of("displayName", "X"), etagOfVersion(version));
        assertThat(patch.getStatusCode().value()).isEqualTo(403);
        assertThat(registry.counter(BusinessMetrics.AUTHZ_DENIALS).count()).isGreaterThan(before);

        // 无关系主体访问 private 仓库 → 防枚举 404（RESOURCE_NOT_FOUND），不计入鉴权拒绝
        Session stranger = newUser("bmdstr");
        double before404 = registry.counter(BusinessMetrics.AUTHZ_DENIALS).count();
        String privId = dataNode(createRepo(owner, userNamespaceId(owner), unique("BmPriv"), "private", null, null))
                .path("id").asText();
        awaitLifecycleHttp(owner.accessToken(), privId, "active");
        assertThat(repoDetail(stranger.accessToken(), privId).getStatusCode().value()).isEqualTo(404);
        assertThat(registry.counter(BusinessMetrics.AUTHZ_DENIALS).count()).isEqualTo(before404);
    }

    // ---------- helpers ----------

    private ResponseEntity<String> badLogin(String username, String ip) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add("X-Test-Client-Ip", ip);
        return rest.postForEntity("/api/v1/auth/login",
                new HttpEntity<>(Map.of("username", username, "password", "Wrong-Password-1"), h), String.class);
    }

    /** 并发突发发送 n 个请求，等待全部完成并收集响应（RateLimitTests 模式）。 */
    private List<ResponseEntity<String>> burst(int n,
            java.util.function.Supplier<ResponseEntity<String>> call) throws Exception {
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

    private String createOrg(Session actor, String slug) {
        HttpHeaders h = bearer(actor.accessToken());
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> resp = rest.postForEntity("/api/v1/organizations",
                new HttpEntity<>(Map.of("slug", slug, "name", "组织 " + slug), h), String.class);
        if (resp.getStatusCode().value() != 201) {
            throw new IllegalStateException("create org failed: " + resp.getBody());
        }
        return dataNode(resp).path("id").asText();
    }

    private ResponseEntity<String> addMember(Session actor, String orgId, String userPublicId, String role) {
        HttpHeaders h = bearer(actor.accessToken());
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity("/api/v1/organizations/" + orgId + "/members",
                new HttpEntity<>(Map.of("userId", userPublicId, "role", role), h), String.class);
    }

    /** 组织 namespace 的 publicId（经内部 id 查询）。 */
    private String orgNamespaceId(String orgPublicId) {
        Long internalId = organizations.findByPublicId(java.util.UUID.fromString(orgPublicId))
                .orElseThrow().getId();
        return namespaces.findByOrganizationIdAndNamespaceType(internalId, "organization")
                .orElseThrow().getPublicId().toString();
    }
}
