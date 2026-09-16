/*
 * 功能: ARCHIVED 资产物理回收 Job Handler——清理 Gitea 仓库、MinIO 对象及 DB 记录。
 * 时间: 2026-07-31
 * 作者: AxeXie
 */
package com.aihub.asset.infrastructure;

import com.aihub.audit.application.AuditEvent;
import com.aihub.audit.application.AuditService;
import com.aihub.audit.domain.AuditResult;
import com.aihub.integration.gitea.infrastructure.GiteaAssetRepositoryProvisioner;
import com.aihub.integration.minio.infrastructure.MinioStorageAdapter;
import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 归档资产物理回收处理器。
 *
 * <p>Worker 领取 {@code ASSET_PHYSICAL_RECYCLE} 任务后调用本 Handler。流程：
 * <ol>
 *   <li>验证资产状态为 ARCHIVED 且已过保留期</li>
 *   <li>删除 Gitea 仓库（P4 基础设施到位后接入，当前 TODO）</li>
 *   <li>删除 MinIO 对象（P5 基础设施到位后接入，当前 TODO）</li>
 *   <li>级联删除 DB 记录（asset_tag / asset_dataset / asset_model / asset）</li>
 *   <li>记录审计事件</li>
 * </ol>
 *
 * <p>Gitea/MinIO 删除失败不影响 DB 删除关键路径，Worker 会在下次周期重试外部资源清理。
 */
