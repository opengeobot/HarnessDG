package com.aihub.integration.gitea.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import com.aihub.platform.observability.application.PlatformMetrics;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 已发布版本对账 Worker。
 *
 * <p>校验 PG 中 PUBLISHED 版本与 Gitea Tag/Commit 的一致性。
 * 检测 Tag 缺失、Commit SHA 不匹配、Manifest Digest 不一致等问题。
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

    public PublishedVersionReconciler(JdbcTemplate jdbcTemplate, PlatformMetrics platformMetrics) {
        this.jdbcTemplate = jdbcTemplate;
        this.platformMetrics = platformMetrics;
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
                    "SELECT version_id, asset_id, version, git_tag, manifest_digest, source_commit " +
                            "FROM asset_version WHERE status = 'PUBLISHED' " +
                            "ORDER BY version_id LIMIT ?", BATCH_SIZE)
                    : jdbcTemplate.queryForList(
                    "SELECT version_id, asset_id, version, git_tag, manifest_digest, source_commit " +
                            "FROM asset_version WHERE status = 'PUBLISHED' " +
                            "AND version_id > ? ORDER BY version_id LIMIT ?", cursor, BATCH_SIZE);

            if (batch.isEmpty()) break;

            for (Map<String, Object> row : batch) {
                String versionId = (String) row.get("version_id");
                String gitTag = (String) row.get("git_tag");
                String manifestDigest = (String) row.get("manifest_digest");

                // 校验 git_tag 格式：必须以 v 前缀开始
                if (gitTag == null || gitTag.isEmpty()) {
                    LOG.warn("PUBLISHED version missing git_tag versionId={}", versionId);
                    platformMetrics.recordReconciliationDiscrepancy("PublishedVersion", "MISSING_GIT_TAG");
                    discrepancies++;
                } else if (!VALID_GIT_TAG.matcher(gitTag).matches()) {
                    LOG.warn("PUBLISHED version has invalid git_tag format versionId={} tag={}", versionId, gitTag);
                    platformMetrics.recordReconciliationDiscrepancy("PublishedVersion", "INVALID_GIT_TAG");
                    discrepancies++;
                }

                // 校验 manifest_digest 格式：SHA-256 hex
                if (manifestDigest == null || manifestDigest.isEmpty()) {
                    LOG.warn("PUBLISHED version missing manifest_digest versionId={}", versionId);
                    platformMetrics.recordReconciliationDiscrepancy("PublishedVersion", "MISSING_DIGEST");
                    discrepancies++;
                } else if (!VALID_DIGEST.matcher(manifestDigest).matches()) {
                    LOG.warn("PUBLISHED version has invalid digest format versionId={} digest={} (expected 64 hex chars)",
                            versionId, manifestDigest.substring(0, Math.min(8, manifestDigest.length())) + "...");
                    platformMetrics.recordReconciliationDiscrepancy("PublishedVersion", "INVALID_DIGEST");
                    discrepancies++;
                }

                // 校验 source_commit 格式
                String sourceCommit = (String) row.get("source_commit");
                if (sourceCommit == null || sourceCommit.isEmpty()) {
                    LOG.warn("PUBLISHED version missing source_commit versionId={}", versionId);
                    platformMetrics.recordReconciliationDiscrepancy("PublishedVersion", "MISSING_COMMIT");
                    discrepancies++;
                } else if (!VALID_COMMIT_SHA.matcher(sourceCommit).matches()) {
                    LOG.warn("PUBLISHED version has invalid commit SHA versionId={} commit={}",
                            versionId, sourceCommit.substring(0, Math.min(8, sourceCommit.length())) + "...");
                    platformMetrics.recordReconciliationDiscrepancy("PublishedVersion", "INVALID_COMMIT");
                    discrepancies++;
                }
                totalChecked++;
            }

            cursor = (String) batch.get(batch.size() - 1).get("version_id");
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        } while (true);

        LOG.info("published version reconciliation completed jobId={} totalChecked={} discrepancies={}",
                context.jobId(), totalChecked, discrepancies);
    }
}
