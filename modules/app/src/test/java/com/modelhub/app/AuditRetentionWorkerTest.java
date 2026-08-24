package com.modelhub.app;

import com.modelhub.identity.worker.AuditRetentionWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 审计保留清理（07 §2 SEC-05）：超过保留期（默认 180 天）的审计行被删除，新鲜行保留。
 */
class AuditRetentionWorkerTest extends BaseIntegrationTest {

    @Autowired
    private AuditRetentionWorker worker;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void cleanupDeletesExpiredAuditRowsAndKeepsFreshOnes() {
        String actor = unique("retention");
        jdbc.update("INSERT INTO audit_logs (actor, action, resource, result, created_at) "
                        + "VALUES (?, 'auth.login', 'user:test', 'success', now() - interval '200 days')", actor);
        jdbc.update("INSERT INTO audit_logs (actor, action, resource, result, created_at) "
                + "VALUES (?, 'auth.login', 'user:test', 'success', now())", actor);

        int deleted = worker.cleanupExpiredAuditLogs();

        assertThat(deleted).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE actor = ? "
                + "AND created_at < now() - interval '180 days'", Integer.class, actor)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE actor = ? "
                + "AND created_at >= now() - interval '180 days'", Integer.class, actor)).isEqualTo(1);
    }
}
