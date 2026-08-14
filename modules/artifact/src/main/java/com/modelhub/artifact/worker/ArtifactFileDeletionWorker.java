package com.modelhub.artifact.worker;

import com.fasterxml.jackson.databind.JsonNode;
import com.modelhub.artifact.domain.FileVersionEntity;
import com.modelhub.artifact.domain.ObjectBlobEntity;
import com.modelhub.artifact.repo.FileVersionRepository;
import com.modelhub.artifact.repo.ObjectBlobRepository;
import com.modelhub.artifact.storage.ObjectStorageService;
import com.modelhub.catalog.domain.GitBindingEntity;
import com.modelhub.catalog.repo.GitBindingRepository;
import com.modelhub.catalog.repo.JobRepository;
import com.modelhub.catalog.service.GiteaClient;
import com.modelhub.catalog.service.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文件删除异步链路（05 §6.3）：消费 FileDeletionRequested，执行 Git 删除提交
 * （commit trailer 带 EventId 幂等），成功后 file_version → deleted；
 * object source 同步回收 blob 引用计数，归零后删除对象。
 */
@Component
public class ArtifactFileDeletionWorker {

    private static final Logger log = LoggerFactory.getLogger(ArtifactFileDeletionWorker.class);

    private final FileVersionRepository fileVersions;
    private final ObjectBlobRepository blobs;
    private final GitBindingRepository gitBindings;
    private final JobRepository jobs;
    private final GiteaClient gitea;
    private final ObjectStorageService storage;
    private final OutboxService outbox;
    private final TransactionTemplate tx;

    public ArtifactFileDeletionWorker(FileVersionRepository fileVersions, ObjectBlobRepository blobs,
                                      GitBindingRepository gitBindings, JobRepository jobs,
                                      GiteaClient gitea, ObjectStorageService storage,
                                      OutboxService outbox, TransactionTemplate tx) {
        this.fileVersions = fileVersions;
        this.blobs = blobs;
        this.gitBindings = gitBindings;
        this.jobs = jobs;
        this.gitea = gitea;
        this.storage = storage;
        this.outbox = outbox;
        this.tx = tx;
    }

    public void handleDeletion(JsonNode payload) {
        String id = payload.path("fileId").asText(null);
        if (id == null) {
            return;
        }
        UUID fileId = UUID.fromString(id);
        FileVersionEntity fv = fileVersions.findByPublicId(fileId).orElse(null);
        if (fv == null || !"deleting".equals(fv.getStatus())) {
            return; // 幂等：已删除或非删除中状态直接忽略（至少一次投递）
        }
        updateJob(fileId, "running", null);
        GitBindingEntity binding = gitBindings.findByRepositoryId(fv.getRepositoryId()).orElse(null);
        if (binding == null) {
            fail(fileId, fv, "Git 绑定未就绪，无法执行删除提交");
            return;
        }
        try {
            String message = "Delete " + fv.getPath()
                    + "\n\nEventId: FileDeletionRequested/" + fileId;
            gitea.deleteFile(binding.getExternalNamespace(), binding.getExternalName(),
                    fv.getPath(), message, fv.getBranch());
        } catch (Exception e) {
            log.error("Git 删除提交失败 fileId={}: {}", fileId, e.getMessage());
            fail(fileId, fv, "Git 删除提交失败: " + e.getMessage());
            return;
        }
        // 对象回收：object source 递减引用计数，归零删除对象（05 §7 去重回收）
        String objectKeyToDelete = null;
        if ("object".equals(fv.getContentSource()) && fv.getObjectBlobId() != null) {
            ObjectBlobEntity blob = blobs.findById(fv.getObjectBlobId()).orElse(null);
            if (blob != null) {
                blob.setRefCount(Math.max(0, blob.getRefCount() - 1));
                if (blob.getRefCount() == 0) {
                    blob.setStatus("deleted");
                    objectKeyToDelete = blob.getObjectKey();
                }
                blobs.save(blob);
            }
        }
        if (objectKeyToDelete != null) {
            storage.deleteObject(objectKeyToDelete);
        }
        tx.executeWithoutResult(t -> {
            FileVersionEntity locked = fileVersions.findById(fv.getId()).orElse(null);
            if (locked == null || !"deleting".equals(locked.getStatus())) {
                return;
            }
            locked.setStatus("deleted");
            locked.setUpdatedAt(OffsetDateTime.now());
            fileVersions.save(locked);
            // file_count 重算触发（06 §7.1）：与状态变更同事务
            outbox.publish("FileCountChanged", String.valueOf(fv.getRepositoryId()), 0L,
                    Map.of("repositoryId", fv.getRepositoryId()));
        });
        updateJob(fileId, "succeeded", null);
        log.info("文件已删除 fileId={} path={}", fileId, fv.getPath());
    }

    private void fail(UUID fileId, FileVersionEntity fv, String error) {
        tx.executeWithoutResult(t -> {
            FileVersionEntity locked = fileVersions.findById(fv.getId()).orElse(null);
            if (locked != null && "deleting".equals(locked.getStatus())) {
                locked.setStatus("sync_error");
                locked.setUpdatedAt(OffsetDateTime.now());
                fileVersions.save(locked);
            }
        });
        updateJob(fileId, "failed", error);
    }

    private void updateJob(UUID fileId, String status, String error) {
        try {
            tx.executeWithoutResult(t -> jobs
                    .findFirstByAggregateTypeAndAggregateIdAndStatusInOrderByCreatedAtDesc(
                            "file", fileId.toString(), List.of("queued", "running"))
                    .ifPresent(job -> {
                        job.setStatus(status);
                        job.setErrorMessage(error);
                        job.setUpdatedAt(OffsetDateTime.now());
                        jobs.save(job);
                    }));
        } catch (Exception e) {
            log.warn("Job 状态回写失败 fileId={}: {}", fileId, e.getMessage());
        }
    }
}
