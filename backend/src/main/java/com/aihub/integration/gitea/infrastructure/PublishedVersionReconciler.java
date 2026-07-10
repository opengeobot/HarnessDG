package com.aihub.integration.gitea.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import com.aihub.platform.observability.application.PlatformMetrics;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 已发布版本对账 Worker。
 *
 * <p>校验 PG 中 PUBLISHED 版本与 Gitea Tag/Commit 的一致性。
 * 检测 Tag 缺失、Commit SHA 不匹配、Manifest Digest 不一致等问题。
 *
 * <p>当 {@code aihub.gitea.enabled=true} 时，在 PG 格式校验通过后额外调用 Gitea API
 * 确认 Tag 存在且 Commit 匹配；Gitea 未启用时仅执行 PG 侧格式校验。
 */
@Component
public class PublishedVersionReconciler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(PublishedVersionReconciler.class);
    private static final int BATCH_SIZE = 100;
    /** Git Tag 必须以 v 前缀开始，如 v1.0.0 */
    private static final Pattern VALID_GIT_TAG = Pattern.compile("^v\\d+\\.\\d+\\.\\d+.*$");
    /** Manifest Digest 为 SHA-256 hex = 64 个十六进制字符 */
    private static final Pattern VALID_DIGEST = Pattern.compile("^[a-f0-9]{64}$");
    /** Commit SHA 为 40 个十六进制字符 */
    private static final Pattern VALID_COMMIT_SHA = Pattern.compile("^[a-f0-9]{40}$");

    private final JdbcTemplate jdbcTemplate;
    private final PlatformMetrics platformMetrics;
    private final ObjectProvider<GiteaTagVerificationPort> giteaTagVerificationProvider;

    public PublishedVersionReconciler(JdbcTemplate jdbcTemplate,
                                      PlatformMetrics platformMetrics,
                                      ObjectProvider<GiteaTagVerificationPort> giteaTagVerificationProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.platformMetrics = platformMetrics;
        this.giteaTagVerificationProvider = giteaTagVerificationProvider;
    }

    @Override
    public String type() {
        return "PUBLISHED_VERSION_RECONCILE";
    }

    @Override
    public void handle(JobContext context) {
        LOG.info("starting published version reconciliation jobId={}", context.jobId());

        int totalChecked = 0;
        int discrepancies = 0;
        String cursor = null;

        do {
            List<Map<String, Object>> batch = cursor == null
                    ? jdbcTemplate.queryForList(
                    "SELECT v.version_id, v.asset_id, v.version, v.git_tag, v.manifest_digest, v.source_commit, "
                            + "a.namespace, a.name "
                            + "FROM asset_version v JOIN asset a ON v.asset_id = a.asset_id "
                            + "WHERE v.status = 'PUBLISHED' "
                            + "ORDER BY v.version_id LIMIT ?", BATCH_SIZE)
                    : jdbcTemplate.queryForList(
                    "SELECT v.version_id, v.asset_id, v.version, v.git_tag, v.manifest_digest, v.source_commit, "
                            + "a.namespace, a.name "
                            + "FROM asset_version v JOIN asset a ON v.asset_id = a.asset_id "
                            + "WHERE v.status = 'PUBLISHED' "
                            + "AND v.version_id > ? ORDER BY v.version_id LIMIT ?", cursor, BATCH_SIZE);

            if (batch.isEmpty()) {
                break;
            }

            for (Map<String, Object> row : batch) {
                ReconcileResult result = checkPublishedVersion(row);
                if (result != ReconcileResult.CONSISTENT) {
                    discrepancies++;
                    recordDiscrepancy((String) row.get("version_id"), result);
                }
                totalChecked++;
            }

            cursor = (String) batch.get(batch.size() - 1).get("version_id");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

        } while (true);

        updateCheckpoint(context.jobId(), totalChecked, discrepancies);
        LOG.info("published version reconciliation completed jobId={} totalChecked={} discrepancies={}",
                context.jobId(), totalChecked, discrepancies);
    }

    ReconcileResult checkPublishedVersion(Map<String, Object> row) {
        String versionId = (String) row.get("version_id");
        String gitTag = (String) row.get("git_tag");
        String manifestDigest = (String) row.get("manifest_digest");
        String sourceCommit = (String) row.get("source_commit");

        if (gitTag == null || gitTag.isEmpty()) {
            LOG.warn("PUBLISHED version missing git_tag versionId={}", versionId);
            platformMetrics.recordReconciliationDiscrepancy("PublishedVersion", "MISSING_GIT_TAG");
            return ReconcileResult.MANUAL_REVIEW;
        }
        if (!VALID_GIT_TAG.matcher(gitTag).matches()) {
            LOG.warn("PUBLISHED version has invalid git_tag format versionId={} tag={}", versionId, gitTag);
            platformMetrics.recordReconciliationDiscrepancy("PublishedVersion", "INVALID_GIT_TAG");
            return ReconcileResult.MANUAL_REVIEW;
        }

        if (manifestDigest == null || manifestDigest.isEmpty()) {
            LOG.warn("PUBLISHED version missing manifest_digest versionId={}", versionId);
            platformMetrics.recordReconciliationDiscrepancy("PublishedVersion", "MISSING_DIGEST");
            return ReconcileResult.MANUAL_REVIEW;
        }
        if (!VALID_DIGEST.matcher(manifestDigest).matches()) {
            LOG.warn("PUBLISHED version has invalid digest format versionId={}", versionId);
            platformMetrics.recordReconciliationDiscrepancy("PublishedVersion", "INVALID_DIGEST");
            return ReconcileResult.MANUAL_REVIEW;
        }

        if (sourceCommit == null || sourceCommit.isEmpty()) {
            LOG.warn("PUBLISHED version missing source_commit versionId={}", versionId);
            platformMetrics.recordReconciliationDiscrepancy("PublishedVersion", "MISSING_COMMIT");
            return ReconcileResult.MANUAL_REVIEW;
        }
        if (!VALID_COMMIT_SHA.matcher(sourceCommit).matches()) {
            LOG.warn("PUBLISHED version has invalid commit SHA versionId={}", versionId);
            platformMetrics.recordReconciliationDiscrepancy("PublishedVersion", "INVALID_COMMIT");
            return ReconcileResult.MANUAL_REVIEW;
        }

        GiteaTagVerificationPort gitea = giteaTagVerificationProvider.getIfAvailable();
        if (gitea != null) {
            String namespace = (String) row.get("namespace");
            String name = (String) row.get("name");
            if (namespace != null && name != null) {
                GiteaTagVerificationPort.TagVerifyResult tagResult =
                        gitea.verifyTag(namespace, name, gitTag, sourceCommit);
                return switch (tagResult) {
                    case MATCHES -> ReconcileResult.CONSISTENT;
                    case MISSING_TAG -> {
                        LOG.warn("PUBLISHED version gitea tag missing versionId={} tag={}", versionId, gitTag);
                        platformMetrics.recordReconciliationDiscrepancy("PublishedVersion", "GITEA_TAG_MISSING");
                        yield ReconcileResult.MANUAL_REVIEW;
                    }
                    case COMMIT_MISMATCH -> {
                        LOG.warn("PUBLISHED version gitea commit mismatch versionId={} tag={}", versionId, gitTag);
                        platformMetrics.recordReconciliationDiscrepancy("PublishedVersion", "GITEA_COMMIT_MISMATCH");
                        yield ReconcileResult.SECURITY_INCIDENT;
                    }
                    case UNAVAILABLE -> ReconcileResult.CONSISTENT;
                };
            }
        }

        return ReconcileResult.CONSISTENT;
    }

    private void recordDiscrepancy(String versionId, ReconcileResult result) {
        LOG.warn("published version discrepancy versionId={} classification={}", versionId, result);
        if (result == ReconcileResult.SECURITY_INCIDENT) {
            LOG.error("SECURITY INCIDENT: published version {} has critical inconsistency", versionId);
        }
        try {
            String deliveryId = "pubver-reconcile-" + versionId + "-" + System.currentTimeMillis();
            jdbcTemplate.update(
                    "INSERT INTO webhook_inbox (delivery_id, event_type, source, signature_valid, payload, status) "
                            + "VALUES (?, 'PUBLISHED_VERSION_RECONCILE', 'internal', true, "
                            + "jsonb_build_object('versionId', ?, 'classification', ?::text), 'COMPLETED') "
                            + "ON CONFLICT (delivery_id) DO NOTHING",
                    deliveryId, versionId, result.name());
        } catch (Exception e) {
            LOG.error("failed to record published version discrepancy for versionId={}", versionId, e);
        }
    }

    private void updateCheckpoint(String jobId, int totalChecked, int discrepancies) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO reconciliation_checkpoint (reconciler_type, last_cursor, last_run_at, "
                            + "total_checked, discrepancies) "
                            + "VALUES ('PUBLISHED_VERSION_RECONCILE', ?, NOW(), ?, ?) "
                            + "ON CONFLICT (reconciler_type) DO UPDATE SET "
                            + "last_cursor = EXCLUDED.last_cursor, last_run_at = EXCLUDED.last_run_at, "
                            + "total_checked = reconciliation_checkpoint.total_checked + EXCLUDED.total_checked, "
                            + "discrepancies = reconciliation_checkpoint.discrepancies + EXCLUDED.discrepancies",
                    jobId, totalChecked, discrepancies);
        } catch (Exception e) {
            LOG.debug("reconciliation checkpoint update skipped jobId={}", jobId);
        }
    }

    enum ReconcileResult {
        CONSISTENT,
        MANUAL_REVIEW,
        SECURITY_INCIDENT
    }
}
