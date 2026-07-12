/*
 * 功能: 审计服务单元测试——验证字段级脱敏与审计记录追加写。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.audit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.audit.domain.AuditRecord;
import com.aihub.audit.domain.AuditRepository;
import com.aihub.audit.domain.AuditResult;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.logging.SensitiveDataMasker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import java.util.stream.Stream;

/**
 * 审计服务单元测试。
 */
class AuditServiceTest {

    private AuditRepository repository;
    private IdGenerator idGenerator;
    private SensitiveDataMasker masker;
    private ObjectMapper objectMapper;
    private Clock clock;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        repository = mock(AuditRepository.class);
        idGenerator = mock(IdGenerator.class);
        when(idGenerator.generate(IdPrefix.AUDIT)).thenReturn("aud_test1");
        masker = new SensitiveDataMasker();
        objectMapper = new ObjectMapper();
        clock = Clock.fixed(Instant.parse("2026-07-01T10:00:00Z"), ZoneOffset.UTC);
        auditService = new AuditService(repository, idGenerator, clock, masker, objectMapper);

        PrincipalContext ctx = new PrincipalContext(
                "usr_1", PrincipalType.USER, "subj-1", null, List.of(), Set.of(), Set.of(),
                0, "zh-CN", "req-1", "trace-1");
        PrincipalContextHolder.set(ctx);
    }

    @AfterEach
    void tearDown() {
        PrincipalContextHolder.clear();
    }

    @Test
    void shouldMaskSensitiveFieldsInSummary() throws Exception {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("password", "secret123");
        attributes.put("token", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c3JfMSJ9.signature");
        attributes.put("username", "admin");
        attributes.put("authorization", "Bearer abc123");

        AuditEvent event = new AuditEvent(
                "USER_LOGIN", "LOGIN", "usr_1", "USER",
                "USER", "usr_1", null, null, AuditResult.SUCCEEDED, null, attributes);

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        auditService.record(event);

        verify(repository, times(1)).append(captor.capture());
        AuditRecord record = captor.getValue();
        assertThat(record.requestSummary()).isNotNull();
        JsonNode summary = objectMapper.readTree(record.requestSummary());
        // 密码字段应被整体掩码
        assertThat(summary.path("password").asText()).isEqualTo(SensitiveDataMasker.MASK);
        // authorization 字段应被整体掩码
        assertThat(summary.path("authorization").asText()).isEqualTo(SensitiveDataMasker.MASK);
        // token 字段应被整体掩码
        assertThat(summary.path("token").asText()).isEqualTo(SensitiveDataMasker.MASK);
        // username 非敏感，保留原值
        assertThat(summary.path("username").asText()).isEqualTo("admin");
    }

    @Test
    void shouldRecordEventWithPrincipalContext() {
        AuditEvent event = new AuditEvent(
                "ROLE_CREATED", null, null, null,
                null, "rol_1", null, null, AuditResult.SUCCEEDED, null, Map.of());

        auditService.record(event);

        verify(repository, times(1)).append(any(AuditRecord.class));
    }

    @Test
    void shouldHandleNullAttributes() {
        AuditEvent event = new AuditEvent(
                "SYSTEM_CONFIG", "CONFIG", "usr_1", "USER",
                null, null, null, null, AuditResult.SUCCEEDED, null, null);

        auditService.record(event);

        verify(repository, times(1)).append(any(AuditRecord.class));
    }

    @Test
    void shouldRecordDeniedResult() {
        AuditEvent event = new AuditEvent(
                "AUTH_PERMISSION_DENIED", "DENIED", "usr_1", "USER",
                null, null, null, null, AuditResult.DENIED, "AUTH_PERMISSION_DENIED", Map.of());

        auditService.record(event);

        verify(repository, times(1)).append(any(AuditRecord.class));
    }

    // ── 参数化测试：每个业务操作产生审计（AC-P0B-AUD-001/004）──────

    static Stream<Arguments> businessOperations() {
        return Stream.of(
                Arguments.of("ASSET_CREATED", "CREATE", "ast_1", "ASSET", AuditResult.SUCCEEDED, null),
                Arguments.of("ASSET_UPDATED", "UPDATE", "ast_1", "ASSET", AuditResult.SUCCEEDED, null),
                Arguments.of("ASSET_DELETED", "DELETE", "ast_1", "ASSET", AuditResult.SUCCEEDED, null),
                Arguments.of("VERSION_PUBLISHED", "PUBLISH", "ver_1", "VERSION", AuditResult.SUCCEEDED, null),
                Arguments.of("PUBLISH_APPROVED", "APPROVE", "pub_1", "PUBLISH", AuditResult.SUCCEEDED, null),
                Arguments.of("AUTH_PERMISSION_DENIED", "DENIED", "usr_1", "AUTH", AuditResult.DENIED, "AUTH_PERMISSION_DENIED"),
                Arguments.of("UPLOAD_SESSION_CREATED", "CREATE", "upl_1", "UPLOAD", AuditResult.SUCCEEDED, null),
                Arguments.of("ROLE_CREATED", "CREATE", "rol_1", "ROLE", AuditResult.SUCCEEDED, null),
                Arguments.of("USER_LOGIN", "LOGIN", "usr_1", "USER", AuditResult.SUCCEEDED, null)
        );
    }

    @ParameterizedTest(name = "操作 {0} 产生审计记录")
    @MethodSource("businessOperations")
    void eachBusinessOperationProducesAuditRecord(
            String eventType, String action, String resourceId,
            String resourceType, AuditResult result, String errorCode) {

        AuditEvent event = new AuditEvent(
                eventType, action, "usr_1", "USER",
                resourceType, resourceId, null, null, result, errorCode, Map.of());

        auditService.record(event);

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(repository, times(1)).append(captor.capture());

        AuditRecord record = captor.getValue();
        assertThat(record.eventType()).isEqualTo(eventType);
        assertThat(record.action()).isEqualTo(action);
        assertThat(record.resourceId()).isEqualTo(resourceId);
        assertThat(record.resourceType()).isEqualTo(resourceType);
        assertThat(record.result()).isEqualTo(result);
        assertThat(record.errorCode()).isEqualTo(errorCode);
    }

    // ── 审计字段完整性（AC-P0B-AUD-004）──────────────────────

    @Test
    void auditRecordContainsAllRequiredFields() {
        AuditEvent event = new AuditEvent(
                "ASSET_CREATED", "CREATE", "usr_1", "USER",
                "ASSET", "ast_test_complete", "org", "org_1",
                AuditResult.SUCCEEDED, null,
                Map.of("namespace", "test", "name", "my-model"));

        auditService.record(event);

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(repository).append(captor.capture());

        AuditRecord record = captor.getValue();
        // 审计 ID
        assertThat(record.auditId()).startsWith("aud_");
        // 主体信息
        assertThat(record.principalId()).isEqualTo("usr_1");
        assertThat(record.principalType()).isEqualTo("USER");
        // 资源信息
        assertThat(record.resourceType()).isEqualTo("ASSET");
        assertThat(record.resourceId()).isEqualTo("ast_test_complete");
        // Scope 信息
        assertThat(record.scopeType()).isEqualTo("org");
        assertThat(record.scopeId()).isEqualTo("org_1");
        // 结果
        assertThat(record.result()).isEqualTo(AuditResult.SUCCEEDED);
        // 追踪上下文
        assertThat(record.traceId()).isEqualTo("trace-1");
        assertThat(record.requestId()).isEqualTo("req-1");
        // 时间戳
        assertThat(record.occurredAt()).isNotNull();
        // 请求摘要已脱敏
        assertThat(record.requestSummary()).contains("namespace");
    }

    @Test
    void auditRecordFromPrincipalContextWhenEventFieldsNull() {
        // 当 event 字段为 null 时，应回退到 PrincipalContextHolder
        AuditEvent event = new AuditEvent(
                "SYSTEM_CONFIG", null, null, null,
                null, null, null, null, null, null, Map.of());

        auditService.record(event);

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(repository).append(captor.capture());

        AuditRecord record = captor.getValue();
        // 回退到 PrincipalContext
        assertThat(record.principalId()).isEqualTo("usr_1");
        assertThat(record.principalType()).isEqualTo("USER");
        // action 回退到 eventType
        assertThat(record.action()).isEqualTo("SYSTEM_CONFIG");
        // result 回退到 SUCCEEDED
        assertThat(record.result()).isEqualTo(AuditResult.SUCCEEDED);
    }

    @Test
    void durationMsExtractedFromAttributes() {
        AuditEvent event = new AuditEvent(
                "SLOW_QUERY", "QUERY", "usr_1", "USER",
                null, null, null, null, AuditResult.SUCCEEDED, null,
                Map.of("__durationMs", 1500));

        auditService.record(event);

        ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(repository).append(captor.capture());

        assertThat(captor.getValue().durationMs()).isEqualTo(1500L);
    }
}
