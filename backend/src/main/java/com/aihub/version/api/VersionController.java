package com.aihub.version.api;

import com.aihub.job.application.JobApplicationService;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.version.application.ArtifactView;
import com.aihub.version.application.PublishApplicationService;
import com.aihub.version.application.VersionApplicationService;
import com.aihub.version.application.VersionView;
import com.aihub.version.domain.VersionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 版本 REST 控制器（适配器）。
 *
 * <p>提供版本列表、详情、草稿创建、工件查询、状态推进、校验报告与发布审批查询。
 * 所有业务逻辑和数据访问委托给 Application Service。
 */
@RestController
@RequestMapping("/api/v1/assets/{assetId}/versions")
public class VersionController {

    private final VersionApplicationService versionService;
    private final PublishApplicationService publishApplicationService;
    private final JobApplicationService jobApplicationService;
    private final ObjectMapper objectMapper;

    public VersionController(VersionApplicationService versionService,
                             PublishApplicationService publishApplicationService,
                             JobApplicationService jobApplicationService,
                             ObjectMapper objectMapper) {
        this.versionService = versionService;
        this.publishApplicationService = publishApplicationService;
        this.jobApplicationService = jobApplicationService;
        this.objectMapper = objectMapper;
    }

    /** 列出版本（游标分页）。 */
    @GetMapping
    public ApiResponse<CursorPage<VersionView>> listVersions(
            @PathVariable String assetId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false, defaultValue = "20") int limit) {
        return respond(versionService.listVersions(assetId, cursor, limit));
    }

    /** 创建草稿版本。 */
    @PostMapping
    public ApiResponse<VersionView> createDraftVersion(
            @PathVariable String assetId,
            @RequestBody CreateVersionRequest request) {
        String principalId = PrincipalContextHolder.current()
                .map(PrincipalContext::principalId).orElse(null);
        return respond(versionService.createDraftVersion(assetId, request.version(), principalId));
    }

    /** 查询版本详情。 */
    @GetMapping("/{versionId}")
    public ApiResponse<VersionView> getVersion(
            @PathVariable String assetId,
            @PathVariable String versionId) {
        return respond(versionService.getVersion(versionId));
    }

    /** 列出版本下的工件。 */
    @GetMapping("/{versionId}/artifacts")
    public ApiResponse<List<ArtifactView>> listArtifacts(
            @PathVariable String assetId,
            @PathVariable String versionId) {
        return respond(versionService.listArtifacts(versionId));
    }

    /** 推进版本状态（DRAFT→VALIDATING 时自动入队校验 Job）。 */
    @PostMapping("/{versionId}/transition")
    public ApiResponse<VersionView> transitionVersion(
            @PathVariable String assetId,
            @PathVariable String versionId,
            @RequestBody TransitionRequest request) {
        String principalId = PrincipalContextHolder.current()
                .map(PrincipalContext::principalId).orElse(null);
        VersionStatus target = VersionStatus.valueOf(request.targetStatus());
        VersionView updated = versionService.transitionVersion(versionId, target, principalId);

        // DRAFT→VALIDATING 时入队校验 Job
        if (target == VersionStatus.VALIDATING) {
            try {
                String payload = objectMapper.writeValueAsString(Map.of("versionId", versionId));
                jobApplicationService.enqueue("VERSION_VALIDATE", payload, principalId, null, assetId, 3);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException("failed to serialize validation payload", e);
            }
        }
        return respond(updated);
    }

    /** 查询校验报告（委托 VersionApplicationService）。 */
    @GetMapping("/{versionId}/validation-report")
    public ApiResponse<Map<String, Object>> getValidationReport(
            @PathVariable String assetId,
            @PathVariable String versionId) {
        return respond(versionService.getValidationReport(versionId));
    }

    /** 查询发布请求列表（委托 PublishApplicationService）。 */
    @GetMapping("/publish-requests")
    public ApiResponse<List<Map<String, Object>>> listPublishRequests(
            @PathVariable String assetId) {
        return respond(publishApplicationService.listPublishRequests(assetId));
    }

    /** 查询审批决策历史（委托 PublishApplicationService）。 */
    @GetMapping("/publish-requests/{requestId}/decisions")
    public ApiResponse<List<Map<String, Object>>> listDecisions(
            @PathVariable String assetId,
            @PathVariable String requestId) {
        return respond(publishApplicationService.listDecisions(requestId));
    }

    public record CreateVersionRequest(String version) {}
    public record TransitionRequest(String targetStatus) {}

    private static <T> ApiResponse<T> respond(T data) {
        String requestId = PrincipalContextHolder.current().map(PrincipalContext::requestId).orElse(null);
        String traceId = PrincipalContextHolder.current().map(PrincipalContext::traceId).orElse(null);
        return ApiResponse.of(data, requestId, traceId);
    }
}
