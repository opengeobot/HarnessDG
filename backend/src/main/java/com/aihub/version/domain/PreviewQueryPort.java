/*
 * 功能: 预览内容查询端口——应用层读取 asset_preview 投影。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.version.domain;

import java.util.Optional;

/**
 * 预览查询端口。
 */
public interface PreviewQueryPort {

    /** 预览记录快照。 */
    record PreviewRecord(String previewId, String contentType, String content, String generatedAt) {}

    /**
     * 查询资产最新预览记录。
     *
     * @param assetId   资产 ID
     * @param versionId 版本 ID（可空）
     */
    Optional<PreviewRecord> findLatestPreview(String assetId, String versionId);
}
