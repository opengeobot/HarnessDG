/*
 * 功能: job→audit 桥接适配器——任务 DEAD/人工操作审计事件转发到权威 AuditService。
 * 时间: 2026-09-21
 * 作者: AxeXie
 */
package com.aihub.job.infrastructure;

import com.aihub.audit.application.AuditEvent;
import com.aihub.audit.application.AuditService;
import com.aihub.audit.domain.AuditResult;
import com.aihub.job.application.JobAuditPort;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * job→audit 桥接适配器。
 *
 * <p>实现 {@link JobAuditPort}，把任务审计事件交给权威 {@link AuditService} 追加写；
 * 与 {@code TaxonomyAuditAdapter} 同型，业务模块不直接依赖 audit 内部结构。
 *
 * <p><b>与桥接模式的唯一差异：审计写失败必须上抛</b>。本卡一致性门禁是
 * 「状态迁移与审计同一事务原子提交——迁移成功而无审计即为缺陷」，
 * 若像 taxonomy 适配器那样吞掉异常，就会制造正是门禁要排除的半状态；
 * 通知（JobNotificationAdapter）才是真正的旁路，失败不回滚。
 */
@Component
public class JobAuditAdapter implements JobAuditPort {

    private final AuditService auditService;

    public JobAuditAdapter(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void record(String eventType, String action, String jobId, String actorId,
                       Map<String, Object> attributes) {
        // Worker 线程无请求上下文；principalId 缺省时共享服务同样落 null，
        // 这里显式标注 system，与 JobNotificationAdapter 的收件人口径一致。
        String principal = actorId != null && !actorId.isBlank() ? actorId : "system";
        auditService.record(new AuditEvent(
                eventType,
                action == null ? "job:dead" : action,
                principal,
                // principalType 留给共享服务从请求上下文补全（Worker 线程无上下文 → null）。
                null,
                "JOB",
                jobId,
                null,
                null,
                AuditResult.SUCCEEDED,
                null,
                attributes));
    }
}
