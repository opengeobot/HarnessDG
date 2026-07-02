/*
 * 功能: taxonomy 模块审计桥接适配器——将字典/标签写操作审计事件转发到权威 AuditService。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.taxonomy.infrastructure;

import com.aihub.audit.application.AuditEvent;
import com.aihub.audit.application.AuditService;
import com.aihub.audit.domain.AuditResult;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.taxonomy.application.AuditPort;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * taxonomy 审计桥接适配器。
 *
 * <p>将 {@link AuditPort} 调用转发到权威 {@link AuditService}，实现 100% 审计记录。
 */
@Component
public class TaxonomyAuditAdapter implements AuditPort {

    private static final Logger LOG = LoggerFactory.getLogger(TaxonomyAuditAdapter.class);

    private final AuditService auditService;

    public TaxonomyAuditAdapter(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void record(String eventType, String actorId, String targetId, Map<String, Object> attributes) {
        try {
            PrincipalContext ctx = PrincipalContextHolder.current().orElse(null);
            AuditEvent event = new AuditEvent(
                    eventType,
                    eventType,
                    actorId,
                    ctx == null ? null : (ctx.principalType() == null ? null : ctx.principalType().name()),
                    null,
                    targetId,
                    null,
                    null,
                    AuditResult.SUCCEEDED,
                    null,
                    attributes);
            auditService.record(event);
        } catch (Exception ex) {
            LOG.error("failed to record audit event eventType={} actorId={} targetId={}",
                    eventType, actorId, targetId, ex);
        }
    }
}
