package com.aihub.version.api;

import com.aihub.authorization.application.AuthorizationService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    private final JdbcTemplate jdbcTemplate;
    private final AuthorizationService authorizationService;

    public PreviewController(JdbcTemplate jdbcTemplate,
                             AuthorizationService authorizationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authorizationService = authorizationService;
    }

    /**
     * 获取资产预览。
     *
     * @param assetId   资产 ID
     * @param versionId 版本 ID（可空）
     * @return 预览视图
     */
    @GetMapping
    public PreviewView getPreview(@PathVariable String assetId,
                                  @RequestParam(required = false) String versionId) {
        authorizationService.requirePermission("asset:read");

        var row = jdbcTemplate.queryForMap(
                "SELECT preview_id, content_type, content, generated_at FROM asset_preview " +
                "WHERE asset_id = ? AND (? IS NULL OR version_id = ?) " +
                "ORDER BY generated_at DESC LIMIT 1",
                assetId, versionId, versionId);

        return new PreviewView(
                String.valueOf(row.get("preview_id")),
                String.valueOf(row.get("content_type")),
                String.valueOf(row.get("content")),
                row.get("generated_at") != null ? row.get("generated_at").toString() : null);
    }

    /**
     * 预览视图。
     */
    public record PreviewView(String previewId, String contentType,
                              String content, String generatedAt) {}
}
