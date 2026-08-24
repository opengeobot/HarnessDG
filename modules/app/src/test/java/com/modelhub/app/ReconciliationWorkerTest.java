package com.modelhub.app;

import com.modelhub.artifact.worker.ReconciliationWorker;
import com.modelhub.artifact.worker.ReconciliationWorker.ReconciliationReport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 存储一致性对账（05 §10.3，只读）：注入四类不一致后，worker 报告计数精确递增；
 * 采用基线-增量断言以隔离共享测试库中其他用例的历史数据。
 */
class ReconciliationWorkerTest extends ArtifactTestSupport {

    @Autowired
    private ReconciliationWorker reconciliation;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void detectsMissingObjectOrphanRefMismatchAndStaleUpload() throws Exception {
        Session owner = newUser("recon");
        String repoId = createActiveRepo(owner);
        Long repoRowId = awaitEntityStatus(UUID.fromString(repoId), "active").getId();
        Long namespaceId = repositoryRepo.findByPublicId(UUID.fromString(repoId)).orElseThrow()
                .getNamespaceId();

        ReconciliationReport baseline = reconciliation.runChecks();

        // 1) active file_version 指向已删除 blob → missingObjects +1；
        //    同时该 blob ref_count(0) 与实际 active 引用(1) 不一致 → refMismatches +1
        long danglingBlob = insertBlob(namespaceId, "deleted", 0, "now()");
        insertFileVersion(repoRowId, "recon/dangling.bin", danglingBlob, "active");
        // 2) 孤儿对象：available + ref_count=0 + 超 1 天宽限 → orphans +1（引用一致，不计 mismatch）
        insertBlob(namespaceId, "available", 0, "now() - interval '2 days'");
        // 3) ref_count 虚高（无任何引用却记 99）→ refMismatches +1（ref!=0 非孤儿）
        long inflatedBlob = insertBlob(namespaceId, "available", 99, "now()");
        // 4) 未终结会话：conflict 非终态且过期超 1 小时（janitor 不处理 conflict，确定性命中）
        jdbc.update("INSERT INTO upload_sessions (public_id, repository_id, branch, base_commit_sha, path, "
                        + "size_bytes, content_type, content_source, status, part_size, max_concurrency, "
                        + "expires_at, object_key, conflict_resolution, created_by_user_id) "
                        + "VALUES (?, ?, 'main', ?, 'recon/stale.bin', 64, 'text/plain', 'git', 'conflict', "
                        + "16777216, 3, now() - interval '2 hours', ?, 'fail_if_path_changed', "
                        + "(SELECT id FROM users ORDER BY id LIMIT 1))",
                UUID.randomUUID(), repoRowId, "0".repeat(40), "objects/" + UUID.randomUUID());

        ReconciliationReport after = reconciliation.runChecks();

        assertEquals(baseline.missingObjects() + 1, after.missingObjects());
        assertEquals(baseline.orphans() + 1, after.orphans());
        assertEquals(baseline.refMismatches() + 2, after.refMismatches());
        assertEquals(baseline.staleUploads() + 1, after.staleUploads());

        // 只读语义：重复运行计数稳定（无自动修复）
        ReconciliationReport again = reconciliation.runChecks();
        assertEquals(after, again);
    }

    private long insertBlob(Long namespaceId, String status, int refCount, String createdAtExpr) {
        String sha = (UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "")).substring(0, 64);
        jdbc.update("INSERT INTO object_blobs (public_id, namespace_id, sha256, size_bytes, bucket, "
                        + "object_key, content_type, scan_status, scan_policy_version, status, ref_count, created_at) "
                        + "VALUES (?, ?, ?, 64, 'artifacts', ?, 'text/plain', 'clean', 1, ?, ?, " + createdAtExpr + ")",
                UUID.randomUUID(), namespaceId, sha, "objects/" + UUID.randomUUID(), status, refCount);
        return jdbc.queryForObject("SELECT id FROM object_blobs WHERE sha256 = ?", Long.class, sha);
    }

    private void insertFileVersion(Long repositoryId, String path, long blobId, String status) {
        jdbc.update("INSERT INTO file_versions (public_id, repository_id, branch, path, size_bytes, "
                        + "content_type, content_source, object_blob_id, status, created_at, updated_at) "
                        + "VALUES (?, ?, 'main', ?, 64, 'text/plain', 'object', ?, ?, now(), now())",
                UUID.randomUUID(), repositoryId, path, blobId, status);
    }
}
