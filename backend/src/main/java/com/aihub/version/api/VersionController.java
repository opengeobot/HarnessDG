package com.aihub.version.api;

import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.api.CursorPage;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.version.application.ArtifactView;
import com.aihub.version.application.VersionApplicationService;
import com.aihub.version.application.VersionView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 版本 REST 控制器。
 *
 * <p>提供版本列表、详情、草稿创建与工件查询接口。
 */
@RestController
@RequestMapping("/api/v1/assets/{assetId}/versions")
public class VersionController {

    private final VersionApplicationService versionService;

    public VersionController(VersionApplicationService versionService) {
        this.versionService = versionService;
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

    public record CreateVersionRequest(String version) {}

    private static <T> ApiResponse<T> respond(T data) {
        String requestId = PrincipalContextHolder.current().map(PrincipalContext::requestId).orElse(null);
        String traceId = PrincipalContextHolder.current().map(PrincipalContext::traceId).orElse(null);
        return ApiResponse.of(data, requestId, traceId);
    }
}
