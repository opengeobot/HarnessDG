/*
 * 功能: identity 模块审计桥接适配器——将认证域审计事件转发到权威 AuditService。
 * 时间: 2026-07-02
 * 作者: AxeXie
 */
package com.aihub.identity.infrastructure;

import com.aihub.audit.application.AuditEvent;
import com.aihub.audit.application.AuditService;
import com.aihub.audit.domain.AuditResult;
import com.aihub.identity.application.AuditPort;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * identity 审计桥接适配器。
 *
 * <p>将 {@link AuditPort} 调用转发到权威 {@link AuditService}，实现认证事件 100% 审计记录。
 * 审计失败仅告警，不回滚业务事务（审计为旁路）。resourceType 传 null 由 AuditService 从 eventType 推导。
 */
@Component
public class IdentityAuditAdapter implements AuditPort {

    private static final Logger LOG = LoggerFactory.getLogger(IdentityAuditAdapter.class);

    private final AuditService auditService;

    public IdentityAuditAdapter(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void record(String eventType,
                       AuditResult result,
                       String actorId,
                       String targetId,
                       String errorCode,
                       Map<String, Object> attributes) {
        try {
            PrincipalContext ctx = PrincipalContextHolder.current().orElse(null);
            String principalType = ctx == null || ctx.principalType() == null
                    ? null
                    : ctx.principalType().name();
            AuditEvent event = new AuditEvent(
                    eventType,
                    eventType,
                    actorId,
                    principalType,
                    null,
                    targetId,
                    null,
                    null,
                    result == null ? AuditResult.SUCCEEDED : result,
                    errorCode,
                    attributes);
            auditService.record(event);
        } catch (Exception ex) {
            LOG.error("failed to record identity audit event eventType={} actorId={} targetId={}",
                    eventType, actorId, targetId, ex);
        }
    }
}
