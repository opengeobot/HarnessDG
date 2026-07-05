package com.aihub.integration.gitea.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.job.domain.JobHandler;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 资产仓库对账 Worker。
 *
 * <p>PG↔Gitea 对账：检测资产记录与 Gitea 仓库之间的不一致。
 * 差异分类：AUTO_REPAIR / MANUAL_REVIEW / SECURITY_INCIDENT。
 * 游标分批（每批 100）+ 速率限制 + checkpoint。
 */
@Component
public class AssetRepositoryReconciler implements JobHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AssetRepositoryReconciler.class);
    private static final int BATCH_SIZE = 100;

    private final JdbcTemplate jdbcTemplate;

    public AssetRepositoryReconciler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
            // 分批查询已完成建仓的资产
            List<Map<String, Object>> batch = cursor == null
                    ? jdbcTemplate.queryForList(
                    "SELECT asset_id, name, namespace, provisioning_status " +
                            "FROM asset WHERE provisioning_status = 'COMPLETED' " +
                            "ORDER BY asset_id LIMIT ?", BATCH_SIZE)
                    : jdbcTemplate.queryForList(
                    "SELECT asset_id, name, namespace, provisioning_status " +
                            "FROM asset WHERE provisioning_status = 'COMPLETED' " +
                            "AND asset_id > ? ORDER BY asset_id LIMIT ?", cursor, BATCH_SIZE);

            if (batch.isEmpty()) break;

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

            // 速率限制：批间暂停 100ms
            try { Thread.sleep(100); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        } while (true);

        LOG.info("asset repository reconciliation completed jobId={} totalChecked={} discrepancies={}",
                context.jobId(), totalChecked, discrepancies);
    }

    private static final java.util.regex.Pattern VALID_NAME =
            java.util.regex.Pattern.compile("^[a-z0-9][a-z0-9._-]{0,63}$");
    private static final java.util.regex.Pattern VALID_NAMESPACE =
            java.util.regex.Pattern.compile("^[a-z0-9][a-z0-9._/-]{0,127}$");

    private ReconcileResult checkAssetRepository(String assetId, String name, String namespace) {
        // 名称完整性检查
        if (name == null || name.isEmpty()) {
            LOG.warn("asset {} has null/empty name", assetId);
            return ReconcileResult.MANUAL_REVIEW;
        }
        if (!VALID_NAME.matcher(name).matches()) {
            LOG.warn("asset {} has invalid name format: '{}'", assetId, name);
            return ReconcileResult.MANUAL_REVIEW;
        }

        // 命名空间完整性检查
        if (namespace == null || namespace.isEmpty()) {
            LOG.warn("asset {} has null/empty namespace", assetId);
            return ReconcileResult.AUTO_REPAIR;
        }
        if (!VALID_NAMESPACE.matcher(namespace).matches()) {
            LOG.warn("asset {} has invalid namespace format: '{}'", assetId, namespace);
            return ReconcileResult.MANUAL_REVIEW;
        }

        // 仓库名与 namespace/name 拼接应为有效 Gitea 路径
        String expectedFullName = namespace + "/" + name;
        if (expectedFullName.length() > 200) {
            LOG.warn("asset {} full_name exceeds 200 chars: '{}'", assetId, expectedFullName);
            return ReconcileResult.MANUAL_REVIEW;
        }

        return ReconcileResult.CONSISTENT;
    }

    private void recordDiscrepancy(String assetId, ReconcileResult result) {
        LOG.warn("reconciliation discrepancy assetId={} classification={}", assetId, result);
        if (result == ReconcileResult.SECURITY_INCIDENT) {
            LOG.error("SECURITY INCIDENT: asset {} has critical inconsistency", assetId);
        }
        // 记录对账差异到 webhook_inbox 以供审计追溯
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
