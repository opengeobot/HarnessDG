package com.modelhub.catalog.worker;

import com.modelhub.catalog.domain.RepositoryEntity;
import com.modelhub.catalog.repo.GitBindingRepository;
import com.modelhub.catalog.repo.RepositoryRepository;
import com.modelhub.catalog.service.GiteaClient;
import com.modelhub.identity.repo.NamespaceRepository;
import com.modelhub.identity.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 仓库 Purge Worker（05 §9.2 第 4-5 步，两阶段 GC）：
 * deleted 且 retention_until 到期 → purging → Gitea 物理删除 + 释放对象引用 → purged。
 * purged 后仅保留仓库 tombstone 行与审计（名称已由部分唯一索引释放），从此不可恢复
 * （CatalogService.restore 仅接受 deleted）。
 * 幂等：Gitea 404 视为已删除；对象引用按 (repo, blob) 引用数递减，重复执行无二次副作用。
 * v1 不物理删除 MinIO 对象：file_versions 全量置 deleted，共享 blob ref_count 归零后仅标记
 * pending_delete（两阶段 GC 第一阶段），物理回收交由后续版本/人工流程（05 §10.3 禁止未验证直接删除）。
 * 失败语义：任一外部步骤失败保持 purging，下一轮重试（与 deleting Saga 相同收敛策略）。
 */
@Component
public class RepositoryPurgeWorker {

    private static final Logger log = LoggerFactory.getLogger(RepositoryPurgeWorker.class);

    private final RepositoryRepository repositories;
    private final NamespaceRepository namespaces;
    private final GitBindingRepository gitBindings;
    private final GiteaClient gitea;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final JdbcTemplate jdbc;

    public RepositoryPurgeWorker(RepositoryRepository repositories, NamespaceRepository namespaces,
                                 GitBindingRepository gitBindings, GiteaClient gitea, AuditService audit,
                                 TransactionTemplate tx, JdbcTemplate jdbc) {
        this.repositories = repositories;
        this.namespaces = namespaces;
        this.gitBindings = gitBindings;
        this.gitea = gitea;
        this.audit = audit;
        this.tx = tx;
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "${modelhub.catalog.purge-interval-ms:300000}")
    public void purgeDue() {
        List<RepositoryEntity> candidates = repositories.findPurgeCandidates(OffsetDateTime.now());
        for (RepositoryEntity repo : candidates) {
            try {
                purge(repo.getId());
            } catch (Exception e) {
                // 保持 purging 等待下一轮重试（幂等：purging 候选会被再次扫描）
                log.error("purge 失败 repository={}：{}", repo.getPublicId(), e.getMessage(), e);
            }
        }
    }

    private record PurgeTarget(long repositoryId, String publicId, String nsSlug, String name, boolean bound) {}

    void purge(Long repositoryId) {
        PurgeTarget target = beginPurge(repositoryId);
        if (target == null) {
            return;
        }
        // 阶段 2a：Gitea 物理删除（05 §9.2 第 5 步；deletion Saga 只 archive 未删除）。
        // GiteaClient.deleteRepository 内部将 404 视为已删除（幂等）。
        if (target.bound()) {
            gitea.deleteRepository(target.nsSlug(), target.name());
        }
        // 阶段 2b：对象引用释放（file_versions → deleted；blob ref_count 递减，归零 pending_delete）
        releaseObjectReferences(target.repositoryId());
        // 阶段 3：收口 purged（tombstone：仅保留 repositories 行与审计）
        tx.executeWithoutResult(s -> {
            RepositoryEntity locked = repositories.findByIdForUpdate(target.repositoryId()).orElse(null);
            if (locked == null || !"purging".equals(locked.getLifecycleStatus())) {
                return;
            }
            locked.setLifecycleStatus("purged");
            locked.setUpdatedAt(OffsetDateTime.now());
            repositories.save(locked);
        });
        audit.appendSimple("system", "repository.purge", "repository:" + target.publicId(), "success");
        log.info("仓库已 purge: {}/{} publicId={}", target.nsSlug(), target.name(), target.publicId());
    }

    /** 阶段 1：deleted+到期 → purging（同事务审计）；已在 purging（上轮失败重试）直接放行。 */
    private PurgeTarget beginPurge(Long repositoryId) {
        return tx.execute(s -> {
            RepositoryEntity locked = repositories.findByIdForUpdate(repositoryId).orElse(null);
            if (locked == null) {
                return null;
            }
            String status = locked.getLifecycleStatus();
            OffsetDateTime now = OffsetDateTime.now();
            boolean due = "deleted".equals(status) && locked.getRetentionUntil() != null
                    && locked.getRetentionUntil().isBefore(now);
            boolean retry = "purging".equals(status);
            if (!due && !retry) {
                return null;
            }
            var ns = namespaces.findById(locked.getNamespaceId()).orElse(null);
            if (ns == null) {
                return null;
            }
            if (due) {
                locked.setLifecycleStatus("purging");
                locked.setUpdatedAt(now);
                repositories.save(locked);
                audit.appendSimple("system", "repository.purge",
                        "repository:" + locked.getPublicId(), "accepted");
            }
            return new PurgeTarget(locked.getId(), locked.getPublicId().toString(), ns.getSlug(),
                    locked.getNormalizedName(), gitBindings.findByRepositoryId(locked.getId()).isPresent());
        });
    }

    /**
     * 释放对象引用：镜像 {@code ArtifactFileDeletionWorker} 的 ref_count 维护模式
     * （V7：ref_count 由事务维护）。catalog 模块不依赖 artifact 仓储，且两域共库，
     * 故经 SQL 直写：本仓库 object source 引用逐 blob 抵减，归零标记 pending_delete；
     * 跨仓库共享 blob 保留 available（05 §12：删除共享对象的一个引用不影响其他仓库）。
     */
    private void releaseObjectReferences(long repositoryId) {
        List<Map<String, Object>> refs = jdbc.queryForList(
                "SELECT fv.object_blob_id AS blob_id, count(*) AS cnt FROM file_versions fv "
                        + "WHERE fv.repository_id = ? AND fv.content_source = 'object' "
                        + "AND fv.object_blob_id IS NOT NULL AND fv.status IN ('staging','active','deleting') "
                        + "GROUP BY fv.object_blob_id", repositoryId);
        int released = jdbc.update(
                "UPDATE file_versions SET status = 'deleted', updated_at = now() "
                        + "WHERE repository_id = ? AND status IN ('staging','active','deleting')",
                repositoryId);
        for (Map<String, Object> ref : refs) {
            long blobId = ((Number) ref.get("blob_id")).longValue();
            int cnt = ((Number) ref.get("cnt")).intValue();
            jdbc.update("UPDATE object_blobs SET "
                    + "ref_count = GREATEST(0, ref_count - ?), "
                    + "status = CASE WHEN GREATEST(0, ref_count - ?) = 0 THEN 'pending_delete' ELSE status END "
                    + "WHERE id = ? AND status <> 'deleted'", cnt, cnt, blobId);
        }
        if (released > 0 || !refs.isEmpty()) {
            log.info("对象引用已释放 repositoryId={} fileVersions={} blobs={}",
                    repositoryId, released, refs.size());
        }
    }
}
