package com.modelhub.artifact.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modelhub.artifact.config.ArtifactProperties;
import com.modelhub.artifact.storage.ObjectStorageService;
import com.modelhub.catalog.access.RepositoryAccessFacade;
import com.modelhub.catalog.access.RepositoryAccessFacade.RepoContext;
import com.modelhub.catalog.access.RepositoryAccessFacade.RepoRole;
import com.modelhub.catalog.domain.GitBindingEntity;
import com.modelhub.catalog.domain.JobEntity;
import com.modelhub.catalog.domain.PreviewArtifactEntity;
import com.modelhub.catalog.domain.RepositoryEntity;
import com.modelhub.catalog.repo.GitBindingRepository;
import com.modelhub.catalog.repo.JobRepository;
import com.modelhub.catalog.repo.PreviewArtifactRepository;
import com.modelhub.catalog.service.GiteaClient;
import com.modelhub.catalog.service.JobEventService;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import com.modelhub.shared.id.PublicIds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 预览链路（05 §5 / 04 Preview）：
 * - 触发幂等：同仓库同 commit 至多一个活跃 preview.generate 任务（05 §5.2）；
 * - 产物按 (repo, ref) 版本化，最新行的 job_id 拥有写入权（迟到旧任务不得覆盖新预览）；
 * - GET 语义：ready → 200 Preview（head 已前移时投影为 stale，不自动重触发）；
 *   pending/running → 202 JobEnvelope；无产物无活跃任务 → 404。
 */
@Service
public class PreviewService {

    private static final Logger log = LoggerFactory.getLogger(PreviewService.class);
    private static final Pattern SHA40 = Pattern.compile("^[0-9a-fA-F]{40}$");
    private static final String JOB_TYPE = "preview.generate";
    private static final Set<String> ACTIVE_JOB_STATUSES = Set.of("queued", "running", "cancel_requested");

    /** 契约 Preview schema（04）：required sourceCommitSha/manifestHash/policyVersion/status。 */
    public record PreviewView(String sourceCommitSha, String manifestHash, int policyVersion, String status,
                              Long rowCount, Long sampleCount, String sampleStrategy, List<Object> sample) {}

    /** 契约 Download schema（04）：required url/expiresAt。 */
    public record DownloadView(String url, OffsetDateTime expiresAt) {}

    /** get() 双态结果：preview 非空 → 200 PreviewEnvelope；job 非空 → 202 JobEnvelope。 */
    public record GetResult(PreviewView preview, Map<String, Object> job) {}

    private final RepositoryAccessFacade access;
    private final PreviewArtifactRepository previews;
    private final JobRepository jobs;
    private final GitBindingRepository gitBindings;
    private final GiteaClient gitea;
    private final ObjectStorageService storage;
    private final JobEventService jobEvents;
    private final ArtifactProperties props;
    private final ObjectMapper objectMapper;

