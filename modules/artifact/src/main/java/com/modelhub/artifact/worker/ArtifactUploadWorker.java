package com.modelhub.artifact.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.modelhub.artifact.config.ArtifactProperties;
import com.modelhub.artifact.domain.FileVersionEntity;
import com.modelhub.artifact.domain.ObjectBlobEntity;
import com.modelhub.artifact.domain.UploadSessionEntity;
import com.modelhub.artifact.repo.FileVersionRepository;
import com.modelhub.artifact.repo.ObjectBlobRepository;
import com.modelhub.artifact.repo.UploadSessionRepository;
import com.modelhub.artifact.scan.ContentScanner;
import com.modelhub.artifact.storage.ObjectStorageService;
import com.modelhub.artifact.storage.ObjectStorageService.ProviderPart;
import com.modelhub.catalog.domain.GitBindingEntity;
import com.modelhub.catalog.domain.JobEntity;
import com.modelhub.catalog.domain.RepositoryEntity;
import com.modelhub.catalog.repo.GitBindingRepository;
import com.modelhub.catalog.repo.JobRepository;
import com.modelhub.catalog.repo.RepositoryRepository;
import com.modelhub.catalog.service.GiteaClient;
import com.modelhub.catalog.service.OutboxService;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import com.modelhub.shared.id.PublicIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 上传异步链路 Worker（05 §6）：消费 Outbox 事件，驱动
 * initiated→uploading→verifying→scanning→committing→completed 状态机。
 * 全部处理器幂等（至少一次投递）：先查状态再推进，重放不产生副作用（05 §10.2）。
 * Git CAS 以 baseCommitSha 对分支 head 比对（05 §6.3/§6.4）。
 */
@Component
public class ArtifactUploadWorker {

    private static final Logger log = LoggerFactory.getLogger(ArtifactUploadWorker.class);

    private final UploadSessionRepository sessions;
    private final FileVersionRepository fileVersions;
    private final ObjectBlobRepository blobs;
    private final RepositoryRepository repositories;
    private final GitBindingRepository gitBindings;
    private final JobRepository jobs;
    private final OutboxService outbox;
    private final GiteaClient gitea;
    private final ObjectStorageService storage;
    private final ContentScanner scanner;
    private final ArtifactProperties props;
    private final TransactionTemplate tx;

    public ArtifactUploadWorker(UploadSessionRepository sessions, FileVersionRepository fileVersions,
                                ObjectBlobRepository blobs, RepositoryRepository repositories,
                                GitBindingRepository gitBindings, JobRepository jobs, OutboxService outbox,
                                GiteaClient gitea, ObjectStorageService storage, ContentScanner scanner,
                                ArtifactProperties props, TransactionTemplate tx) {
        this.sessions = sessions;
        this.fileVersions = fileVersions;
        this.blobs = blobs;
        this.repositories = repositories;
        this.gitBindings = gitBindings;
        this.jobs = jobs;
        this.outbox = outbox;
        this.gitea = gitea;
        this.storage = storage;
        this.scanner = scanner;
        this.props = props;
        this.tx = tx;
    }

    // ---------- UploadInitializationRequested（05 §6.1 第 6 步） ----------

    public void handleInitialization(JsonNode payload) {
        UploadSessionEntity s = findSession(payload);
        if (s == null || !"initiated".equals(s.getStatus())) {
            return;
        }
        updateJob(s.getPublicId(), "running", null);
        // 幂等初始化：DB 回填失败后重放会认领已有 Multipart（05 §6.1 第 7 条）
        String providerUploadId = storage.initMultipart(s.getObjectKey(), s.getContentType());
        tx.executeWithoutResult(t -> {
            UploadSessionEntity locked = sessions.findByIdForUpdate(s.getId()).orElse(null);
            if (locked == null || !"initiated".equals(locked.getStatus())) {
                return;
            }
            locked.setProviderUploadId(providerUploadId);
            locked.setStatus("uploading");
            locked.setUpdatedAt(OffsetDateTime.now());
            sessions.save(locked);
        });
        log.info("Multipart 已初始化 uploadId={} providerUploadId={}", s.getPublicId(), providerUploadId);
    }

