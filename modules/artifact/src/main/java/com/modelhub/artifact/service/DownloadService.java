package com.modelhub.artifact.service;

import com.modelhub.artifact.config.ArtifactProperties;
import com.modelhub.artifact.domain.DownloadSessionEntity;
import com.modelhub.artifact.domain.FileVersionEntity;
import com.modelhub.artifact.domain.ObjectBlobEntity;
import com.modelhub.artifact.repo.DownloadSessionRepository;
import com.modelhub.artifact.repo.FileVersionRepository;
import com.modelhub.artifact.repo.ObjectBlobRepository;
import com.modelhub.artifact.storage.ObjectStorageService;
import com.modelhub.catalog.access.RepositoryAccessFacade;
import com.modelhub.catalog.access.RepositoryAccessFacade.RepoContext;
import com.modelhub.catalog.access.RepositoryAccessFacade.RepoRole;
import com.modelhub.catalog.domain.GitBindingEntity;
import com.modelhub.catalog.repo.RepositoryRepository;
import com.modelhub.catalog.service.GiteaClient;
import com.modelhub.catalog.service.OutboxService;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.identity.service.AuditService;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import com.modelhub.shared.id.PublicIds;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 下载链路（04 §6.5 / 05 §2/§11）：授权成功后签发短期下载会话；签发即下载计数事实，
 * 以 (repository_id, idempotency_key) 幂等。object source 走 MinIO 预签名 GET；
 * git source 走自建内容端点（Gitea raw 经业务 API 鉴权后流式返回）。
 * scan_status 未 clean 的对象保持隔离，不允许签发下载（05 §11）。
 */
@Service
public class DownloadService {

    /** DownloadSession 契约视图。 */
    public record DownloadSessionView(UUID sessionId, UUID fileId, String url,
                                      OffsetDateTime issuedAt, OffsetDateTime expiresAt) {}

    private final RepositoryAccessFacade access;
    private final RepositoryRepository repositories;
    private final FileVersionRepository fileVersions;
    private final ObjectBlobRepository blobs;
    private final DownloadSessionRepository downloadSessions;
    private final ObjectStorageService storage;
    private final GiteaClient gitea;
    private final BrowseService browse;
    private final ArtifactProperties props;
    private final OutboxService outbox;
    private final AuditService audit;

    public DownloadService(RepositoryAccessFacade access, RepositoryRepository repositories,
                           FileVersionRepository fileVersions, ObjectBlobRepository blobs,
                           DownloadSessionRepository downloadSessions, ObjectStorageService storage,
                           GiteaClient gitea, BrowseService browse, ArtifactProperties props,
                           OutboxService outbox, AuditService audit) {
        this.access = access;
        this.repositories = repositories;
        this.fileVersions = fileVersions;
        this.blobs = blobs;
        this.downloadSessions = downloadSessions;
        this.storage = storage;
        this.gitea = gitea;
        this.browse = browse;
        this.props = props;
        this.outbox = outbox;
        this.audit = audit;
    }

