/*
 * 功能: Agent REST 适配器，提供 /api/v1/agent/* 只读与贡献写端点，复用与 MCP 相同的应用服务与授权。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.agent.api;

import com.aihub.asset.api.AssetApiContext;
import com.aihub.asset.application.AssetApplicationService;
import com.aihub.asset.application.AssetSearchQuery;
import com.aihub.asset.application.AssetSummaryView;
import com.aihub.asset.application.AssetView;
import com.aihub.asset.domain.AssetType;
import com.aihub.audit.application.AuditEvent;
import com.aihub.audit.application.AuditService;
import com.aihub.audit.domain.AuditResult;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.job.application.IdempotencyService;
import com.aihub.mcp.application.McpDownloadHandle;
import com.aihub.platform.security.RateLimiter;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.error.AuthorizationException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.RateLimitException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.idempotency.IdempotencyKey;
import com.aihub.shared.idempotency.IdempotencySupport;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.transfer.application.UploadApplicationService;
import com.aihub.transfer.application.UploadSessionView;
import com.aihub.transfer.domain.StoragePort;
import com.aihub.transfer.application.DownloadApplicationService;
import com.aihub.version.application.VersionApplicationService;
import com.aihub.version.application.VersionQueryService;
import com.aihub.version.application.VersionView;
import com.aihub.version.domain.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent REST 适配器（只读 + 贡献 Profile）。
 *
 * <p>薄控制器：请求映射 + JWT 授权（与 MCP 等价的 Scope + Agent 工具白名单），业务规则在应用服务内完成。
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private final AssetApplicationService assetService;
    private final VersionApplicationService versionService;
    private final VersionQueryService versionQueryService;
    private final DownloadApplicationService downloadService;
    private final UploadApplicationService uploadService;
    private final AuthorizationService authorizationService;
    private final IdempotencyService idempotencyService;
    private final IdempotencySupport idempotency;
    private final RateLimiter rateLimiter;
    private final AuditService auditService;
    private final boolean writeToolsEnabled;

    public AgentController(AssetApplicationService assetService,
                           VersionApplicationService versionService,
                           VersionQueryService versionQueryService,
                           DownloadApplicationService downloadService,
                           UploadApplicationService uploadService,
                           AuthorizationService authorizationService,
                           IdempotencyService idempotencyService,
                           ObjectMapper objectMapper,
                           RateLimiter rateLimiter,
                           AuditService auditService,
                           @Value("${mcp.writeTools.enabled:false}") boolean writeToolsEnabled) {
        this.assetService = assetService;
        this.versionService = versionService;
        this.versionQueryService = versionQueryService;
        this.downloadService = downloadService;
        this.uploadService = uploadService;
        this.authorizationService = authorizationService;
        this.idempotencyService = idempotencyService;
        this.idempotency = new IdempotencySupport(objectMapper);
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.writeToolsEnabled = writeToolsEnabled;
    }

    /** 搜索资产（等价 MCP {@code asset_search}）。 */
    @GetMapping("/assets/search")
    public ApiResponse<CursorPage<Map<String, Object>>> searchAssets(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AssetType type,
            @RequestParam(required = false) String namespace,
            @RequestParam(required = false) String tagId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        requireAgentAccess("asset_search", Permissions.ASSET_READ);
        String principalId = AssetApiContext.principalId();
        AssetSearchQuery query = new AssetSearchQuery(
                keyword, type, namespace, null, null, null, null, null,
                null, null, null, null, tagId, null, null, null,
                null, null, null, null, false, cursor, limit, principalId);
        CursorPage<AssetSummaryView> page = assetService.searchAssets(query);
        Map<String, Version> latestByAsset = versionQueryService.findLatestPublishedByAssetIds(
                Set.copyOf(page.items().stream().map(AssetSummaryView::assetId).toList()));
        List<Map<String, Object>> items = page.items().stream()
                .map(summary -> toSummaryMap(summary, latestByAsset.get(summary.assetId())))
                .toList();
        return AssetApiContext.respond(new CursorPage<>(items, page.nextCursor(), page.hasMore()));
    }

    /** 查询资产详情（等价 MCP {@code asset_get}）。 */
    @GetMapping("/assets/{assetId}")
    public ApiResponse<Map<String, Object>> getAsset(@PathVariable String assetId) {
        requireAgentAccess("asset_get", Permissions.ASSET_READ);
        AssetView view = assetService.getAsset(assetId, AssetApiContext.principalId());
        Map<String, Object> result = toDetailMap(view);
        versionQueryService.findLatestPublishedByAssetId(assetId)
                .ifPresent(v -> result.put("latestPublished", v.version()));
        return AssetApiContext.respond(result);
    }

    /** 列出版本（等价 MCP {@code asset_list_versions}）。 */
    @GetMapping("/assets/{assetId}/versions")
    public ApiResponse<CursorPage<VersionView>> listVersions(
            @PathVariable String assetId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        requireAgentAccess("asset_list_versions", Permissions.ASSET_READ);
        return AssetApiContext.respond(versionService.listVersions(assetId, cursor, limit));
    }

    /** 查询版本详情（等价 MCP {@code asset_get_version}）。 */
    @GetMapping("/versions/{versionId}")
    public ApiResponse<VersionView> getVersion(@PathVariable String versionId) {
        requireAgentAccess("asset_get_version", Permissions.ASSET_READ);
        return AssetApiContext.respond(versionService.getVersion(versionId));
    }

    /** 签发下载票据 handle（等价 MCP {@code asset_request_download}，不含 presignedUrl）。 */
    @PostMapping("/versions/{versionId}/download")
    public ApiResponse<McpDownloadHandle> requestDownload(
            @PathVariable String versionId,
            @RequestBody(required = false) DownloadRequest request) {
        requireAgentAccess("asset_request_download", Permissions.ASSET_READ);
        String artifactId = request == null ? null : request.artifactId();
        var ticket = downloadService.issueTicket(versionId, artifactId);
        return AssetApiContext.respond(McpDownloadHandle.fromTicket(versionId, artifactId, ticket));
    }

    /** 创建草稿版本（等价 MCP {@code asset_create_draft}）。 */
    @PostMapping("/assets/{assetId}/versions/draft")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<VersionView> createDraftVersion(
            @PathVariable String assetId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue,
            @RequestBody CreateDraftRequest request) {
        PrincipalContext context = requireAgentWriteAccess("asset_create_draft", Permissions.ASSET_MANAGE);
        enforceRateLimit("asset_create_draft");
        requireIdempotencyKey(idempotencyKeyValue);
        String principalId = context.principalId();
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue, principalId,
                "POST", "/api/v1/agent/assets/" + assetId + "/versions/draft");
        String fingerprint = idempotency.sha256Digest(request);
        AtomicReference<VersionView> ref = new AtomicReference<>();
        idempotencyService.execute(key, fingerprint, () -> {
            VersionView view = versionService.createDraftVersion(assetId, request.version(), principalId);
            ref.set(view);
            return new IdempotencyService.IdempotencyResponse(201, idempotency.serialize(view));
        });
        return AssetApiContext.respond(ref.get());
    }

    /** 创建上传会话（等价 MCP {@code asset_create_upload_session}）。 */
    @PostMapping("/assets/{assetId}/upload-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UploadSessionView> createUploadSession(
            @PathVariable String assetId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue,
            @RequestBody CreateUploadSessionRequest request) {
        PrincipalContext context = requireAgentWriteAccess("asset_create_upload_session", Permissions.ASSET_MANAGE);
        enforceRateLimit("asset_create_upload_session");
        requireIdempotencyKey(idempotencyKeyValue);
        String principalId = context.principalId();
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue, principalId,
                "POST", "/api/v1/agent/assets/" + assetId + "/upload-sessions");
        String fingerprint = idempotency.sha256Digest(request);
        AtomicReference<UploadSessionView> ref = new AtomicReference<>();
        idempotencyService.execute(key, fingerprint, () -> {
            UploadSessionView view = uploadService.createSession(
                    assetId, request.versionId(),
                    request.totalBytes(), request.fileCount(), principalId);
            ref.set(view);
            return new IdempotencyService.IdempotencyResponse(201, idempotency.serialize(view));
        });
        return AssetApiContext.respond(ref.get());
    }

    /** 完成上传会话（等价 MCP {@code asset_complete_upload}）。 */
    @PostMapping("/assets/{assetId}/upload-sessions/{sessionId}/complete")
    public ApiResponse<UploadSessionView> completeUploadSession(
            @PathVariable String assetId,
            @PathVariable String sessionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue,
            @RequestBody(required = false) CompleteUploadSessionRequest request) {
        PrincipalContext context = requireAgentWriteAccess("asset_complete_upload", Permissions.ASSET_MANAGE);
        enforceRateLimit("asset_complete_upload");
        requireIdempotencyKey(idempotencyKeyValue);
        String principalId = context.principalId();
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue, principalId,
                "POST", "/api/v1/agent/assets/" + assetId + "/upload-sessions/" + sessionId + "/complete");
        CompleteUploadSessionRequest body = request == null ? new CompleteUploadSessionRequest(List.of(), List.of()) : request;
        String fingerprint = idempotency.sha256Digest(body);
        AtomicReference<UploadSessionView> ref = new AtomicReference<>();
        idempotencyService.execute(key, fingerprint, () -> {
            List<StoragePort.PartInfo> parts = body.parts() == null ? List.of()
                    : body.parts().stream()
                            .map(p -> new StoragePort.PartInfo(p.partNumber(), p.etag()))
                            .toList();
            List<UploadApplicationService.FileMetadata> files = body.files() == null ? List.of()
                    : body.files().stream()
                            .map(f -> new UploadApplicationService.FileMetadata(
                                    f.path(), f.sha256(), f.size(), f.mediaType(), f.sampleContent()))
                            .toList();
            UploadSessionView view = uploadService.completeSession(sessionId, parts, files, principalId);
            ref.set(view);
            return new IdempotencyService.IdempotencyResponse(200, idempotency.serialize(view));
        });
        return AssetApiContext.respond(ref.get());
    }

    /** 查询上传会话/物化任务状态（等价 MCP {@code asset_get_upload_status}）。 */
    @GetMapping("/assets/{assetId}/upload-sessions/{sessionId}")
    public ApiResponse<UploadSessionView> getUploadSession(
            @PathVariable String assetId,
            @PathVariable String sessionId) {
        requireAgentWriteAccess("asset_get_upload_status", Permissions.ASSET_READ);
        enforceRateLimit("asset_get_upload_status");
        return AssetApiContext.respond(uploadService.getSessionForAsset(assetId, sessionId));
    }

    public record DownloadRequest(String artifactId) {
    }

    public record CreateDraftRequest(String version) {
    }

    public record CreateUploadSessionRequest(String versionId, long totalBytes, int fileCount) {
    }

    public record CompleteUploadSessionRequest(List<PartEntry> parts, List<FileEntry> files) {
    }

    public record PartEntry(int partNumber, String etag) {
    }

    public record FileEntry(String path, String sha256, long size, String mediaType, String sampleContent) {
    }

    private void requireAgentAccess(String mcpTool, String requiredPermission) {
        PrincipalContext context = PrincipalContextHolder.current()
                .filter(ctx -> ctx.principalId() != null)
                .orElseThrow(() -> new AuthorizationException(
                        ErrorCode.AUTH_UNAUTHENTICATED, "no authenticated principal", Map.of()));
        authorizationService.requirePermission(context, Permissions.MCP_INVOKE);
        authorizationService.requirePermission(context, requiredPermission);
        if (context.principalType() == PrincipalType.AGENT) {
            authorizationService.requireToolAllowed(context.principalId(), mcpTool);
        }
    }

    private PrincipalContext requireAgentWriteAccess(String mcpTool, String requiredPermission) {
        PrincipalContext context = PrincipalContextHolder.current()
                .filter(ctx -> ctx.principalId() != null)
                .orElseThrow(() -> new AuthorizationException(
                        ErrorCode.AUTH_UNAUTHENTICATED, "no authenticated principal", Map.of()));
        if (!writeToolsEnabled) {
            throw new AuthorizationException(
                    ErrorCode.AUTH_PERMISSION_DENIED, "write tools are disabled", Map.of("tool", mcpTool));
        }
        authorizationService.requirePermission(context, Permissions.MCP_INVOKE);
        authorizationService.requirePermission(context, requiredPermission);
        if (context.principalType() == PrincipalType.AGENT) {
            authorizationService.requireToolAllowed(context.principalId(), mcpTool);
        }
        return context;
    }

    private void requireIdempotencyKey(String idempotencyKeyValue) {
        if (idempotencyKeyValue == null || idempotencyKeyValue.isBlank()) {
            throw new ValidationException("Idempotency-Key header is required for write endpoints");
        }
    }

    private void enforceRateLimit(String toolName) {
        PrincipalContext context = PrincipalContextHolder.current().orElse(null);
        if (context == null || context.principalId() == null) {
            return;
        }
        if (rateLimiter.tryAcquire(context.principalId(), toolName)) {
            return;
        }
        auditRateLimitDenied(context, toolName);
        throw new RateLimitException(ErrorCode.RATE_LIMIT_EXCEEDED, "rate limit exceeded",
                Map.of("tool", toolName));
    }

    private void auditRateLimitDenied(PrincipalContext context, String toolName) {
        auditService.record(new AuditEvent(
                "AGENT_RATE_LIMIT_DENIED",
                "agent:rest:call",
                context.principalId(),
                context.principalType() == null ? null : context.principalType().name(),
                "MCP_TOOL",
                toolName,
                null,
                null,
                AuditResult.DENIED,
                ErrorCode.RATE_LIMIT_EXCEEDED.name(),
                Map.of("tool", toolName)));
    }

    private static Map<String, Object> toSummaryMap(AssetSummaryView summary, Version latestPublished) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("assetId", summary.assetId());
        item.put("coordinate", summary.coordinate());
        item.put("type", summary.type());
        item.put("namespace", summary.namespace());
        item.put("name", summary.name());
        item.put("displayName", summary.displayName());
        item.put("description", summary.description());
        item.put("visibility", summary.visibility());
        item.put("status", summary.status());
        item.put("license", summary.license());
        item.put("matchedFields", summary.matchedFields());
        item.put("updatedAt", summary.updatedAt());
        if (latestPublished != null) {
            item.put("latestPublished", latestPublished.version());
        }
        return item;
    }

    private static Map<String, Object> toDetailMap(AssetView view) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assetId", view.assetId());
        result.put("coordinate", view.coordinate());
        result.put("type", view.type());
        result.put("namespace", view.namespace());
        result.put("name", view.name());
        result.put("displayName", view.displayName());
        result.put("description", view.description());
        result.put("visibility", view.visibility());
        result.put("status", view.status());
        result.put("license", view.license());
        result.put("organizationId", view.organizationId());
        result.put("owners", view.owners());
        result.put("tags", view.tags());
        result.put("tagIds", view.tagIds());
        result.put("provisioningStatus", view.provisioningStatus());
        result.put("createdAt", view.createdAt());
        result.put("updatedAt", view.updatedAt());
        return result;
    }
}