    // ---------- UploadVerificationRequested（05 §6.3 第 3-7 步） ----------

    public void handleVerification(JsonNode payload) {
        UploadSessionEntity s = findSession(payload);
        if (s == null) {
            return;
        }
        String status = s.getStatus();
        if (Set.of("completed", "conflict", "failed", "aborted", "expired").contains(status)) {
            return;
        }
        if (!Set.of("verifying", "scanning", "committing").contains(status)) {
            log.warn("verification 事件到达但状态非法 uploadId={} status={}", s.getPublicId(), status);
            return;
        }
        updateJob(s.getPublicId(), "running", null);

        if ("verifying".equals(status)) {
            verify(s);
        }
        if ("scanning".equals(sessions.findByPublicId(s.getPublicId()).orElseThrow().getStatus())) {
            scan(s.getPublicId());
        }
    }

    /** ListParts 真实校验 + complete Multipart + 流式 SHA-256（05 §6.3 第 3-5 步）。 */
    private void verify(UploadSessionEntity s) {
        if (s.getProviderUploadId() == null) {
            // 极端恢复：初始化回填失败但 complete 已到达，按稳定 key 重新认领
            s.setProviderUploadId(storage.initMultipart(s.getObjectKey(), s.getContentType()));
        }
        List<ProviderPart> parts = storage.listParts(s.getObjectKey(), s.getProviderUploadId());
        long total = parts.stream().mapToLong(ProviderPart::sizeBytes).sum();
        if (total != s.getSizeBytes()) {
            failSession(s.getPublicId(), "已上传总大小 " + total + " 与声明 " + s.getSizeBytes() + " 不一致");
            return;
        }
        storage.completeMultipart(s.getObjectKey(), s.getProviderUploadId(), parts);
        // 禁止 Multipart ETag 当 SHA-256：完整对象流式哈希（05 §6.3 第 4-5 步）
        String sha = storage.computeSha256(s.getObjectKey());
        if (s.getClaimedSha256() != null && !s.getClaimedSha256().equalsIgnoreCase(sha)) {
            failSession(s.getPublicId(), "SHA-256 校验失败: claimed=" + s.getClaimedSha256() + " actual=" + sha);
            return;
        }
        tx.executeWithoutResult(t -> {
            UploadSessionEntity locked = sessions.findByIdForUpdate(s.getId()).orElseThrow();
            if (!"verifying".equals(locked.getStatus())) {
                return;
            }
            locked.setVerifiedSha256(sha);
            locked.setStatus("scanning");
            locked.setUpdatedAt(OffsetDateTime.now());
            sessions.save(locked);
        });
        log.info("对象校验完成 uploadId={} sha256={}", s.getPublicId(), sha);
    }

    /** 内容扫描：clean → committing + Outbox；rejected → 隔离失败；error → 抛异常重试（fail closed，05 §6.3 第 6-7 步）。 */
    private void scan(UUID uploadId) {
        UploadSessionEntity s = sessions.findByPublicId(uploadId).orElseThrow();
        ContentScanner.ScanResult result = scanner.scan(s.getObjectKey(), s.getSizeBytes());
        if ("rejected".equals(result.status())) {
            tx.executeWithoutResult(t -> {
                if ("object".equals(s.getContentSource())) {
                    ensureBlob(s, true, result.policyVersion());
                }
                UploadSessionEntity locked = sessions.findByIdForUpdate(s.getId()).orElseThrow();
                locked.setStatus("failed");
                locked.setLastError("内容扫描未通过（scan rejected, policyVersion=" + result.policyVersion() + "）");
                locked.setUpdatedAt(OffsetDateTime.now());
                sessions.save(locked);
            });
            updateJob(uploadId, "failed", "content rejected");
            log.warn("内容扫描拒绝 uploadId={}", uploadId);
            return;
        }
        // clean：进入发布分支；对象保持隔离直至事务内转 available（05 §6.3 第 8 步）
        tx.executeWithoutResult(t -> {
            UploadSessionEntity locked = sessions.findByIdForUpdate(s.getId()).orElseThrow();
            if (!"scanning".equals(locked.getStatus())) {
                return;
            }
            locked.setStatus("committing");
            locked.setUpdatedAt(OffsetDateTime.now());
            sessions.save(locked);
            outbox.publish("UploadCommitRequested", uploadId.toString(), 0L,
                    Map.of("uploadId", uploadId.toString()));
        });
    }

