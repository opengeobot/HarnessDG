/*
 * 功能: 资产预览 JDBC 查询仓储——从 asset_preview 表读取最新预览记录。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.version.infrastructure;

import com.aihub.version.domain.PreviewQueryPort;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 预览内容查询仓储。
 */
@Repository
public class PreviewQueryRepository implements PreviewQueryPort {

    private final JdbcTemplate jdbcTemplate;

    public PreviewQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<PreviewRecord> findLatestPreview(String assetId, String versionId) {
        var rows = jdbcTemplate.queryForList(
                "SELECT preview_id, content_type, content, generated_at FROM asset_preview "
                        + "WHERE asset_id = ? AND (? IS NULL OR version_id = ?) "
                        + "ORDER BY generated_at DESC LIMIT 1",
                assetId, versionId, versionId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        var row = rows.get(0);
        return Optional.of(new PreviewRecord(
                String.valueOf(row.get("preview_id")),
                String.valueOf(row.get("content_type")),
                String.valueOf(row.get("content")),
                row.get("generated_at") != null ? row.get("generated_at").toString() : null));
    }
}
