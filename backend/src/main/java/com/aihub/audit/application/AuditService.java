/*
 * 功能: 权威审计应用服务——追加写审计记录（不可篡改），字段级脱敏，全链路追踪上下文透传。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.audit.application;

import com.aihub.audit.domain.AuditRecord;
import com.aihub.audit.domain.AuditRepository;
import com.aihub.audit.domain.AuditResult;
import com.aihub.platform.observability.application.PlatformMetrics;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.logging.SensitiveDataMasker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * 权威审计应用服务。
 *
 * <p>{@link #record(AuditEvent)} 以追加写方式落库（{@code audit_log} 表，应用层无 UPDATE/DELETE 路径，
 * 数据库层另有触发器兜底）。P0-B 采用同步落库，确保审计 100% 记录。落库前对 {@code requestSummary}
 * 做字段级脱敏（Token/JWT/密码/预签名串/凭据/Cookie），绝不记录密钥/凭据明文。
 *
 * <p>不使用 {@code @Transactional}：在无事务上下文时（如授权拒绝）追加写自动提交、即使随后抛出异常也保留；
 * 在业务事务内（成功审计）则加入当前事务随业务原子提交。
 */
@Service
public class AuditService {

    private final AuditRepository repository;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final SensitiveDataMasker masker;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<PlatformMetrics> platformMetricsProvider;

    public AuditService(AuditRepository repository, IdGenerator idGenerator, Clock clock,
                        SensitiveDataMasker masker, ObjectMapper objectMapper) {
        this(repository, idGenerator, clock, masker, objectMapper, null);
    }

    public AuditService(AuditRepository repository, IdGenerator idGenerator, Clock clock,
                        SensitiveDataMasker masker, ObjectMapper objectMapper,
                        ObjectProvider<PlatformMetrics> platformMetricsProvider) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.masker = masker;
        this.objectMapper = objectMapper;
        this.platformMetricsProvider = platformMetricsProvider;
    }

    /**
     * 记录一条审计事件（追加写，不可改/删）。
     */
    public void record(AuditEvent event) {
        PrincipalContext ctx = PrincipalContextHolder.current().orElse(null);
        Instant now = clock.instant();
        String summary = maskSummary(event.attributes());
        AuditRecord record = new AuditRecord(
                null,
                idGenerator.generate(IdPrefix.AUDIT),
                event.eventType(),
                event.principalId() != null ? event.principalId()
                        : (ctx == null ? null : ctx.principalId()),
                event.principalType() != null ? event.principalType()
                        : (ctx == null ? null : (ctx.principalType() == null ? null : ctx.principalType().name())),
                event.action() != null ? event.action() : event.eventType(),
                event.resourceType() != null ? event.resourceType() : deriveResourceType(event.eventType()),
                event.resourceId(),
                event.scopeType(),
                event.scopeId(),
                event.result() != null ? event.result() : AuditResult.SUCCEEDED,
                event.errorCode(),
                summary,
                ctx == null ? null : ctx.traceId(),
                ctx == null ? null : ctx.requestId(),
                event.attributes() == null ? null
                        : (event.attributes().containsKey("__durationMs") ? toLong(event.attributes().get("__durationMs")) : null),
                now);
        repository.append(record);
        recordMetrics(event);
    }

    private void recordMetrics(AuditEvent event) {
        if (platformMetricsProvider == null) {
            return;
        }
        PlatformMetrics metrics = platformMetricsProvider.getIfAvailable();
        if (metrics == null) {
            return;
        }
        String result = event.result() != null ? event.result().name() : AuditResult.SUCCEEDED.name();
        metrics.recordAuditEvent(event.eventType(), result);
    }

    private String maskSummary(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("__")) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof String s) {
                masked.put(key, masker.maskField(key, s));
            } else if (value != null) {
                masked.put(key, masker.maskField(key, String.valueOf(value)));
            } else {
                masked.put(key, null);
            }
        }
        try {
            return objectMapper.writeValueAsString(masked);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private static String deriveResourceType(String eventType) {
        if (eventType == null) {
            return null;
        }
        int idx = eventType.lastIndexOf('_');
        return idx > 0 ? eventType.substring(0, idx) : eventType;
    }

    private static Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
