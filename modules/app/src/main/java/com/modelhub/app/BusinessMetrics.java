package com.modelhub.app;

import com.modelhub.shared.metrics.BusinessCounters;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 业务指标（07 §5.2）：Outbox pending/最老年龄、未完结 Job、活跃上传会话（Gauge，抓取时查询 DB）；
 * 登录失败、鉴权拒绝、限流触发（Counter，事件点递增）。
 * 事件源位于 identity-access/catalog 模块，经 shared {@link BusinessCounters} 解耦，
 * 本类是唯一实现；高基数字段（repoId/userId/路径）不得作为指标 label（07 §5.1）。
 */
@Component
public class BusinessMetrics implements BusinessCounters {

    static final String LOGIN_FAILURES = "modelhub_auth_login_failures_total";
    static final String AUTHZ_DENIALS = "modelhub_authz_denials_total";
    static final String RATE_LIMIT_TRIGGERS = "modelhub_ratelimit_triggers_total";

    private final MeterRegistry registry;
    private final JdbcTemplate jdbc;

    public BusinessMetrics(MeterRegistry registry, JdbcTemplate jdbc) {
        this.registry = registry;
        this.jdbc = jdbc;
        Gauge.builder("modelhub_outbox_pending", this,
                        o -> o.count("SELECT COUNT(*) FROM outbox_events WHERE published_at IS NULL"))
                .description("Outbox 待发布事件数")
                .register(registry);
        Gauge.builder("modelhub_outbox_pending_oldest_age_seconds", this,
                        o -> o.count("SELECT EXTRACT(EPOCH FROM (now() - MIN(created_at))) "
                                + "FROM outbox_events WHERE published_at IS NULL"))
                .description("Outbox 最老待发布事件年龄（秒）")
                .register(registry);
        Gauge.builder("modelhub_jobs_queued", this,
                        o -> o.count("SELECT COUNT(*) FROM jobs WHERE status IN "
                                + "('queued','running','retry_wait','cancel_requested')"))
                .description("队列中未完结 Job 数")
                .register(registry);
        Gauge.builder("modelhub_upload_sessions_active", this,
                        o -> o.count("SELECT COUNT(*) FROM upload_sessions WHERE status IN "
                                + "('initiated','uploading','verifying','scanning','committing')"))
                .description("活跃上传会话数")
                .register(registry);
    }

    @Override
    public void loginFailure() {
        registry.counter(LOGIN_FAILURES).increment();
    }

    @Override
    public void authzDenial() {
        registry.counter(AUTHZ_DENIALS).increment();
    }

    @Override
    public void rateLimitTrigger() {
        registry.counter(RATE_LIMIT_TRIGGERS).increment();
    }

    /** 聚合查询空集/查询失败时按 0 上报，避免抓取失败。 */
    private double count(String sql) {
        try {
            Long value = jdbc.queryForObject(sql, Long.class);
            return value == null ? 0.0 : value;
        } catch (DataAccessException e) {
            return 0.0;
        }
    }
}