    public PreviewService(RepositoryAccessFacade access, PreviewArtifactRepository previews,
                          JobRepository jobs, GitBindingRepository gitBindings, GiteaClient gitea,
                          ObjectStorageService storage, JobEventService jobEvents,
                          ArtifactProperties props, ObjectMapper objectMapper) {
        this.access = access;
        this.previews = previews;
        this.jobs = jobs;
        this.gitBindings = gitBindings;
        this.gitea = gitea;
        this.storage = storage;
        this.jobEvents = jobEvents;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    // ---------- POST preview-jobs（05 §5.2 触发） ----------

    /** 触发预览生成：READ 授权 + 同 commit 活跃任务幂等复用；返回 Job 视图（202）。 */
    @Transactional
    public Map<String, Object> trigger(CurrentPrincipal actor, UUID repoId, String ref, boolean force) {
        RepoContext ctx = authorizeRead(actor, repoId);
        String refName = resolveRefName(ctx.repo(), ref);
        String commitSha = resolveCommitSha(ctx.repo(), refName, ref);

        if (!force) {
            Map<String, Object> active = activeJobForCommit(ctx.repo(), commitSha);
            if (active != null) {
                log.info("预览任务幂等复用 repo={} commit={} job={}", repoId, commitSha,
                        active.get("id"));
                return active;
            }
        }

        JobEntity job = newJob(actor, ctx.repo(), refName, commitSha);

        // 版本化产物行：新行 version+1，status running；旧行保留（历史版本不覆盖）
        PreviewArtifactEntity latest = previews
                .findFirstByRepositoryIdAndRefNameOrderByVersionDesc(ctx.repo().getId(), refName)
                .orElse(null);
        OffsetDateTime now = OffsetDateTime.now();
        PreviewArtifactEntity row = new PreviewArtifactEntity();
        row.setRepositoryId(ctx.repo().getId());
        row.setJobId(job.getId());
        row.setArtifactKey("previews/" + PublicIds.next());
        row.setVersion(latest == null ? 1 : latest.getVersion() + 1);
        row.setSourceCommitSha(commitSha);
        row.setPolicyVersion(1);
        row.setStatus("running");
        row.setRefName(refName);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        previews.save(row);
        log.info("预览任务已受理 repo={} ref={} commit={} job={} version={}",
                repoId, refName, commitSha, job.getPublicId(), row.getVersion());
        return jobView(job);
    }

    // ---------- GET preview（05 §5.2 只读语义） ----------

    @Transactional(readOnly = true)
    public GetResult get(CurrentPrincipal actor, UUID repoId, String ref) {
        RepoContext ctx = authorizeRead(actor, repoId);
        String refName = resolveRefName(ctx.repo(), ref);
        String head = resolveCommitSha(ctx.repo(), refName, ref);

        PreviewArtifactEntity row = previews
                .findFirstByRepositoryIdAndRefNameOrderByVersionDesc(ctx.repo().getId(), refName)
                .orElse(null);
        if (row == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                    "预览不存在：请先经 preview-jobs 触发生成（ref=" + refName + "）");
        }
        String status = row.getStatus();
        if ("pending".equals(status) || "running".equals(status)) {
            JobEntity job = row.getJobId() == null ? null
                    : jobs.findById(row.getJobId()).orElse(null);
            if (job != null && ACTIVE_JOB_STATUSES.contains(job.getStatus())) {
                return new GetResult(null, jobView(job));
            }
            // 任务已终态但行未收敛：按行状态返回 Preview（schema 允许 pending/running）
            return new GetResult(toView(row, status), null);
        }
        // ready 产物：head 已前移 → 投影 stale（v1 不自动重触发，05 §5.2）
        String effective = "ready".equals(status) && row.getSourceCommitSha() != null
                && head != null && !row.getSourceCommitSha().equalsIgnoreCase(head)
                ? "stale" : status;
        return new GetResult(toView(row, effective), null);
    }

    // ---------- preview/download（短周期预签名 URL） ----------