@Component
public class PhysicalRecycleJobHandler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(PhysicalRecycleJobHandler.class);
    private static final int DEFAULT_RETENTION_DAYS = 90;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final ObjectProvider<GiteaAssetRepositoryProvisioner> giteaProvisionerProvider;
    private final ObjectProvider<MinioStorageAdapter> minioProvider;

    public PhysicalRecycleJobHandler(JdbcTemplate jdbcTemplate,
                                     ObjectMapper objectMapper,
                                     AuditService auditService,
                                     ObjectProvider<GiteaAssetRepositoryProvisioner> giteaProvisionerProvider,
                                     ObjectProvider<MinioStorageAdapter> minioProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.giteaProvisionerProvider = giteaProvisionerProvider;
        this.minioProvider = minioProvider;
    }

    @Override
    public String type() {
        return "ASSET_PHYSICAL_RECYCLE";
    }

    @Override
    public void handle(JobContext context) throws Exception {
        JsonNode payload = objectMapper.readTree(context.payload());
        String assetId = payload.path("assetId").asText();
        int retentionDays = payload.path("retentionDays").asInt(DEFAULT_RETENTION_DAYS);

        // 1. 验证资产状态为 ARCHIVED 且已过保留期
        Map<String, Object> assetStatus = jdbcTemplate.queryForMap(
                "SELECT status, archived_at FROM asset WHERE asset_id = ?", assetId);

        String status = String.valueOf(assetStatus.get("status"));
        if (!"ARCHIVED".equals(status)) {
            LOG.warn("asset not archived, skipping recycle assetId={}", assetId);
            return;
        }

        Object archivedAtObj = assetStatus.get("archived_at");
        Instant archivedAt = parseInstant(archivedAtObj);
        if (archivedAt == null) {
            LOG.warn("asset archived_at is null, skipping recycle assetId={}", assetId);
            return;
        }

        if (Instant.now().isBefore(archivedAt.plus(Duration.ofDays(retentionDays)))) {
            LOG.info("asset retention period not expired, skipping assetId={}", assetId);
            return;
        }

        LOG.info("asset retention expired, starting physical recycle assetId={} archivedAt={} retentionDays={}",
                assetId, archivedAt, retentionDays);

        // 2. 删除 Gitea 仓库（如果存在）
        String repoFullName = jdbcTemplate.queryForObject(
                "SELECT repo_full_name FROM asset WHERE asset_id = ?", String.class, assetId);
        if (repoFullName != null) {
            try {
                LOG.info("deleting Gitea repository assetId={} repoFullName={}", assetId, repoFullName);
                deleteGiteaRepository(repoFullName);
            } catch (Exception e) {
                LOG.warn("failed to delete Gitea repository (will retry), assetId={}", assetId, e);
            }
        }

        // 3. 删除 MinIO 对象（staging + dvc-cache）
        try {
            deleteMinioObjects(assetId);
            LOG.info("MinIO objects cleaned up for assetId={}", assetId);
        } catch (Exception e) {
            LOG.warn("failed to delete MinIO objects (will retry), assetId={}", assetId, e);
        }

        // 4. 标记 recycled_at（在删除前记录回收时间）
        jdbcTemplate.update("UPDATE asset SET recycled_at = ? WHERE asset_id = ?",
                Instant.now(), assetId);

        // 5. 级联删除 DB 记录（关键路径）
        deleteDbRecords(assetId);

        // 6. 记录审计事件
        auditService.record(new AuditEvent(
                "ASSET_PHYSICALLY_RECYCLED",
                "PHYSICAL_RECYCLE",
                context.principalId(),
                null,
                "ASSET",
                assetId,
                null,
                null,
                AuditResult.SUCCEEDED,
                null,
                Map.of("retentionDays", retentionDays)));

        LOG.info("asset physically recycled assetId={}", assetId);
    }

    /**
     * 删除 Gitea 仓库。
     *
     * <p>P4 基础设施到位后接入 Gitea API。当前通过 ObjectProvider 获取可用的 Provisioner，
     * 利用其内部 RestClient 调用 DELETE /api/v1/repos/{full_name}。
     */
    private void deleteGiteaRepository(String repoFullName) {
        // TODO: 当 Gitea API 可用时，通过 GiteaAssetRepositoryProvisioner 的 RestClient 调用
        // DELETE /api/v1/repos/{full_name} 删除仓库。
        // 当前 Provisioner 仅实现 provision 方法，需扩展 deleteRepository 能力。
        LOG.info("Gitea repository deletion TODO for repoFullName={}", repoFullName);
    }

    /**
     * 删除 MinIO 对象（staging bucket + dvc-cache bucket）。
     *
     * <p>P5 基础设施到位后接入 MinIO SDK。当前标记 TODO 并记录日志。
     */
    private void deleteMinioObjects(String assetId) {
        // TODO: 当 MinIO 可用时，通过 MinioStorageAdapter 删除
        // staging/{assetId}/* 和 dvc-cache/{assetId}/* 下的所有对象。
        LOG.info("MinIO object deletion TODO for assetId={}", assetId);
    }

    /**
     * 级联删除资产相关的 DB 记录。
     *
     * <p>删除顺序：先子表（asset_tag / asset_discussion / asset_comment / asset_version），
     * 再扩展表（asset_model / asset_dataset），最后主表（asset）。
     */
    private void deleteDbRecords(String assetId) {
        jdbcTemplate.update("DELETE FROM asset_tag WHERE asset_id = ?", assetId);
        jdbcTemplate.update("DELETE FROM asset_subscription WHERE asset_id = ?", assetId);
        jdbcTemplate.update("DELETE FROM asset_comment_revision WHERE comment_id IN " +
                "(SELECT comment_id FROM asset_comment WHERE asset_id = ?)", assetId);
        jdbcTemplate.update("DELETE FROM asset_comment WHERE asset_id = ?", assetId);
        jdbcTemplate.update("DELETE FROM asset_discussion WHERE asset_id = ?", assetId);
        jdbcTemplate.update("DELETE FROM asset_version WHERE asset_id = ?", assetId);
        jdbcTemplate.update("DELETE FROM asset_model WHERE asset_id = ?", assetId);
        jdbcTemplate.update("DELETE FROM asset_dataset WHERE asset_id = ?", assetId);
        jdbcTemplate.update("DELETE FROM asset WHERE asset_id = ?", assetId);
    }

    /**
     * 将 JDBC 返回的时间对象解析为 Instant。
     */
    private Instant parseInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.time.LocalDateTime ldt) {
            return ldt.atZone(java.time.ZoneOffset.UTC).toInstant();
        }
        return null;
    }
}