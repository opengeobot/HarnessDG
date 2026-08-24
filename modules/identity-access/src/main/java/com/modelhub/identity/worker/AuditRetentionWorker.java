package com.modelhub.identity.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 审计日志保留清理（07 §2 SEC-05）：默认保留 180 天，超期行按周期删除。
 * audit_logs 只追加，删除仅由本 Worker 按保留策略执行。
 */
@Component
public class AuditRetentionWorker {

    private static final Logger log = LoggerFactory.getLogger(AuditRetentionWorker.class);

    private final JdbcTemplate jdbc;
    private final int retentionDays;

    public AuditRetentionWorker(JdbcTemplate jdbc,
                                @Value("${modelhub.governance.audit-retention-days:180}") int retentionDays) {
        this.jdbc = jdbc;
        this.retentionDays = retentionDays;
    }

    @Scheduled(fixedDelayString = "${modelhub.governance.audit-retention-interval-ms:86400000}")
    public void scheduledCleanup() {
        cleanupExpiredAuditLogs();
    }

    /** 删除 created_at 早于保留期起点的审计行，返回删除行数。 */
    public int cleanupExpiredAuditLogs() {
        int deleted = jdbc.update(
                "DELETE FROM audit_logs WHERE created_at < now() - (? * interval '1 day')", retentionDays);
        log.info("审计保留清理完成：删除 {} 行（保留期 {} 天）", deleted, retentionDays);
        return deleted;
    }
}