    @Transactional(readOnly = true)
    public DownloadView download(CurrentPrincipal actor, UUID repoId, String ref) {
        RepoContext ctx = authorizeRead(actor, repoId);
        String refName = resolveRefName(ctx.repo(), ref);
        PreviewArtifactEntity row = previews
                .findFirstByRepositoryIdAndRefNameOrderByVersionDesc(ctx.repo().getId(), refName)
                .orElse(null);
        if (row == null || !"ready".equals(row.getStatus()) || row.getArtifactKey() == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND,
                    "预览产物不存在或尚未就绪（ref=" + refName + "）");
        }
        Duration ttl = Duration.ofSeconds(props.getDownloadUrlTtlSeconds());
        String url = storage.presignGetObject(row.getArtifactKey(), ttl);
        return new DownloadView(url, OffsetDateTime.now().plus(ttl));
    }

    // ---------- 内部辅助 ----------

    private RepoContext authorizeRead(CurrentPrincipal actor, UUID repoId) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.READ);
        if (ctx.gatedEnabled() && !ctx.hasActiveGrant()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "该仓库内容受 gated 策略保护，需先获得访问授权");
        }
        return ctx;
    }

    /** ref → 行键：null/blank → 默认分支；40-hex sha 原样作为键（按 ref 字符串定位产物行）。 */
    private String resolveRefName(RepositoryEntity repo, String ref) {
        if (ref == null || ref.isBlank()) {
            return repo.getDefaultBranch();
        }
        return ref.trim();
    }

    /** ref → commit sha：sha 直接使用；分支名经 Gitea head 解析（与 BrowseService.listFiles 同源）。 */
    private String resolveCommitSha(RepositoryEntity repo, String refName, String rawRef) {
        String target = rawRef == null || rawRef.isBlank() ? refName : rawRef.trim();
        if (SHA40.matcher(target).matches()) {
            return target.toLowerCase();
        }
        GitBindingEntity binding = gitBindings.findByRepositoryId(repo.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "仓库 Git 绑定未就绪"));
        String head = gitea.headCommitSha(binding.getExternalNamespace(), binding.getExternalName(), target);
        if (head == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "分支不存在: " + target);
        }
        return head.toLowerCase();
    }

    /** 同仓库同 commit 的活跃 preview 任务（05 §5.2：相同版本与策略只能存在一个活跃任务）。 */
    private Map<String, Object> activeJobForCommit(RepositoryEntity repo, String commitSha) {
        List<JobEntity> active = jobs.findByJobTypeAndAggregateIdAndStatusInOrderByCreatedAtDesc(
                JOB_TYPE, repo.getPublicId().toString(), ACTIVE_JOB_STATUSES);
        for (JobEntity job : active) {
            if (commitSha.equalsIgnoreCase(commitShaOf(job))) {
                return jobView(job);
            }
        }
        return null;
    }

    private String commitShaOf(JobEntity job) {
        try {
            JsonNode payload = objectMapper.readTree(job.getPayload() == null ? "{}" : job.getPayload());
            return payload.path("commitSha").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    /** Job 落库（05 §1）：payload 携带 repositoryId/ref/commitSha，created 事件同事务写入。 */
    private JobEntity newJob(CurrentPrincipal actor, RepositoryEntity repo, String refName, String commitSha) {
        OffsetDateTime now = OffsetDateTime.now();
        JobEntity job = new JobEntity();
        job.setPublicId(PublicIds.next());
        job.setJobType(JOB_TYPE);
        job.setStatus("queued");
        job.setAggregateType("repository");
        job.setAggregateId(repo.getPublicId().toString());
        job.setPayload(toJson(Map.of(
                "repositoryId", repo.getId(),
                "ref", refName,
                "commitSha", commitSha)));
        job.setCreatedBy(actor == null ? null : actor.userId());
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        JobEntity saved = jobs.save(job);
        jobEvents.record(saved.getId(), "created", Map.of("status", "queued"));
        return saved;
    }

    /** 契约 Job schema 视图（与 JobsController.view 同形）。 */
    public Map<String, Object> jobView(JobEntity job) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", job.getPublicId().toString());
        data.put("type", job.getJobType());
        data.put("status", job.getStatus());
        data.put("createdAt", job.getCreatedAt() == null ? null : job.getCreatedAt().toString());
        data.put("progressCurrent", job.getProgressCurrent());
        data.put("progressTotal", job.getProgressTotal());
        data.put("progressMessage", job.getProgressMessage());
        data.put("resultSummary", readJson(job.getResultSummary()));
        data.put("errorCode", job.getErrorCode());
        data.put("startedAt", job.getStartedAt() == null ? null : job.getStartedAt().toString());
        data.put("finishedAt", job.getFinishedAt() == null ? null : job.getFinishedAt().toString());
        return data;
    }

    private PreviewView toView(PreviewArtifactEntity row, String status) {
        return new PreviewView(
                row.getSourceCommitSha() == null ? "" : row.getSourceCommitSha(),
                row.getManifestHash() == null ? "" : row.getManifestHash(),
                row.getPolicyVersion(),
                status,
                row.getRowCount(),
                row.getSampleCount(),
                row.getSampleStrategy(),
                parseSample(row.getSample()));
    }

    private List<Object> parseSample(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Object>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private Object readJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("payload 序列化失败", e);
        }
    }
}
