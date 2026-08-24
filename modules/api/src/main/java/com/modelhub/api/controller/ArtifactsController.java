package com.modelhub.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modelhub.api.support.IdempotentOps;
import com.modelhub.api.support.Principals;
import com.modelhub.artifact.service.BrowseService;
import com.modelhub.artifact.service.BrowseService.BranchListData;
import com.modelhub.artifact.service.BrowseService.CommitPageData;
import com.modelhub.artifact.service.BrowseService.FilePageData;
import com.modelhub.artifact.service.DownloadService;
import com.modelhub.artifact.service.DownloadService.DownloadSessionView;
import com.modelhub.artifact.service.DownloadService.GitContent;
import com.modelhub.artifact.service.FilesService;
import com.modelhub.catalog.service.CatalogService.JobView;
import com.modelhub.identity.config.IdentityProperties;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.idempotency.JdbcIdempotencyService.Acquired;
import com.modelhub.shared.web.ApiEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Artifact 只读浏览与下载端点（04 Artifacts）：branches/commits/files 清单、
 * 下载会话签发、git source 内容交付、文件删除。files 响应 ETag=resolvedCommitSha，
 * 供 DELETE files 的 If-Match 前置条件（04 FilePageEnvelope）。
 */
@RestController
public class ArtifactsController {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;

    private final BrowseService browse;
    private final DownloadService downloads;
    private final FilesService files;
    private final IdempotentOps idempotent;
    private final ObjectMapper objectMapper;
    private final IdentityProperties identityProps;

    public ArtifactsController(BrowseService browse, DownloadService downloads, FilesService files,
                               IdempotentOps idempotent, ObjectMapper objectMapper,
                               IdentityProperties identityProps) {
        this.browse = browse;
        this.downloads = downloads;
        this.files = files;
        this.idempotent = idempotent;
        this.objectMapper = objectMapper;
        this.identityProps = identityProps;
    }

    /** branches 清单（cursor 模式 data）：items + nextCursor（翻尽为 null，04 §6.2）。 */
    @GetMapping("/api/v1/repositories/{repoId}/branches")
    public ApiEnvelope<BranchListData> branches(@PathVariable UUID repoId,
                                                @RequestParam(required = false) String cursor,
                                                @RequestParam(required = false) Integer limit,
                                                HttpServletRequest request) {
        return ApiEnvelope.ok(browse.listBranches(Principals.optionalCurrent(request), repoId,
                cursor, limit(limit)));
    }

    /** commits 清单（cursor 模式 data）：items + nextCursor（翻尽为 null，04 §6.2）。 */
    @GetMapping("/api/v1/repositories/{repoId}/commits")
    public ApiEnvelope<CommitPageData> commits(@PathVariable UUID repoId,
                                               @RequestParam(required = false) String branch,
                                               @RequestParam(required = false) String cursor,
                                               @RequestParam(required = false) Integer limit,
                                               HttpServletRequest request) {
        return ApiEnvelope.ok(browse.listCommits(Principals.optionalCurrent(request), repoId,
                branch, cursor, limit(limit)));
    }

    /** files 清单：ETag = resolvedCommitSha（04 FilePageEnvelope 响应头约定）。
     *  branch 与 ref 互斥（契约 listFiles 同时声明二者），同时提供 → 400。 */
    @GetMapping("/api/v1/repositories/{repoId}/files")
    public ResponseEntity<ApiEnvelope<Map<String, Object>>> files(@PathVariable UUID repoId,
                                                                  @RequestParam(required = false) String branch,
                                                                  @RequestParam(required = false) String ref,
                                                                  @RequestParam(required = false) String path,
                                                                  @RequestParam(required = false) String pathPrefix,
                                                                  @RequestParam(required = false) String cursor,
                                                                  @RequestParam(required = false) Integer limit,
                                                                  HttpServletRequest request) {
        if (branch != null && !branch.isBlank() && ref != null && !ref.isBlank()) {
            throw ApiException.badRequest("branch 与 ref 互斥，仅可提供其一",
                    List.of(new ApiException.Detail("ref", "mutually_exclusive")));
        }
        FilePageData data = browse.listFiles(Principals.optionalCurrent(request), repoId,
                branch, ref, path != null ? path : pathPrefix, cursor, limit(limit),
                Principals.clientIp(request, identityProps));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("resolvedCommitSha", data.resolvedCommitSha());
        body.put("nextCursor", data.nextCursor());
        body.put("items", data.items());
        return ResponseEntity.ok()
                .eTag("\"" + data.resolvedCommitSha() + "\"")
                .body(ApiEnvelope.ok(body));
    }

    /** 下载会话签发（04 §6.5）：授权成功即下载计数事实，201 + DownloadSessionEnvelope。 */
    @PostMapping("/api/v1/repositories/{repoId}/files/{fileId}/download-sessions")
    public ResponseEntity<ApiEnvelope<DownloadSessionView>> downloadSession(
            @PathVariable UUID repoId, @PathVariable UUID fileId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest request) {
        DownloadSessionView view = downloads.createSession(
                Principals.optionalCurrent(request), repoId, fileId, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.created(view));
    }

    /** git source 内容交付：下载会话 URL 指向本端点，交付时再次鉴权（05 §11）。 */
    @GetMapping("/api/v1/downloads/{sessionId}/content")
    public ResponseEntity<byte[]> content(@PathVariable UUID sessionId, HttpServletRequest request) {
        GitContent content = downloads.fetchGitContent(Principals.optionalCurrent(request), sessionId);
        String filename = new String(content.fileName().getBytes(StandardCharsets.UTF_8),
                StandardCharsets.ISO_8859_1);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(content.bytes());
    }

    /** 文件删除（04 deleteFile）：If-Match=resolvedCommitSha，202 + JobEnvelope。 */
    @DeleteMapping("/api/v1/repositories/{repoId}/files/{fileId}")
    public ResponseEntity<String> deleteFile(@PathVariable UUID repoId, @PathVariable UUID fileId,
                                             @RequestHeader(value = "If-Match", required = false) String ifMatch,
                                             @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                             HttpServletRequest request) throws Exception {
        CurrentPrincipal principal = Principals.requireCurrent(request);
        if (idempotencyKey == null) {
            JobView job = files.deleteFile(principal, repoId, fileId, ifMatch);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(ApiEnvelope.ok(job)));
        }
        Acquired acq = idempotent.begin(principal, request.getMethod(), request.getRequestURI(),
                idempotencyKey, null);
        if (acq.replay()) {
            return ResponseEntity.status(acq.recordedStatus())
                    .contentType(MediaType.APPLICATION_JSON).body(acq.recordedBody());
        }
        JobView job = files.deleteFile(principal, repoId, fileId, ifMatch);
        String responseBody = objectMapper.writeValueAsString(ApiEnvelope.ok(job));
        idempotent.finish(principal, request.getMethod(), request.getRequestURI(),
                idempotencyKey, 202, responseBody);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .contentType(MediaType.APPLICATION_JSON).body(responseBody);
    }

    /** limit 严格校验（04 §6.2）：缺省 50；越界（<1 或 >100）→ 400，不静默收敛；
     *  非整数由 Spring 类型不匹配统一映射 400 VALIDATION_FAILED。 */
    private static int limit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw ApiException.badRequest("limit 必须在 1..100",
                    List.of(new ApiException.Detail("limit", "out_of_range")));
        }
        return limit;
    }
}
