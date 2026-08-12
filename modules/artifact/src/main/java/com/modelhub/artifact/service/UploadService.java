package com.modelhub.artifact.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modelhub.artifact.config.ArtifactProperties;
import com.modelhub.artifact.domain.UploadSessionEntity;
import com.modelhub.artifact.repo.FileVersionRepository;
import com.modelhub.artifact.repo.UploadSessionRepository;
import com.modelhub.artifact.storage.ObjectStorageService;
import com.modelhub.catalog.access.RepositoryAccessFacade;
import com.modelhub.catalog.access.RepositoryAccessFacade.RepoContext;
import com.modelhub.catalog.access.RepositoryAccessFacade.RepoRole;
import com.modelhub.catalog.domain.GitBindingEntity;
import com.modelhub.catalog.domain.JobEntity;
import com.modelhub.catalog.domain.RepositoryEntity;
import com.modelhub.catalog.domain.SchemaVersionEntity;
import com.modelhub.catalog.repo.GitBindingRepository;
import com.modelhub.catalog.repo.JobRepository;
import com.modelhub.catalog.repo.RepositoryRepository;
import com.modelhub.catalog.repo.SchemaVersionRepository;
import com.modelhub.catalog.service.CatalogService.JobView;
import com.modelhub.catalog.service.GiteaClient;
import com.modelhub.catalog.service.OutboxService;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 上传会话应用服务（05 §5/§6）：
 * - initiate：授权 + 校验 + 冻结 content_source + 创建 initiated 会话与 Outbox（同事务）；
 * - part-urls：仅 uploading 状态签发短期 URL，expectedSizeBytes 含末片真实长度；
 * - complete/publish/abort：条件状态转换 + Outbox + Job，返回 202 JobEnvelope；
 * 所有外部副作用（MinIO/Gitea）由 Worker 异步执行，请求线程不做 50GiB 级 IO（05 §6.3）。
 */
