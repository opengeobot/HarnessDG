/*
 * 功能: 上传会话 REST 适配器，编排会话创建、分片预签名、完成与取消，主体从 PrincipalContext 注入。
 * 时间: 2026-09-23
 * 作者: AxeXie
 */
package com.aihub.transfer.api;

import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.error.AuthorizationException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.transfer.application.UploadApplicationService;
import com.aihub.transfer.application.UploadSessionView;
import com.aihub.transfer.domain.StoragePort;
import java.net.URL;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 上传会话 REST 控制器。
 *
 * <p>编排上传会话的创建、Part 预签名、完成与取消。
 */
@RestController
@RequestMapping("/api/v1")
public class UploadController {

    private final UploadApplicationService uploadApplicationService;

    public UploadController(UploadApplicationService uploadApplicationService) {
        this.uploadApplicationService = uploadApplicationService;
    }

    /** 创建上传会话。 */
    @PostMapping("/assets/{assetId}/versions/{versionId}/upload-sessions")
    public ApiResponse<UploadSessionView> createSession(
            @PathVariable String assetId,
            @PathVariable String versionId,
            @RequestBody CreateSessionRequest body) {
        return TransferApiContext.respond(uploadApplicationService.createSession(
                assetId, versionId,
                body.totalBytes(), body.fileCount(),
                requirePrincipalId()));
    }

    /** 查询上传会话详情。 */
    @GetMapping("/upload-sessions/{sessionId}")
    public ApiResponse<UploadSessionView> getSession(@PathVariable String sessionId) {
        return TransferApiContext.respond(uploadApplicationService.getSession(sessionId));
    }

    /** 按资产查询上传会话详情（含分片状态，用于恢复）。 */
    @GetMapping("/assets/{assetId}/upload-sessions/{sessionId}")
    public ApiResponse<UploadSessionView> getSessionForAsset(@PathVariable String assetId,
                                                 @PathVariable String sessionId) {
        return TransferApiContext.respond(uploadApplicationService.getSessionForAsset(assetId, sessionId));
    }

    /** 签发 Part 上传预签名 URL。 */
    @GetMapping("/upload-sessions/{sessionId}/parts/{partNumber}/presign")
    public ApiResponse<Map<String, String>> presignPart(@PathVariable String sessionId,
                                            @PathVariable int partNumber) {
        URL url = uploadApplicationService.presignPartUpload(sessionId, partNumber, requirePrincipalId());
        return TransferApiContext.respond(Map.of("url", url.toString()));
    }

    /** 完成上传会话。 */
    @PostMapping("/upload-sessions/{sessionId}/complete")
    public ApiResponse<UploadSessionView> completeSession(@PathVariable String sessionId,
                                              @RequestBody CompleteSessionRequest body) {
        List<StoragePort.PartInfo> parts = body.parts().stream()
                .map(p -> new StoragePort.PartInfo(p.partNumber(), p.etag()))
                .toList();
        List<UploadApplicationService.FileMetadata> files = body.files() == null
                ? List.of()
                : body.files().stream()
                        .map(f -> new UploadApplicationService.FileMetadata(
                                f.path(), f.sha256(), f.size(), f.mediaType(), f.sampleContent()))
                        .toList();
        return TransferApiContext.respond(
                uploadApplicationService.completeSession(sessionId, parts, files, requirePrincipalId()));
    }

    /** 取消上传会话。 */
    @PostMapping("/upload-sessions/{sessionId}/cancel")
    public ApiResponse<UploadSessionView> cancelSession(@PathVariable String sessionId) {
        return TransferApiContext.respond(uploadApplicationService.cancelSession(sessionId, requirePrincipalId()));
    }

    /** 列出资产下的上传会话。 */
    @GetMapping("/assets/{assetId}/upload-sessions")
    public ApiResponse<CursorPage<UploadSessionView>> listSessions(
            @PathVariable String assetId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {
        return TransferApiContext.respond(uploadApplicationService.listSessions(assetId, cursor, limit));
    }

    /** 创建上传会话请求体。 */
    public record CreateSessionRequest(long totalBytes, int fileCount, Integer ttlSeconds) {}

    /**
     * 读取当前认证主体 ID；缺失即拒绝（fail-closed），
     * 上传会话必须以真实主体记账与审计。
     */
    private String requirePrincipalId() {
        return PrincipalContextHolder.current()
                .map(PrincipalContext::principalId)
                .filter(id -> id != null && !id.isBlank())
                .orElseThrow(() -> new AuthorizationException(
                        ErrorCode.AUTH_UNAUTHENTICATED, "no authenticated principal", Map.of()));
    }

    /** 完成上传会话请求体。 */
    public record CompleteSessionRequest(List<PartEntry> parts, List<FileEntry> files) {}

    /** Part 条目。 */
    public record PartEntry(int partNumber, String etag) {}

    /** 文件元数据条目。 */
    public record FileEntry(String path, String sha256, long size,
                            String mediaType, String sampleContent) {}
}