    @Transactional
    public DownloadSessionView createSession(CurrentPrincipal actor, UUID repoId, UUID fileId,
                                             String idempotencyKey) {
        RepoContext ctx = access.authorize(repoId, actor, RepoRole.READ);
        // gated 仓库：无有效 grant 禁止下载（02 §5 第 7 步：授权成功后才访问内容）
        if (ctx.gatedEnabled() && !ctx.hasActiveGrant()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "该仓库内容受 gated 策略保护，需先获得访问授权");
        }
        FileVersionEntity fv = fileVersions.findByPublicId(fileId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在"));
        if (!fv.getRepositoryId().equals(ctx.repo().getId())) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在");
        }
        if (!"active".equals(fv.getStatus())) {
            throw new ApiException(ErrorCode.INVALID_STATE_TRANSITION,
                    "文件当前状态不可下载: " + fv.getStatus());
        }
        // 幂等：同 repository 同键复用既有会话（04 §10）；重放视图必须回放既有会话的文件
        if (idempotencyKey != null) {
            var existing = downloadSessions
                    .findByRepositoryIdAndIdempotencyKey(ctx.repo().getId(), idempotencyKey);
            if (existing.isPresent()) {
                FileVersionEntity boundFv = fileVersions.findById(existing.get().getFileVersionId())
                        .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在"));
                if (!boundFv.getPublicId().equals(fileId)) {
                    throw new ApiException(ErrorCode.CONFLICT, "Idempotency-Key 已绑定其他文件");
                }
                return toView(existing.get(), boundFv.getPublicId());
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        // TTL 分支（05 §11 / 07 §2 SEC-02）：public 走 downloadUrlTtlSeconds；
        // private/organization/gated 走更短的 downloadUrlTtlSecondsPrivate（setter 已钳制 <= 600s）
        boolean publicRepo = "public".equals(ctx.repo().getVisibility());
        int ttlSeconds = publicRepo ? props.getDownloadUrlTtlSeconds()
                : props.getDownloadUrlTtlSecondsPrivate();
        OffsetDateTime expires = now.plusSeconds(ttlSeconds);
        UUID sessionId = PublicIds.next();
        String url = resolveDownloadUrl(fv, sessionId, ttlSeconds);

        DownloadSessionEntity ds = new DownloadSessionEntity();
        ds.setPublicId(sessionId);
        ds.setRepositoryId(ctx.repo().getId());
        ds.setFileVersionId(fv.getId());
        ds.setActorUserId(actor == null ? null : actor.userId());
        ds.setUrl(url);
        ds.setIdempotencyKey(idempotencyKey == null ? sessionId.toString() : idempotencyKey);
        ds.setIssuedAt(now);
        ds.setExpiresAt(expires);
        downloadSessions.save(ds);
        // 签发即下载计数事实（03 §6.2 / 06 §7.1）：幂等键复用不重复发布，stats 由消费者重算
        outbox.publish("DownloadSessionIssued", ctx.repo().getPublicId().toString(), null,
                Map.of("repositoryId", ctx.repo().getId()));
        // SEC-05：私有/受限仓库下载审计（public 匿名流量不记，避免噪音）
        if (!"public".equals(ctx.repo().getVisibility())) {
            audit.appendSimple(actor == null ? "anonymous" : String.valueOf(actor.userId()),
                    "artifact.download_session", "repo:" + repoId, "success");
        }
        return new DownloadSessionView(sessionId, fileId, url, now, expires);
    }

    /** object → MinIO 预签名 GET（scan clean 校验，TTL 与会话一致）；git → 自建内容端点。 */
    private String resolveDownloadUrl(FileVersionEntity fv, UUID sessionId, int ttlSeconds) {
        if ("object".equals(fv.getContentSource())) {
            ObjectBlobEntity blob = fv.getObjectBlobId() == null ? null
                    : blobs.findById(fv.getObjectBlobId()).orElse(null);
            if (blob == null || !"clean".equals(blob.getScanStatus())
                    || !"available".equals(blob.getStatus())) {
                throw new ApiException(ErrorCode.CONTENT_REJECTED, "对象尚未通过扫描或已隔离，暂不可下载");
            }
            return storage.presignGetObject(blob.getObjectKey(), Duration.ofSeconds(ttlSeconds));
        }
        // git source：业务 API 自建内容端点（Gitea 不经公网直连），鉴权在交付时再校验
        return props.getApiBaseUrl() + "/api/v1/downloads/" + sessionId + "/content";
    }

    /** 交付 git source 内容：校验会话有效期 + 再次授权后从 Gitea raw 读取。 */
    public GitContent fetchGitContent(CurrentPrincipal actor, UUID sessionId) {
        DownloadSessionEntity ds = downloadSessions.findByPublicId(sessionId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "下载会话不存在"));
        if (ds.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ApiException(ErrorCode.EVENT_HISTORY_EXPIRED, "下载会话已过期，请重新签发",
                    java.util.List.of(), java.util.Map.of(), 410);
        }
        RepoContext ctx = access.authorize(repoPublicId(ds.getRepositoryId()), actor, RepoRole.READ);
        if (ctx.gatedEnabled() && !ctx.hasActiveGrant()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "该仓库内容受 gated 策略保护");
        }
        FileVersionEntity fv = fileVersions.findById(ds.getFileVersionId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "文件不存在"));
        GitBindingEntity binding = browse.requireBinding(fv.getRepositoryId());
        byte[] content = gitea.rawFile(binding.getExternalNamespace(), binding.getExternalName(),
                fv.getBranch(), fv.getPath());
        if (content == null) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "文件内容在 Git 中不存在");
        }
        String contentType = fv.getContentType() == null ? "application/octet-stream" : fv.getContentType();
        return new GitContent(content, contentType, fv.getPath());
    }

    public record GitContent(byte[] bytes, String contentType, String fileName) {}

    private UUID repoPublicId(Long repositoryId) {
        // 下载会话只存内部 id，经仓库表查回 publicId 供授权门面使用
        return repositories.findById(repositoryId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "仓库不存在"))
                .getPublicId();
    }

    private DownloadSessionView toView(DownloadSessionEntity ds, UUID fileId) {
        return new DownloadSessionView(ds.getPublicId(), fileId, ds.getUrl(), ds.getIssuedAt(), ds.getExpiresAt());
    }
}
