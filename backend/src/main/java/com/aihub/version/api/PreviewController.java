/*
 * 功能: 资产预览 REST 控制器，查询与触发预览生成。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.version.api;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.version.application.PreviewApplicationService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资产预览 REST 控制器。
 *
 * <p>返回已生成的预览内容（JSON 格式），前端渲染时需 DOMPurify 清洗。
 */
@RestController
@RequestMapping("/api/v1/assets/{assetId}/previews")
public class PreviewController {

    private final AuthorizationService authorizationService;
    private final PreviewApplicationService previewApplicationService;

    public PreviewController(AuthorizationService authorizationService,
                             PreviewApplicationService previewApplicationService) {
        this.authorizationService = authorizationService;
        this.previewApplicationService = previewApplicationService;
    }

    /**
     * 获取资产预览。
     *
     * @param assetId   资产 ID
     * @param versionId 版本 ID（可空）
     * @return 预览视图
     */
    @GetMapping
    public PreviewApplicationService.PreviewView getPreview(@PathVariable String assetId,
                                                            @RequestParam(required = false) String versionId) {
        return previewApplicationService.getPreview(assetId, versionId);
    }

    /**
     * 触发预览生成 Job。
     */
    @PostMapping("/generate")
    public Map<String, String> triggerPreviewGeneration(@PathVariable String assetId,
                                                        @RequestBody GeneratePreviewRequest body) {
        authorizationService.requirePermission("asset:manage");
        String principalId = PrincipalContextHolder.current()
                .map(PrincipalContext::principalId).orElse(null);
        String jobId = previewApplicationService.enqueuePreview(
                assetId,
                body.versionId(),
                body.content(),
                body.contentType(),
                principalId);
        return Map.of("jobId", jobId);
    }

    /** 触发预览生成请求。 */
    public record GeneratePreviewRequest(String versionId, String content, String contentType) {}
}