@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);
    private static final long MIB = 1024L * 1024;
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".md", ".txt", ".rst", ".json", ".yaml", ".yml", ".xml", ".csv", ".tsv",
            ".py", ".java", ".js", ".ts", ".c", ".h", ".cpp", ".sh", ".toml", ".ini", ".cfg");

    // ---------- 契约视图（04 Artifacts schemas） ----------

    public record PartView(int partNumber, long sizeBytes, String providerEtag) {}

    public record UploadView(UUID id, String contentSource, String baseCommitSha, String currentBranchHead,
                             String verifiedSha256, String status, long partSize, int maxConcurrency,
                             List<PartView> uploadedParts, OffsetDateTime expiresAt) {}

    public record PartUrlView(int partNumber, String url, OffsetDateTime expiresAt, long expectedSizeBytes,
                              String checksumAlgorithm, Map<String, String> requiredHeaders) {}

    public record InitiateCmd(String branch, String baseCommitSha, String path, long sizeBytes,
                              String sha256, String contentType) {}

    public record PublishCmd(String baseCommitSha, String conflictResolution, String commitMessage) {}

    private final RepositoryAccessFacade access;
    private final RepositoryRepository repositories;
    private final GitBindingRepository gitBindings;
    private final SchemaVersionRepository schemaVersions;
    private final UploadSessionRepository sessions;
    private final FileVersionRepository fileVersions;
    private final JobRepository jobs;
    private final OutboxService outbox;
    private final GiteaClient gitea;
    private final ObjectStorageService storage;
    private final ArtifactProperties props;
    private final ObjectMapper objectMapper;

    public UploadService(RepositoryAccessFacade access, RepositoryRepository repositories,
                         GitBindingRepository gitBindings, SchemaVersionRepository schemaVersions,
                         UploadSessionRepository sessions, FileVersionRepository fileVersions,
                         JobRepository jobs, OutboxService outbox, GiteaClient gitea,
                         ObjectStorageService storage, ArtifactProperties props, ObjectMapper objectMapper) {
        this.access = access;
        this.repositories = repositories;
        this.gitBindings = gitBindings;
        this.schemaVersions = schemaVersions;
        this.sessions = sessions;
        this.fileVersions = fileVersions;
        this.jobs = jobs;
        this.outbox = outbox;
        this.gitea = gitea;
        this.storage = storage;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    // ---------- initiate（05 §6.1） ----------

    @Transactional
    public UploadView initiate(CurrentPrincipal actor, UUID repoId, InitiateCmd cmd, String idempotencyKey) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.WRITE);
        RepositoryEntity repo = ctx.repo();
        if (!"active".equals(repo.getLifecycleStatus())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "仓库当前状态不允许上传: " + repo.getLifecycleStatus());
        }
        // 幂等重放（04 §10）：同 repository 同键直接返回既有会话
        if (idempotencyKey != null) {
            var existing = sessions.findByRepositoryIdAndIdempotencyKey(repo.getId(), idempotencyKey);
            if (existing.isPresent()) {
                return toView(existing.get(), List.of());
            }
        }

        GitBindingEntity binding = gitBindings.findByRepositoryId(repo.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "仓库 Git 绑定未就绪"));

        String path = normalizePath(cmd.path());
        String branch = cmd.branch() == null ? repo.getDefaultBranch() : cmd.branch();
        String head = gitea.headCommitSha(binding.getExternalNamespace(), binding.getExternalName(), branch);
        if (head == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "分支不存在: " + branch,
                    List.of(new ApiException.Detail("branch", "not found")));
        }
        if (!head.equalsIgnoreCase(cmd.baseCommitSha())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "baseCommitSha 与分支 head 不一致，请先拉取最新文件清单",
                    List.of(new ApiException.Detail("baseCommitSha", "stale")));
        }

        FilePolicy policy = loadFilePolicy(repo);
        if (cmd.sizeBytes() > policy.maxFileSizeBytes()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "文件超过类型策略单文件上限 " + policy.maxFileSizeBytes() + " bytes",
                    List.of(new ApiException.Detail("sizeBytes", "exceeds maxFileSizeBytes")),
                    Map.of(), 413);
        }
        String contentType = cmd.contentType() == null || cmd.contentType().isBlank()
                ? "application/octet-stream" : cmd.contentType().toLowerCase(Locale.ROOT);
        if (!policy.allowedContentTypes().isEmpty() && !policy.allowedContentTypes().contains(contentType)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "内容类型不在类型策略允许列表内: " + contentType,
                    List.of(new ApiException.Detail("contentType", "not allowed")));
        }

        // content_source 由服务端冻结（05 §3）：小文本走 Git，其余走对象存储
        String contentSource = resolveContentSource(path, contentType, cmd.sizeBytes());

        UUID publicId = PublicIds.next();
        UploadSessionEntity s = new UploadSessionEntity();
        s.setPublicId(publicId);
        s.setRepositoryId(repo.getId());
        s.setBranch(branch);
        s.setBaseCommitSha(cmd.baseCommitSha().toLowerCase(Locale.ROOT));
        s.setPath(path);
        s.setSizeBytes(cmd.sizeBytes());
        s.setClaimedSha256(cmd.sha256() == null ? null : cmd.sha256().toLowerCase(Locale.ROOT));
        s.setContentType(contentType);
        s.setContentSource(contentSource);
        s.setStatus("initiated");
        s.setPartSize(computePartSize(cmd.sizeBytes()));
        s.setMaxConcurrency(props.getDefaultMaxConcurrency());
        s.setExpiresAt(OffsetDateTime.now().plusHours(props.getSessionTtlHours()));
        // 对象键服务端生成 objects/{publicId}（05 §4），不依赖未验证的客户端哈希
        s.setObjectKey("objects/" + publicId);
        s.setConflictResolution("fail_if_path_changed");
        s.setCreatedByUserId(actor.userId());
        s.setIdempotencyKey(idempotencyKey);
        s.setCreatedAt(OffsetDateTime.now());
        s.setUpdatedAt(OffsetDateTime.now());
        sessions.save(s);
        outbox.publish("UploadInitializationRequested", publicId.toString(), 0L, Map.of(
                "uploadId", publicId.toString(),
                "repositoryId", repo.getId(),
                "objectKey", s.getObjectKey(),
                "contentType", contentType));
        log.info("上传会话已初始化 uploadId={} repo={} source={} size={}",
                publicId, repo.getPublicId(), contentSource, cmd.sizeBytes());
        return toView(s, List.of());
    }

    // ---------- get（05 §6.2：ListParts 重建真实进度） ----------

    public UploadView getUpload(CurrentPrincipal actor, UUID uploadId) {
        UploadSessionEntity s = requireSession(uploadId);
        authorizeSession(s, actor);
        List<PartView> parts = providerParts(s);
        return toView(s, parts);
    }

    // ---------- part-urls（05 §6.2） ----------

    public List<PartUrlView> partUrls(CurrentPrincipal actor, UUID uploadId, List<Integer> partNumbers) {
        UploadSessionEntity s = requireSession(uploadId);
        authorizeSession(s, actor);
        checkNotExpired(s);
        if (!"uploading".equals(s.getStatus())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "仅 uploading 状态可签发 part URL，当前: " + s.getStatus());
        }
        if (partNumbers == null || partNumbers.isEmpty() || partNumbers.size() > 100) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "partNumbers 批次必须在 1..100 之间");
        }
        long totalParts = (s.getSizeBytes() + s.getPartSize() - 1) / s.getPartSize();
        List<PartUrlView> out = new ArrayList<>();
        Duration ttl = Duration.ofMinutes(props.getPartUrlTtlMinutes());
        for (Integer partNumber : partNumbers) {
            if (partNumber == null || partNumber < 1 || partNumber > totalParts) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "partNumber 超出范围: " + partNumber + "（总分片数 " + totalParts + "）");
            }
            long expected = partNumber == totalParts
                    ? s.getSizeBytes() - (totalParts - 1) * s.getPartSize()
                    : s.getPartSize();
            var signed = storage.presignUploadPart(s.getObjectKey(), s.getProviderUploadId(), partNumber, ttl);
            out.add(new PartUrlView(partNumber, signed.url(), signed.expiresAt(), expected,
                    "sha256", Map.of()));
        }
        return out;
    }

    // ---------- complete（05 §6.3：202 + jobId，Worker 异步校验） ----------

    @Transactional
    public JobView complete(CurrentPrincipal actor, UUID uploadId) {
        UploadSessionEntity s = lockSession(uploadId);
        authorizeSession(s, actor);
        checkNotExpired(s);
        if (!"initiated".equals(s.getStatus()) && !"uploading".equals(s.getStatus())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "当前状态不可 complete: " + s.getStatus());
        }
        s.setStatus("verifying");
        s.setUpdatedAt(OffsetDateTime.now());
        sessions.save(s);
        JobEntity job = newJob(actor, "upload.verification", s.getPublicId().toString());
        outbox.publish("UploadVerificationRequested", s.getPublicId().toString(), 0L,
                Map.of("uploadId", s.getPublicId().toString()));
        return toJobView(job);
    }

    // ---------- publish（05 §6.4：conflict 后免重传发布） ----------

    @Transactional
    public JobView publish(CurrentPrincipal actor, UUID uploadId, PublishCmd cmd) {
        UploadSessionEntity s = lockSession(uploadId);
        authorizeSession(s, actor);
        if (!"conflict".equals(s.getStatus())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "仅 conflict 状态可 publish，当前: " + s.getStatus());
        }
        if (cmd.baseCommitSha() == null || !cmd.baseCommitSha().matches("^[0-9a-fA-F]{40}([0-9a-fA-F]{24})?$")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "baseCommitSha 格式非法");
        }
        s.setBaseCommitSha(cmd.baseCommitSha().toLowerCase(Locale.ROOT));
        if ("overwrite".equals(cmd.conflictResolution())) {
            // overwrite 需要显式提交并通过审计（05 §6.4）
            s.setConflictResolution("overwrite");
            log.info("upload {} publish overwrite 请求 actor={}", uploadId, actor.userId());
        }
        s.setStatus("committing");
        s.setUpdatedAt(OffsetDateTime.now());
        sessions.save(s);
        JobEntity job = newJob(actor, "upload.commit", s.getPublicId().toString());
        outbox.publish("UploadCommitRequested", s.getPublicId().toString(), 0L,
                Map.of("uploadId", s.getPublicId().toString()));
        return toJobView(job);
    }

    // ---------- abort（05 §6.3：重复调用返回同一终态语义） ----------

    @Transactional
    public JobView abort(CurrentPrincipal actor, UUID uploadId) {
        UploadSessionEntity s = lockSession(uploadId);
        authorizeSession(s, actor);
        JobEntity job = newJob(actor, "upload.abort", s.getPublicId().toString());
        String status = s.getStatus();
        if (Set.of("aborted", "completed", "failed", "expired").contains(status)) {
            // 已终态：直接成功语义（05 §6.3 重复调用）
            job.setStatus("succeeded");
            job.setUpdatedAt(OffsetDateTime.now());
            jobs.save(job);
            return toJobView(job);
        }
        if (Set.of("initiated", "uploading", "verifying", "scanning", "conflict").contains(status)) {
            s.setStatus("aborting");
            s.setUpdatedAt(OffsetDateTime.now());
            sessions.save(s);
            jobs.save(job);
            outbox.publish("UploadAbortRequested", s.getPublicId().toString(), 0L,
                    Map.of("uploadId", s.getPublicId().toString()));
            return toJobView(job);
        }
        throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "当前状态不可 abort: " + status);
    }

    // ---------- 内部辅助 ----------

    public UploadSessionEntity requireSession(UUID uploadId) {
        return sessions.findByPublicId(uploadId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "上传会话不存在"));
    }

    private UploadSessionEntity lockSession(UUID uploadId) {
        UploadSessionEntity s = requireSession(uploadId);
        return sessions.findByIdForUpdate(s.getId()).orElse(s);
    }

    /** 会话授权：仓库 WRITE+ 或会话创建者（05 §6.3 校验会话主体）。 */
    private void authorizeSession(UploadSessionEntity s, CurrentPrincipal actor) {
        RepositoryEntity repo = repositories.findById(s.getRepositoryId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "仓库不存在"));
        boolean owner = actor != null && actor.userId().equals(s.getCreatedByUserId());
        RepoContext ctx;
        try {
            ctx = access.authorizeRepo(repo, actor, owner ? RepoRole.READ : RepoRole.WRITE);
        } catch (ApiException e) {
            if (owner && e.code() == ErrorCode.FORBIDDEN) {
                return;
            }
            throw e;
        }
        if (!owner && !ctx.role().atLeast(RepoRole.WRITE)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "权限不足");
        }
    }

    private void checkNotExpired(UploadSessionEntity s) {
        if (s.getExpiresAt().isBefore(OffsetDateTime.now())
                && !Set.of("completed", "aborted", "failed", "expired").contains(s.getStatus())) {
            s.setStatus("expired");
            s.setUpdatedAt(OffsetDateTime.now());
            sessions.save(s);
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION, "上传会话已过期");
        }
    }

    private List<PartView> providerParts(UploadSessionEntity s) {
        if (s.getProviderUploadId() == null) {
            return List.of();
        }
        if (Set.of("initiated", "aborted", "expired", "failed").contains(s.getStatus())) {
            return List.of();
        }
        try {
            return storage.listParts(s.getObjectKey(), s.getProviderUploadId()).stream()
                    .map(p -> new PartView(p.partNumber(), p.sizeBytes(), p.etag()))
                    .toList();
        } catch (Exception e) {
            // ListParts 失败不阻断会话查询，进度以空投影返回（真相源仍是对象存储）
            log.warn("ListParts 失败 uploadId={}: {}", s.getPublicId(), e.getMessage());
            return List.of();
        }
    }

    private UploadView toView(UploadSessionEntity s, List<PartView> parts) {
        return new UploadView(s.getPublicId(), s.getContentSource(), s.getBaseCommitSha(),
                s.getCurrentBranchHead(), s.getVerifiedSha256(), s.getStatus(), s.getPartSize(),
                s.getMaxConcurrency(), parts, s.getExpiresAt());
    }

    private JobEntity newJob(CurrentPrincipal actor, String type, String aggregateId) {
        OffsetDateTime now = OffsetDateTime.now();
        JobEntity job = new JobEntity();
        job.setPublicId(PublicIds.next());
        job.setJobType(type);
        job.setStatus("queued");
        job.setAggregateType("upload");
        job.setAggregateId(aggregateId);
        job.setCreatedBy(actor == null ? null : actor.userId());
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        return jobs.save(job);
    }

    private JobView toJobView(JobEntity job) {
        return new JobView(job.getPublicId(), job.getJobType(), job.getStatus(), job.getCreatedAt());
    }

    /** partSize = roundUpMiB(max(16MiB, ceil(size/10000)))（05 §6.1）。 */
    static long computePartSize(long sizeBytes) {
        long minPart = Math.max(16 * MIB, (sizeBytes + 9999) / 10000);
        return ((minPart + MIB - 1) / MIB) * MIB;
    }

    /** 规范化用户路径：禁止路径穿越与绝对路径（05 §4 用户路径只存 file_versions）。 */
    static String normalizePath(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "path 不能为空");
        }
        String path = raw.replace('\\', '/').trim();
        if (path.startsWith("/") || path.endsWith("/")) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "path 不得以 / 开头或结尾");
        }
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "path 含非法段: " + segment);
            }
        }
        return path;
    }

    /** content_source 冻结判定（05 §3）：小文本 → git；其余 → object。 */
    private String resolveContentSource(String path, String contentType, long sizeBytes) {
        if (sizeBytes > props.getGitSourceMaxBytes()) {
            return "object";
        }
        boolean textLike = contentType.startsWith("text/")
                || contentType.equals("application/json")
                || contentType.equals("application/xml")
                || contentType.equals("application/x-yaml")
                || contentType.equals("application/csv");
        if (!textLike) {
            String lower = path.toLowerCase(Locale.ROOT);
            int dot = lower.lastIndexOf('.');
            String fileName = lower.substring(lower.lastIndexOf('/') + 1);
            textLike = fileName.equals("readme") || fileName.equals("license")
                    || (dot > 0 && TEXT_EXTENSIONS.contains(lower.substring(dot)));
        }
        return textLike ? "git" : "object";
    }

    record FilePolicy(long maxFileSizeBytes, List<String> allowedContentTypes) {}

    private FilePolicy loadFilePolicy(RepositoryEntity repo) {
        try {
            SchemaVersionEntity sv = schemaVersions
                    .findById(new com.modelhub.catalog.domain.SchemaVersionId(
                            repo.getResourceType(), repo.getMetadataSchemaVersion()))
                    .orElse(null);
            if (sv == null || sv.getFilePolicy() == null) {
                return new FilePolicy(50L * 1024 * 1024 * 1024, List.of());
            }
            JsonNode policy = objectMapper.readTree(sv.getFilePolicy());
            long max = policy.path("maxFileSizeBytes").asLong(50L * 1024 * 1024 * 1024);
            List<String> types = new ArrayList<>();
            policy.path("allowedContentTypes").forEach(n -> types.add(n.asText()));
            return new FilePolicy(max, types);
        } catch (Exception e) {
            log.warn("filePolicy 解析失败 type={} version={}: {}",
                    repo.getResourceType(), repo.getMetadataSchemaVersion(), e.getMessage());
            return new FilePolicy(50L * 1024 * 1024 * 1024, List.of());
        }
    }
}
