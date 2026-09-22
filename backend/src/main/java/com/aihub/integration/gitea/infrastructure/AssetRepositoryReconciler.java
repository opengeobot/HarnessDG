/*
 * 功能: 资产仓库对账 Worker，校验 PG 记录与 Gitea 仓库一致性。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

import com.aihub.job.domain.Job;
import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import com.aihub.job.domain.JobRepository;
import com.aihub.job.domain.JobStatus;
import com.aihub.platform.observability.application.PlatformMetrics;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 资产仓库对账 Worker。
 *
 * <p>PG↔Gitea 对账：检测资产记录与 Gitea 仓库之间的不一致。
 * 差异分类：AUTO_REPAIR / MANUAL_REVIEW / SECURITY_INCIDENT。
 * 游标分批（每批 100）+ 速率限制 + checkpoint。
 *
 * <p>当 {@code aihub.gitea.enabled=true} 时，在 PG 名称/命名空间校验通过后，
 * 额外调用 Gitea API 确认仓库存在；缺失时标记 {@code MANUAL_REVIEW} 并入队
 * {@code REPOSITORY_PROVISION} 重试建仓。Gitea 未启用时仅执行 PG 侧格式校验。
 */
@Component
public class AssetRepositoryReconciler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AssetRepositoryReconciler.class);
    private static final int BATCH_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;
    private final PlatformMetrics platformMetrics;
    private final ObjectProvider<GiteaRepositoryExistencePort> giteaExistenceProvider;
    private final JobRepository jobRepository;
    private final IdGenerator idGenerator;

    public AssetRepositoryReconciler(JdbcTemplate jdbcTemplate,
                                     PlatformMetrics platformMetrics,
                                     ObjectProvider<GiteaRepositoryExistencePort> giteaExistenceProvider,
                                     JobRepository jobRepository,
                                     IdGenerator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.platformMetrics = platformMetrics;
        this.giteaExistenceProvider = giteaExistenceProvider;
        this.jobRepository = jobRepository;
        this.idGenerator = idGenerator;
    }

    @Override
    public String type() {
        return "ASSET_REPO_RECONCILE";
    }

    @Override
    public void handle(JobContext context) {
        LOG.info("starting asset repository reconciliation jobId={}", context.jobId());

        int totalChecked = 0;
        int discrepancies = 0;
        String cursor = null;

        do {
            List<Map<String, Object>> batch = cursor == null
                    ? jdbcTemplate.queryForList(
                    "SELECT asset_id, name, namespace, provisioning_status " +
                            "FROM asset WHERE provisioning_status = 'COMPLETED' " +
                            "ORDER BY asset_id LIMIT ?", BATCH_SIZE)
                    : jdbcTemplate.queryForList(
                    "SELECT asset_id, name, namespace, provisioning_status " +
                            "FROM asset WHERE provisioning_status = 'COMPLETED' " +
                            "AND asset_id > ? ORDER BY asset_id LIMIT ?", cursor, BATCH_SIZE);

            if (batch.isEmpty()) {
                break;
            }

            for (Map<String, Object> row : batch) {
                String assetId = (String) row.get("asset_id");
                String name = (String) row.get("name");
                String namespace = (String) row.get("namespace");

                ReconcileResult result = checkAssetRepository(assetId, name, namespace);
                if (result != ReconcileResult.CONSISTENT) {
                    discrepancies++;
                    recordDiscrepancy(assetId, result);
                }
                totalChecked++;
            }

            cursor = (String) batch.get(batch.size() - 1).get("asset_id");

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

        } while (true);

        LOG.info("asset repository reconciliation completed jobId={} totalChecked={} discrepancies={}",
                context.jobId(), totalChecked, discrepancies);
    }

    private static final java.util.regex.Pattern VALID_NAME =
            java.util.regex.Pattern.compile("^[a-z0-9][a-z0-9._-]{0,63}$");
    private static final java.util.regex.Pattern VALID_NAMESPACE =
            java.util.regex.Pattern.compile("^[a-z0-9][a-z0-9._/-]{0,127}$");

    ReconcileResult checkAssetRepository(String assetId, String name, String namespace) {
        if (name == null || name.isEmpty()) {
            LOG.warn("asset {} has null/empty name", assetId);
            return ReconcileResult.MANUAL_REVIEW;
        }
        if (!VALID_NAME.matcher(name).matches()) {
            LOG.warn("asset {} has invalid name format: '{}'", assetId, name);
            return ReconcileResult.MANUAL_REVIEW;
        }

        if (namespace == null || namespace.isEmpty()) {
            LOG.warn("asset {} has null/empty namespace", assetId);
            return ReconcileResult.AUTO_REPAIR;
        }
        if (!VALID_NAMESPACE.matcher(namespace).matches()) {
            LOG.warn("asset {} has invalid namespace format: '{}'", assetId, namespace);
            return ReconcileResult.MANUAL_REVIEW;
        }

        String expectedFullName = namespace + "/" + name;
        if (expectedFullName.length() > 200) {
            LOG.warn("asset {} full_name exceeds 200 chars: '{}'", assetId, expectedFullName);
            return ReconcileResult.MANUAL_REVIEW;
        }

        GiteaRepositoryExistencePort gitea = giteaExistenceProvider.getIfAvailable();
        if (gitea != null && !gitea.repositoryExists(namespace, name)) {
            LOG.warn("asset {} gitea repository missing: {}", assetId, expectedFullName);
            enqueueReprovisionJob(assetId);
            return ReconcileResult.MANUAL_REVIEW;
        }

        return ReconcileResult.CONSISTENT;
    }

    private void enqueueReprovisionJob(String assetId) {
        try {
            String jobId = idGenerator.generate(IdPrefix.JOB);
            Instant now = Instant.now();
            String payload = "{\"assetId\":\"" + assetId + "\",\"source\":\"reconcile\"}";
            Job job = new Job(null, jobId, "REPOSITORY_PROVISION", payload,
                    JobStatus.PENDING, 5, 0, now, null, null, null, "system",
                    assetId, null, now, now, 0);
            jobRepository.insert(job);
            LOG.info("enqueued REPOSITORY_PROVISION for assetId={} jobId={}", assetId, jobId);
        } catch (Exception ex) {
            LOG.error("failed to enqueue reprovision job for assetId={}", assetId, ex);
        }
    }

    private void recordDiscrepancy(String assetId, ReconcileResult result) {
        LOG.warn("reconciliation discrepancy assetId={} classification={}", assetId, result);
        if (result == ReconcileResult.SECURITY_INCIDENT) {
            LOG.error("SECURITY INCIDENT: asset {} has critical inconsistency", assetId);
        }
        platformMetrics.recordReconciliationDiscrepancy("AssetRepository", result.name());
        try {
            String deliveryId = "reconcile-" + assetId + "-" + System.currentTimeMillis();
            jdbcTemplate.update(
                    "INSERT INTO webhook_inbox (delivery_id, event_type, source, signature_valid, payload, status) " +
                            "VALUES (?, 'RECONCILE_DISCREPANCY', 'internal', true, " +
                            "jsonb_build_object('assetId', ?, 'classification', ?::text), 'COMPLETED') " +
                            "ON CONFLICT (delivery_id) DO NOTHING",
                    deliveryId, assetId, result.name());
        } catch (Exception e) {
            LOG.error("failed to record reconciliation discrepancy for assetId={}", assetId, e);
        }
    }

    enum ReconcileResult {
        CONSISTENT,
        AUTO_REPAIR,
        MANUAL_REVIEW,
        SECURITY_INCIDENT
    }
}