    // ---------- UploadCommitRequested（05 §6.3 第 8-11 步） ----------

    public void handleCommit(JsonNode payload) {
        UploadSessionEntity s = findSession(payload);
        if (s == null) {
            return;
        }
        if (!"committing".equals(s.getStatus())) {
            return;
        }
        updateJob(s.getPublicId(), "running", null);

        GitBindingEntity binding = gitBindings.findByRepositoryId(s.getRepositoryId())
                .orElseThrow(() -> new IllegalStateException("Git 绑定缺失 repositoryId=" + s.getRepositoryId()));
        String org = binding.getExternalNamespace();
        String repo = binding.getExternalName();

        // baseCommitSha 对分支 head 做 compare-and-swap（05 §6.3 第 10 步）
        String head = gitea.headCommitSha(org, repo, s.getBranch());
        if (head == null) {
            throw new IllegalStateException("分支不存在或 Gitea 不可达: " + s.getBranch());
        }
        if (!head.equalsIgnoreCase(s.getBaseCommitSha())) {
            markConflict(s, head);
            return;
        }

        GiteaClient.PutFileResult committed;
        if ("git".equals(s.getContentSource())) {
            // git source：从临时 staging 对象读已扫描文本写入 Gitea（05 §6.3 第 9 步）
            byte[] content = storage.getObjectBytes(s.getObjectKey());
            if (content.length > props.getGitSourceMaxBytes()) {
                failSession(s.getPublicId(), "git source 内容超出策略上限");
                return;
            }
            String message = "Upload " + s.getPath() + "\n\nEventId: UploadCommitRequested/" + s.getPublicId();
            committed = gitea.putFileBase64(org, repo, s.getPath(),
                    Base64.getEncoder().encodeToString(content), message, s.getBranch());
        } else {
            // object source：Gitea 只保存指针 manifest（05 §1/§3）
            ObjectBlobEntity blob = tx.execute(t -> ensureBlob(s, false, 1));
            String manifest = pointerManifest(blob, s);
            String message = "Upload " + s.getPath() + " (object pointer)"
                    + "\n\nEventId: UploadCommitRequested/" + s.getPublicId();
            committed = gitea.putFileBase64(org, repo, s.getPath(),
                    Base64.getEncoder().encodeToString(manifest.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                    message, s.getBranch());
        }

        // Git 提交成功 → 同一事务：file_version active + 会话 completed + FileVersionPublished（05 §6.3 第 11 步）
        final GiteaClient.PutFileResult fc = committed;
        tx.executeWithoutResult(t -> {
            UploadSessionEntity locked = sessions.findByIdForUpdate(s.getId()).orElseThrow();
            if (!"committing".equals(locked.getStatus())) {
                return;
            }
            FileVersionEntity headVersion = fileVersions
                    .findByRepositoryIdAndBranchAndPathAndStatusIn(s.getRepositoryId(), s.getBranch(), s.getPath(),
                            List.of("staging", "active"))
                    .orElse(null);
            if (headVersion != null) {
                // 旧版本标记 deleted，历史 commit 仍可引用（05 §9.1）
                headVersion.setStatus("deleted");
                headVersion.setUpdatedAt(OffsetDateTime.now());
                fileVersions.save(headVersion);
            }
            FileVersionEntity fv = new FileVersionEntity();
            fv.setPublicId(PublicIds.next());
            fv.setRepositoryId(s.getRepositoryId());
            fv.setBranch(s.getBranch());
            fv.setPath(s.getPath());
            fv.setSizeBytes(s.getSizeBytes());
            fv.setContentType(s.getContentType());
            fv.setContentSource(s.getContentSource());
            if ("git".equals(s.getContentSource())) {
                // 二选一约束：git 只有 git_blob_sha，绝不保留 object_blob_id（05 §6.3）
                fv.setGitBlobSha(fc.fileSha());
            } else {
                // 二选一约束：object 只有 object_blob_id，绝不保留 git_blob_sha（05 §6.3）；
                // blob 已在扫描 clean 后创建/复用，这里直接按租户去重键定位
                ObjectBlobEntity blob = blobs.findByNamespaceIdAndSha256(
                        namespaceIdOf(s), locked.getVerifiedSha256()).orElseThrow();
                fv.setObjectBlobId(blob.getId());
            }
            fv.setCommitSha(fc.commitSha());
            fv.setStatus("active");
            fv.setUploadSessionId(s.getId());
            fv.setCreatedByUserId(s.getCreatedByUserId());
            fv.setCreatedAt(OffsetDateTime.now());
            fv.setUpdatedAt(OffsetDateTime.now());
            fileVersions.save(fv);

            locked.setFileVersionId(fv.getId());
            locked.setStatus("completed");
            locked.setUpdatedAt(OffsetDateTime.now());
            sessions.save(locked);
            outbox.publish("FileVersionPublished", fv.getPublicId().toString(), 0L, Map.of(
                    "fileVersionId", fv.getPublicId().toString(),
                    "repositoryId", s.getRepositoryId(),
                    "commitSha", fc.commitSha() == null ? "" : fc.commitSha()));
            // file_count 重算触发（06 §7.1）：与发布同事务，消费者幂等重算
            outbox.publish("FileCountChanged", s.getRepositoryId().toString(), 0L,
                    Map.of("repositoryId", s.getRepositoryId()));
        });
        if ("git".equals(s.getContentSource())) {
            // git source 的临时 MinIO 对象进入回收（v1 直接删除，05 §6.3 第 11 步）
            storage.deleteObject(s.getObjectKey());
        }
        updateJob(s.getPublicId(), "succeeded", null);
        log.info("上传发布完成 uploadId={} commit={}", s.getPublicId(),
                committed == null ? null : committed.commitSha());
    }

    // ---------- UploadAbortRequested（05 §6.3 abort） ----------

    public void handleAbort(JsonNode payload) {
        UploadSessionEntity s = findSession(payload);
        if (s == null || !"aborting".equals(s.getStatus())) {
            return;
        }
        updateJob(s.getPublicId(), "running", null);
        if (s.getProviderUploadId() != null) {
            storage.abortMultipart(s.getObjectKey(), s.getProviderUploadId());
        }
        // 未发布对象一律回收（object source 尚未生成 blob 引用时同样只是 staging）
        boolean hasBlobRef = "object".equals(s.getContentSource())
                && Set.of("completed").contains(s.getStatus());
        if (!hasBlobRef) {
            storage.deleteObject(s.getObjectKey());
        }
        tx.executeWithoutResult(t -> {
            UploadSessionEntity locked = sessions.findByIdForUpdate(s.getId()).orElseThrow();
            if (!"aborting".equals(locked.getStatus())) {
                return;
            }
            locked.setStatus("aborted");
            locked.setUpdatedAt(OffsetDateTime.now());
            sessions.save(locked);
        });
        updateJob(s.getPublicId(), "succeeded", null);
        log.info("上传已终止 uploadId={}", s.getPublicId());
    }

    // ---------- 内部辅助 ----------

    private UploadSessionEntity findSession(JsonNode payload) {
        String id = payload.path("uploadId").asText(null);
        if (id == null) {
            return null;
        }
        return sessions.findByPublicId(UUID.fromString(id)).orElse(null);
    }

    private void markConflict(UploadSessionEntity s, String currentHead) {
        tx.executeWithoutResult(t -> {
            UploadSessionEntity locked = sessions.findByIdForUpdate(s.getId()).orElseThrow();
            if (!"committing".equals(locked.getStatus())) {
                return;
            }
            // 分支 head 变更：保留已验证对象，等待客户端 :publish（05 §6.4）
            locked.setStatus("conflict");
            locked.setCurrentBranchHead(currentHead);
            locked.setLastError("分支 head 已变更，需 :publish 重新发布");
            locked.setUpdatedAt(OffsetDateTime.now());
            sessions.save(locked);
        });
        updateJob(s.getPublicId(), "failed", "branch conflict");
        log.warn("发布冲突 uploadId={} expectedBase={} currentHead={}",
                s.getPublicId(), s.getBaseCommitSha(), currentHead);
    }

    private void failSession(UUID uploadId, String error) {
        tx.executeWithoutResult(t -> {
            UploadSessionEntity s = sessions.findByPublicId(uploadId).orElse(null);
            if (s == null || Set.of("completed", "aborted", "failed").contains(s.getStatus())) {
                return;
            }
            s.setStatus("failed");
            s.setLastError(error);
            s.setUpdatedAt(OffsetDateTime.now());
            sessions.save(s);
        });
        updateJob(uploadId, "failed", error);
        log.error("上传失败 uploadId={}: {}", uploadId, error);
    }

    /** object source 的 blob 创建/复用（租户内 sha256 去重，05 §7）。rejected=true 时建隔离记录。 */
    private ObjectBlobEntity ensureBlob(UploadSessionEntity s, boolean rejected, int policyVersion) {
        Long nsId = namespaceIdOf(s);
        String sha = s.getVerifiedSha256();
        Optional<ObjectBlobEntity> existing = sha == null
                ? Optional.empty() : blobs.findByNamespaceIdAndSha256(nsId, sha);
        if (existing.isPresent() && !rejected) {
            ObjectBlobEntity blob = existing.get();
            blob.setRefCount(blob.getRefCount() + 1);
            return blobs.save(blob);
        }
        ObjectBlobEntity blob = new ObjectBlobEntity();
        blob.setPublicId(PublicIds.next());
        blob.setNamespaceId(nsId);
        blob.setSha256(sha == null ? "" : sha);
        blob.setSizeBytes(s.getSizeBytes());
        blob.setBucket(props.getBucket());
        blob.setObjectKey(s.getObjectKey());
        blob.setContentType(s.getContentType());
        blob.setScanStatus(rejected ? "rejected" : "clean");
        blob.setScanPolicyVersion(policyVersion);
        blob.setStatus("quarantined");
        blob.setRefCount(rejected ? 0 : 1);
        blob.setCreatedAt(OffsetDateTime.now());
        blob = blobs.save(blob);
        if (!rejected) {
            // 扫描 clean 后在同一事务转 available（05 §6.3 第 8 步）
            blob.setStatus("available");
            blob = blobs.save(blob);
        }
        return blob;
    }

    private Long namespaceIdOf(UploadSessionEntity s) {
        RepositoryEntity repo = repositories.findById(s.getRepositoryId()).orElseThrow(
                () -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "仓库不存在"));
        return repo.getNamespaceId();
    }

    private String pointerManifest(ObjectBlobEntity blob, UploadSessionEntity s) {
        return "{\"version\":1,\"modelhub\":{"
                + "\"objectId\":\"" + blob.getPublicId() + "\","
                + "\"sha256\":\"" + s.getVerifiedSha256() + "\","
                + "\"sizeBytes\":" + s.getSizeBytes() + ","
                + "\"bucket\":\"" + blob.getBucket() + "\","
                + "\"objectKey\":\"" + blob.getObjectKey() + "\"}}";
    }

    private void updateJob(UUID uploadId, String status, String error) {
        try {
            tx.executeWithoutResult(t -> jobs
                    .findFirstByAggregateTypeAndAggregateIdAndStatusInOrderByCreatedAtDesc(
                            "upload", uploadId.toString(), List.of("queued", "running"))
                    .ifPresent(job -> {
                        job.setStatus(status);
                        job.setErrorMessage(error);
                        job.setUpdatedAt(OffsetDateTime.now());
                        jobs.save(job);
                    }));
        } catch (Exception e) {
            log.warn("Job 状态回写失败 uploadId={}: {}", uploadId, e.getMessage());
        }
    }
}
