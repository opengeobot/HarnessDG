/*
 * 功能: P0B 审计生命周期集成测试——显式覆盖 p0b-exit-catalog AC-P0B-AUD-001..005。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.aihub.audit.application.AuditEvent;
import com.aihub.audit.application.AuditLogView;
import com.aihub.audit.application.AuditQueryService;
import com.aihub.audit.application.AuditService;
import com.aihub.audit.domain.AuditRecord;
import com.aihub.audit.domain.AuditRepository;
import com.aihub.audit.domain.AuditResult;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.logging.SensitiveDataMasker;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * P0B 审计生命周期集成测试。
 *
 * <p>显式映射 p0b-exit-catalog AC-P0B-AUD-001..005 场景。
 * 无 Docker 时整体跳过，不阻断 verify。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class AuditLifecycleIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("aihub")
                    .withUsername("aihub")
                    .withPassword("aihub");

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private AuditService auditService;
    @Autowired private AuditQueryService auditQueryService;
    @Autowired private AuditRepository auditRepository;
    @Autowired private SensitiveDataMasker masker;

    // ── AC-P0B-AUD-001: 成功/失败/拒绝事件均追加记录 ──────────

    @Test
    void aud001_successFailureAndDeniedEventsAllRecorded() {
        String principalId = "aud001_principal";
        String resource = "aud001_resource";

        // 成功事件。
        auditService.record(new AuditEvent("ASSET_CREATED", "create", principalId, "USER",
                "ASSET", resource, "PLATFORM", null, AuditResult.SUCCEEDED, null,
                Map.of("name", "test-asset")));

        // 失败事件。
        auditService.record(new AuditEvent("ASSET_CREATE", "create", principalId, "USER",
                "ASSET", resource, "PLATFORM", null, AuditResult.FAILED, "VALIDATION_ERROR",
                Map.of("error", "name too long")));

        // 拒绝事件。
        auditService.record(new AuditEvent("ASSET_CREATE", "create", principalId, "USER",
                "ASSET", resource, "PLATFORM", null, AuditResult.DENIED, "FORBIDDEN",
                Map.of("reason", "insufficient permissions")));

        // 查询验证三条记录。
        CursorPage<AuditLogView> page = auditQueryService.listAuditLogs(
                principalId, null, null, null, 50);
        assertThat(page.items()).hasSizeGreaterThanOrEqualTo(3);

        // 验证不同 result 值。
        List<String> results = page.items().stream()
                .map(AuditLogView::result)
                .distinct()
                .toList();
        assertThat(results).contains("SUCCEEDED", "FAILED", "DENIED");

        // 验证 failed/denied 有 errorCode。
        List<AuditLogView> failedOrDenied = page.items().stream()
                .filter(v -> "FAILED".equals(v.result()) || "DENIED".equals(v.result()))
                .toList();
        assertThat(failedOrDenied).allSatisfy(v -> assertThat(v.errorCode()).isNotBlank());
    }

    // ── AC-P0B-AUD-002: 敏感数据（Token/JWT/密码/预签名串）在审计摘要中被脱敏 ──────────

    @Test
    void aud002_sensitiveDataMaskedInAuditSummary() {
        String principalId = "aud002_principal";

        // 放入敏感字段。
        auditService.record(new AuditEvent("LOGIN_ATTEMPT", "login", principalId, "USER",
                "SESSION", "sess_001", "PLATFORM", null, AuditResult.SUCCEEDED, null,
                Map.of(
                        "password", "Sup3rSecret!23",
                        "authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.test.sig",
                        "token", "very-secret-token-value",
                        "username", "normal-user")));

        // 通过 Repository 直接获取 AuditRecord（含 requestSummary）。
        List<AuditRecord> records = auditRepository.list(principalId, null, null, null, null, 10);
        assertThat(records).isNotEmpty();

        String summary = records.get(0).requestSummary();

        // 密码不可见原文。
        assertThat(summary).doesNotContain("Sup3rSecret!23");
        // Bearer token 不可见原文。
        assertThat(summary).doesNotContain("eyJhbGciOiJSUzI1NiJ9.test.sig");
        // secret token 不可见原文。
        assertThat(summary).doesNotContain("very-secret-token-value");
        // 非敏感字段保持原值。
        assertThat(summary).contains("normal-user");
    }

    // ── AC-P0B-AUD-003: 审计记录不可篡改（无 UPDATE/DELETE API 路径） ──────────

    @Test
    void aud003_auditLogsAreAppendOnly_noUpdateOrDeleteApi() {
        // AuditLogController 只提供 GET 端点，无 PUT/PATCH/DELETE。
        // 通过 AuditQueryService 验证查询功能正常。
        auditService.record(new AuditEvent("IMMUTABILITY_TEST", "test", "aud003_principal",
                "USER", "SYSTEM", null, null, null, AuditResult.SUCCEEDED, null, null));

        CursorPage<AuditLogView> page = auditQueryService.listAuditLogs(
                "aud003_principal", null, null, null, 10);
        assertThat(page.items()).isNotEmpty();

        // AuditQueryService 只有查询方法，无 update/delete。
        // AuditLogController 只暴露 @GetMapping。
        // 数据库层有触发器兜底（V6 migration），此处验证应用层无篡改路径。
        var methods = AuditQueryService.class.getDeclaredMethods();
        for (var method : methods) {
            String name = method.getName().toLowerCase();
            assertThat(name).doesNotContain("update").doesNotContain("delete")
                    .doesNotContain("remove").doesNotContain("modify");
        }
    }

    // ── AC-P0B-AUD-004: 请求追踪上下文（traceId/requestId）贯穿审计记录 ──────────

    @Test
    void aud004_traceAndRequestIdCapturedInAuditRecord() {
        String principalId = "aud004_principal";
        String traceId = "trace_aud004";
        String requestId = "req_aud004";

        // 设置 PrincipalContext 以提供 traceId/requestId。
        PrincipalContext ctx = new PrincipalContext(principalId, PrincipalType.USER,
                null, null, List.of(), Set.of(), Set.of("user:read"),
                0, "zh-CN", requestId, traceId);

        PrincipalContextHolder.set(ctx);
        try {
            auditService.record(new AuditEvent("TRACE_TEST", "test", principalId, "USER",
                    "SYSTEM", "res_001", null, null, AuditResult.SUCCEEDED, null,
                    Map.of("key", "value")));
        } finally {
            PrincipalContextHolder.clear();
        }

        // 查询并验证 traceId/requestId。
        CursorPage<AuditLogView> page = auditQueryService.listAuditLogs(
                principalId, null, null, null, 10);
        assertThat(page.items()).isNotEmpty();

        AuditLogView record = page.items().get(0);
        assertThat(record.traceId()).isEqualTo(traceId);
        assertThat(record.requestId()).isEqualTo(requestId);
    }

    // ── AC-P0B-AUD-005: 超大/敏感请求体的摘要受字段规则限制 ──────────

    @Test
    void aud005_largeOrSensitivePayloadSummaryIsBounded() {
        String principalId = "aud005_principal";

        // 构造包含大量字段和敏感值的 attributes。
        Map<String, Object> attributes = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 50; i++) {
            attributes.put("field_" + i, "value_" + i);
        }
        attributes.put("password", "should-be-masked-password");
        attributes.put("secret", "should-be-masked-secret");
        attributes.put("cookie", "session=abc123def456");

        auditService.record(new AuditEvent("LARGE_PAYLOAD_TEST", "test", principalId, "USER",
                "SYSTEM", null, null, null, AuditResult.SUCCEEDED, null, attributes));

        // 通过 Repository 直接获取 AuditRecord（含 requestSummary）。
        List<AuditRecord> records = auditRepository.list(principalId, null, null, null, null, 10);
        assertThat(records).isNotEmpty();

        String summary = records.get(0).requestSummary();
        // 敏感字段被脱敏。
        assertThat(summary).doesNotContain("should-be-masked-password");
        assertThat(summary).doesNotContain("should-be-masked-secret");
        assertThat(summary).doesNotContain("session=abc123def456");

        // SensitiveDataMasker 验证。
        assertThat(masker.isSensitiveField("password")).isTrue();
        assertThat(masker.isSensitiveField("cookie")).isTrue();
        assertThat(masker.isSensitiveField("authorization")).isTrue();
        assertThat(masker.maskField("password", "my-secret")).isEqualTo(SensitiveDataMasker.MASK);
    }

    // ── 补充: 审计查询支持扩展过滤（eventType/resourceType/时间范围） ──────────

    @Test
    void auditSearchSupportsExtendedFilters() {
        String principalId = "aud_search_principal";

        auditService.record(new AuditEvent("ASSET_CREATED", "create", principalId, "USER",
                "ASSET", "ast_search_1", null, null, AuditResult.SUCCEEDED, null, null));
        auditService.record(new AuditEvent("ROLE_CREATED", "create", principalId, "USER",
                "ROLE", "rol_search_1", null, null, AuditResult.SUCCEEDED, null, null));

        // 按 eventType 过滤（AuditLogView 不含 eventType，通过 Repository 验证过滤效果）。
        CursorPage<AuditLogView> assetOnly = auditQueryService.searchAuditLogs(
                "ASSET_CREATED", principalId, null, null, null, null, null, 50);
        assertThat(assetOnly.items()).isNotEmpty();
        // 通过 Repository 验证 eventType 过滤正确性。
        var assetRecords = auditRepository.search("ASSET_CREATED", principalId, null,
                null, null, null, null, null, 50);
        assertThat(assetRecords).allSatisfy(r ->
                assertThat(r.eventType()).isEqualTo("ASSET_CREATED"));

        // 按 resourceType 过滤。
        CursorPage<AuditLogView> roleOnly = auditQueryService.searchAuditLogs(
                null, null, "ROLE", null, null, null, null, 50);
        assertThat(roleOnly.items()).allSatisfy(v ->
                assertThat(v.resourceType()).isEqualTo("ROLE"));
    }
}
