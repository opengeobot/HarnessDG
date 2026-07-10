package com.aihub.version.infrastructure;

import com.aihub.integration.gitea.domain.GiteaTagPublisher;
import com.aihub.integration.gitea.domain.GiteaTagPublisher.TagPublishMode;
import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 发布 Job Handler（不可变发布 Saga）。
 *
 * <p>处理 {@code VERSION_PUBLISH} 类型任务：
 * <ol>
 *   <li>P1 确认审批状态为 APPROVED</li>
 *   <li>P2 校验 Commit（冻结时摘要一致）</li>
 *   <li>P3 校验 DVC（工件哈希完整）</li>
 *   <li>P4 创建 Tag（Gitea 启用时调用 REST API，否则 PG-only）</li>
 *   <li>P5 验证保护（确认 Tag 唯一）</li>
 *   <li>P6 PG PUBLISHED 状态推进</li>
 *   <li>P7 Outbox 事件（P3 简化为审计日志）</li>
 * </ol>
 *
 * <p>Tag 冲突 → 标记请求 FAILED，永不覆盖。
 */
@Component
public class PublishJobHandler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(PublishJobHandler.class);

    private final VersionRepository versionRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final GiteaTagPublisher giteaTagPublisher;

    public PublishJobHandler(VersionRepository versionRepository,
                             JdbcTemplate jdbcTemplate,
                             ObjectMapper objectMapper,
                             GiteaTagPublisher giteaTagPublisher) {
        this.versionRepository = versionRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.giteaTagPublisher = giteaTagPublisher;
    }

    @Override
    public String type() {
        return "VERSION_PUBLISH";
    }

    @Override
    public void handle(JobContext context) throws Exception {
        JsonNode payload = objectMapper.readTree(context.payload());
        String requestId = payload.path("requestId").asText();
        String versionId = payload.path("versionId").asText();

        LOG.info("publishing version versionId={} requestId={}", versionId, requestId);

        // P1: 确认审批
        Map<String, Object> request = jdbcTemplate.queryForMap(
                "SELECT status, frozen_digest, frozen_source_commit FROM publish_request WHERE request_id = ?",
                requestId);
        String reqStatus = String.valueOf(request.get("status"));
        if (!"APPROVED".equals(reqStatus) && !"PUBLISHING".equals(reqStatus)) {
            LOG.warn("publish request not in APPROVED/PUBLISHING status requestId={} status={}", requestId, reqStatus);
            return;
        }

        // 更新请求状态为 PUBLISHING（幂等）
        jdbcTemplate.update(
                "UPDATE publish_request SET status = 'PUBLISHING' WHERE request_id = ? AND status IN ('APPROVED', 'PUBLISHING')",
                requestId);

        // P2: 校验版本状态
        Version version = versionRepository.findByVersionId(versionId).orElse(null);
        if (version == null) {
            markFailed(requestId);
            throw new IllegalStateException("version not found: " + versionId);
        }

        if (version.status() != VersionStatus.PENDING_REVIEW) {
            markFailed(requestId);
            throw new IllegalStateException("version not in PENDING_REVIEW: " + version.status());
        }

        // P3: 校验 Manifest 摘要一致性
        String frozenDigest = String.valueOf(request.get("frozen_digest"));
        if (version.manifestDigest() == null || !version.manifestDigest().equals(frozenDigest)) {
            markFailed(requestId);
            throw new IllegalStateException(
                    "manifest digest mismatch: expected=" + frozenDigest + " actual=" + version.manifestDigest());
        }

        Object frozenCommitObj = request.get("frozen_source_commit");
        if (frozenCommitObj != null && version.sourceCommit() != null
                && !version.sourceCommit().equalsIgnoreCase(String.valueOf(frozenCommitObj))) {
            markFailed(requestId);
            throw new IllegalStateException("source commit drift detected during publish");
        }

        // P4: 创建 Tag
        String gitTag = version.version();
        Long existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM asset_version WHERE git_tag = ? AND status = 'PUBLISHED' AND version_id <> ?",
                Long.class, gitTag, versionId);
        if (existing != null && existing > 0) {
            markFailed(requestId);
            throw new IllegalStateException("git tag conflict: " + gitTag);
        }

        String repoFullName = resolveRepoFullName(version.assetId());
        String sourceCommit = version.sourceCommit();
        if (sourceCommit == null || sourceCommit.isBlank()) {
            markFailed(requestId);
            throw new IllegalStateException("source commit required for publish");
        }

        GiteaTagPublisher.TagPublishResult tagResult =
                giteaTagPublisher.createProtectedTag(repoFullName, gitTag, sourceCommit);
        if (tagResult.mode() == TagPublishMode.PG_ONLY_GITEA_DISABLED) {
            LOG.warn("publish saga PG-only mode: gitea disabled, tag recorded in PostgreSQL only versionId={} tag={}",
                    versionId, gitTag);
        }

        // P5-P6: 发布版本
        version.publish(version.manifestDigest(), gitTag, context.principalId());
        versionRepository.update(version);

        // P7: 更新请求状态
        jdbcTemplate.update("UPDATE publish_request SET status = 'PUBLISHED', decided_at = ? WHERE request_id = ?",
                Instant.now(), requestId);

        LOG.info("version published successfully versionId={} requestId={} tag={} mode={}",
                versionId, requestId, gitTag, tagResult.mode());
    }

    private String resolveRepoFullName(String assetId) {
        return jdbcTemplate.queryForObject(
                "SELECT repo_full_name FROM asset WHERE asset_id = ?",
                String.class, assetId);
    }

    private void markFailed(String requestId) {
        jdbcTemplate.update("UPDATE publish_request SET status = 'FAILED', decided_at = ? WHERE request_id = ?",
                Instant.now(), requestId);
    }
}
