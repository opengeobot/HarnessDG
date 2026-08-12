package com.modelhub.artifact.service;

import com.modelhub.artifact.domain.FileVersionEntity;
import com.modelhub.artifact.repo.FileVersionRepository;
import com.modelhub.catalog.access.RepositoryAccessFacade;
import com.modelhub.catalog.access.RepositoryAccessFacade.RepoContext;
import com.modelhub.catalog.access.RepositoryAccessFacade.RepoRole;
import com.modelhub.catalog.domain.GitBindingEntity;
import com.modelhub.catalog.domain.JobEntity;
import com.modelhub.catalog.repo.JobRepository;
import com.modelhub.catalog.service.CatalogService.JobView;
import com.modelhub.catalog.service.GiteaClient;
import com.modelhub.catalog.service.OutboxService;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import com.modelhub.shared.id.PublicIds;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 文件删除（04 deleteFile / 05 §6.3）：If-Match 携带 files 清单的 resolvedCommitSha，
 * 与当前分支 head 不一致返回 412；受理后 file_version → deleting + 异步 Git 删除提交。
 */
@Service
public class FilesService {

    private final RepositoryAccessFacade access;
    private final FileVersionRepository fileVersions;
    private final JobRepository jobs;
    private final OutboxService outbox;
    private final GiteaClient gitea;
    private final BrowseService browse;

    public FilesService(RepositoryAccessFacade access, FileVersionRepository fileVersions,
                        JobRepository jobs, OutboxService outbox, GiteaClient gitea, BrowseService browse) {
        this.access = access;
        this.fileVersions = fileVersions;
        this.jobs = jobs;
        this.outbox = outbox;
        this.gitea = gitea;
        this.browse = browse;
    }

    @Transactional
    public JobView deleteFile(CurrentPrincipal actor, UUID repoId, UUID fileId, String ifMatch) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.WRITE);
        FileVersionEntity fv = fileVersions.findByPublicId(fileId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在"));
        if (!fv.getRepositoryId().equals(ctx.repo().getId())) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在");
        }
        if (!"active".equals(fv.getStatus())) {
            // deleting/deleted 重复受理：直接返回既有语义（幂等）
            if ("deleting".equals(fv.getStatus()) || "deleted".equals(fv.getStatus())) {
                JobEntity job = newJob(actor, fv.getPublicId().toString());
                job.setStatus("succeeded");
                job.setUpdatedAt(OffsetDateTime.now());
                jobs.save(job);
                return toJobView(job);
            }
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "文件当前状态不可删除: " + fv.getStatus());
        }
        GitBindingEntity binding = browse.requireBinding(ctx.repo().getId());
        String head = gitea.headCommitSha(binding.getExternalNamespace(), binding.getExternalName(),
                fv.getBranch());
        if (head == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "分支不存在: " + fv.getBranch());
        }
        if (ifMatch == null || ifMatch.isBlank()) {
            throw ApiException.badRequest("缺少 If-Match 头",
                    java.util.List.of(new ApiException.Detail("If-Match", "required")));
        }
        String precondition = stripQuotes(ifMatch.trim());
        if (!precondition.equals(head)) {
            throw new ApiException(ErrorCode.PRECONDITION_FAILED,
                    "If-Match 与当前分支 head 不一致，请刷新文件清单后重试");
        }

        fv.setStatus("deleting");
        fv.setUpdatedAt(OffsetDateTime.now());
        fileVersions.save(fv);
        JobEntity job = newJob(actor, fv.getPublicId().toString());
        outbox.publish("FileDeletionRequested", fv.getPublicId().toString(), 0L, Map.of(
                "fileId", fv.getPublicId().toString(),
                "repositoryId", ctx.repo().getId()));
        return toJobView(job);
    }

    private JobEntity newJob(CurrentPrincipal actor, String aggregateId) {
        OffsetDateTime now = OffsetDateTime.now();
        JobEntity job = new JobEntity();
        job.setPublicId(PublicIds.next());
        job.setJobType("file.delete");
        job.setStatus("queued");
        job.setAggregateType("file");
        job.setAggregateId(aggregateId);
        job.setCreatedBy(actor == null ? null : actor.userId());
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        return jobs.save(job);
    }

    private JobView toJobView(JobEntity job) {
        return new JobView(job.getPublicId(), job.getJobType(), job.getStatus(), job.getCreatedAt());
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}

