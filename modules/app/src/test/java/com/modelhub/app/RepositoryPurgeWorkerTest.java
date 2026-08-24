package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.modelhub.catalog.domain.RepositoryEntity;
import com.modelhub.catalog.service.GiteaClient;
import com.modelhub.catalog.service.GiteaClient.GiteaRepo;
import com.modelhub.catalog.worker.RepositoryPurgeWorker;
import com.modelhub.identity.domain.NamespaceEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 仓库 Purge Worker（05 §9.2 第 4-5 步）：retention_until 到期 → purging →
 * Gitea 物理删除 + 对象引用释放（跨仓库共享 blob 保留）→ purged；
 * purged 不可恢复；重复执行幂等。
 */
class RepositoryPurgeWorkerTest extends ArtifactTestSupport {

    @Autowired
    private RepositoryPurgeWorker purgeWorker;

    @Autowired
    private GiteaClient gitea;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void purgeAfterRetentionDeletesGiteaReleasesObjectsAndBlocksRestore() throws Exception {
        // 两个独立用户的 active 仓库（真实 Gitea provisioning），共享同一 object blob
        Session owner = newUser("purgea");
        String repoId = createActiveRepo(owner);
        UUID publicId = UUID.fromString(repoId);
        RepositoryEntity repo = awaitEntityStatus(publicId, "active");

        Session other = newUser("purgeb");
        String otherRepoId = createActiveRepo(other);
        RepositoryEntity otherRepo = awaitEntityStatus(UUID.fromString(otherRepoId), "active");

        long blobId = insertAvailableBlob(repo.getNamespaceId());
        insertActiveFileVersion(repo.getId(), "purge/seed.bin", blobId);
        insertActiveFileVersion(otherRepo.getId(), "keep/seed.bin", blobId);

        // admin DELETE（owner 为 namespace 管理员）→ deleted + retentionUntil
        long version = dataNode(repoDetail(owner.accessToken(), repoId)).path("version").asLong();
        assertEquals(202, deleteRepo(owner.accessToken(), repoId,
                UUID.randomUUID().toString(), etagOfVersion(version)).getStatusCode().value());
        RepositoryEntity deleted = awaitEntityStatus(publicId, "deleted");
        assertNotNull(deleted.getRetentionUntil(), "deleted 必须设置 retentionUntil");

        // Gitea 仓库在 deleted 阶段仅 archive 未删除（05 §9.2：purge 前禁止物理删除）
        String nsSlug = namespaces.findBySlugIgnoreCase(owner.username())
                .map(NamespaceEntity::getSlug).orElseThrow();
        GiteaRepo before = gitea.getRepository(nsSlug, deleted.getNormalizedName());
        assertNotNull(before, "deleted 阶段 Gitea 仓库应仍存在（仅归档）");

        // 保留期人为到期 → 两阶段 GC
        jdbc.update("UPDATE repositories SET retention_until = now() - interval '1 second' "
                + "WHERE public_id = ?", publicId);
        purgeWorker.purgeDue();

        assertEquals("purged", awaitEntityStatus(publicId, "purged").getLifecycleStatus());
        assertNull(gitea.getRepository(nsSlug, deleted.getNormalizedName()), "purge 后 Gitea 仓库应物理删除");

        // 对象引用释放：本仓库 fv 全量 deleted；共享 blob 仍被另一仓库引用 → ref 1、保持 available
        assertEquals("deleted", jdbc.queryForObject(
                "SELECT status FROM file_versions WHERE repository_id = ? AND path = 'purge/seed.bin'",
                String.class, repo.getId()));
        assertEquals(1, jdbc.queryForObject(
                "SELECT ref_count FROM object_blobs WHERE id = ?", Integer.class, blobId));
        assertEquals("available", jdbc.queryForObject(
                "SELECT status FROM object_blobs WHERE id = ?", String.class, blobId));
        assertEquals("active", jdbc.queryForObject(
                "SELECT status FROM file_versions WHERE repository_id = ? AND path = 'keep/seed.bin'",
                String.class, otherRepo.getId()));

        // purged 不可恢复（05 §9.2：仅 deleted 且保留期内可恢复）
        assertEquals(409, restoreRepo(owner.accessToken(), repoId,
                UUID.randomUUID().toString()).getStatusCode().value());

        // 幂等：重跑无新副作用（purging 候选已收口，状态保持 purged）
        purgeWorker.purgeDue();
        assertTrue(repositoryRepo.findByPublicId(publicId)
                .map(r -> "purged".equals(r.getLifecycleStatus())).orElse(false));
    }

    private long insertAvailableBlob(Long namespaceId) {
        String sha = (UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "")).substring(0, 64);
        jdbc.update("INSERT INTO object_blobs (public_id, namespace_id, sha256, size_bytes, bucket, "
                        + "object_key, content_type, scan_status, scan_policy_version, status, ref_count, created_at) "
                        + "VALUES (?, ?, ?, 128, 'artifacts', ?, 'application/octet-stream', 'clean', 1, "
                        + "'available', 2, now())",
                UUID.randomUUID(), namespaceId, sha, "objects/" + UUID.randomUUID());
        return jdbc.queryForObject("SELECT id FROM object_blobs WHERE sha256 = ?", Long.class, sha);
    }

    private void insertActiveFileVersion(Long repositoryId, String path, long blobId) {
        jdbc.update("INSERT INTO file_versions (public_id, repository_id, branch, path, size_bytes, "
                        + "content_type, content_source, object_blob_id, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'main', ?, 128, 'application/octet-stream', 'object', ?, 'active', now(), now())",
                UUID.randomUUID(), repositoryId, path, blobId);
    }
}
