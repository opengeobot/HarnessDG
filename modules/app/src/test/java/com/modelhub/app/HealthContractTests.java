package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运维与契约基座（07 §5.3 / 04 §6）：健康词汇 ok/degraded/unavailable、
 * 分页双模式互斥 400、未认证 401、traceId。
 */
class HealthContractTests extends BaseIntegrationTest {

    @Test
    void livez_always_returns_ok_without_envelope() throws Exception {
        ResponseEntity<String> resp = rest.getForEntity("/api/v1/livez", String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode body = JSON.readTree(resp.getBody());
        assertThat(body.path("status").asText()).isEqualTo("ok");
        assertThat(body.has("code")).isFalse(); // 健康端点不使用 envelope
    }

    @Test
    void readyz_reports_ok_when_database_available() throws Exception {
        ResponseEntity<String> resp = rest.getForEntity("/api/v1/readyz", String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        JsonNode body = JSON.readTree(resp.getBody());
        assertThat(body.path("status").asText()).isEqualTo("ok");
        assertThat(body.path("checks").path("database").asText()).isEqualTo("ok");
    }

    @Test
    void organizations_endpoint_requires_authentication() {
        ResponseEntity<String> resp = rest.getForEntity("/api/v1/organizations", String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void pagination_modes_are_mutually_exclusive() {
        Session s = register(unique("paging"), "Passw0rd-x12");
        ResponseEntity<String> mixed = rest.exchange(
                "/api/v1/organizations?page=1&pageSize=5&cursor=MSUzQWFi", HttpMethod.GET,
                new HttpEntity<>(bearer(s.accessToken())), String.class);
        assertThat(mixed.getStatusCode().value()).isEqualTo(400);
        assertThat(errorCode(mixed)).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void pagination_bounds_are_enforced() {
        Session s = register(unique("bounds"), "Passw0rd-x12");

        assertThat(status(s, "/api/v1/organizations?page=0")).isEqualTo(400);
        assertThat(status(s, "/api/v1/organizations?pageSize=101")).isEqualTo(400);
        assertThat(status(s, "/api/v1/organizations?page=1&pageSize=100")).isEqualTo(200);

        // cursor 模式：limit 越界 400；非法 cursor 400
        assertThat(status(s, "/api/v1/organizations/00000000-0000-0000-0000-000000000000/members?limit=101"))
                .isEqualTo(400);
        assertThat(status(s, "/api/v1/organizations/00000000-0000-0000-0000-000000000000/members?cursor=!!!"))
                .isEqualTo(400);
    }

    @Test
    void success_and_error_bodies_carry_trace_id() throws Exception {
        Session s = register(unique("trace"), "Passw0rd-x12");
        ResponseEntity<String> ok = meCall(s.accessToken());
        assertThat(JSON.readTree(ok.getBody()).path("traceId").asText()).isNotBlank();

        ResponseEntity<String> err = rest.getForEntity("/api/v1/organizations", String.class);
        assertThat(JSON.readTree(err.getBody()).path("traceId").asText()).isNotBlank();
    }

    private int status(Session s, String uri) {
        ResponseEntity<String> resp = rest.exchange(uri, HttpMethod.GET,
                new HttpEntity<>(bearer(s.accessToken())), String.class);
        return resp.getStatusCode().value();
    }
}
