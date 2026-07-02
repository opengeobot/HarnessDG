/*
 * 功能: 受控标签仓储 JDBC 实现，读写 system_tag，按作用域/状态/关键字过滤并提供批量 ID 解析。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.tag.infrastructure;

import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.taxonomy.domain.TaxonomyStatus;
import com.aihub.taxonomy.tag.domain.Tag;
import com.aihub.taxonomy.tag.domain.TagRepository;
import com.aihub.taxonomy.tag.domain.TagScopeType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 受控标签仓储 JDBC 实现。
 */
@Repository
public class JdbcTagRepository implements TagRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcTagRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Tag> search(TagScopeType scopeType, String scopeId, TaxonomyStatus status, String keyword) {
        StringBuilder sql = new StringBuilder(
                "SELECT tag_id, scope_type, scope_id, tag_code, name, i18n_key, color, status, "
                        + "created_by, version FROM system_tag WHERE 1=1");
        MapSqlParameterSource params = new MapSqlParameterSource();
        if (scopeType != null) {
            sql.append(" AND scope_type = :scopeType");
            params.addValue("scopeType", scopeType.name());
        }
        if (scopeId != null && !scopeId.isBlank()) {
            sql.append(" AND scope_id = :scopeId");
            params.addValue("scopeId", scopeId);
        }
        if (status != null) {
            sql.append(" AND status = :status");
            params.addValue("status", status.name());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (LOWER(name) LIKE :kw OR LOWER(tag_code) LIKE :kw)");
            params.addValue("kw", "%" + keyword.toLowerCase() + "%");
        }
        sql.append(" ORDER BY scope_type, scope_id, tag_code");
        return jdbcTemplate.query(sql.toString(), params, this::mapTag);
    }

    @Override
    public Optional<Tag> findByTagId(String tagId) {
        List<Tag> result = jdbcTemplate.query(
                "SELECT tag_id, scope_type, scope_id, tag_code, name, i18n_key, color, status, "
                        + "created_by, version FROM system_tag WHERE tag_id = :tagId",
                new MapSqlParameterSource("tagId", tagId), this::mapTag);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public List<Tag> findByTagIds(List<String> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
                "SELECT tag_id, scope_type, scope_id, tag_code, name, i18n_key, color, status, "
                        + "created_by, version FROM system_tag WHERE tag_id IN (:tagIds)",
                new MapSqlParameterSource("tagIds", tagIds), this::mapTag);
    }

    @Override
    public boolean existsByCode(TagScopeType scopeType, String scopeId, String tagCode) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(1) FROM system_tag "
                        + "WHERE scope_type = :scopeType AND scope_id = :scopeId AND tag_code = :tagCode",
                new MapSqlParameterSource()
                        .addValue("scopeType", scopeType.name())
                        .addValue("scopeId", scopeId)
                        .addValue("tagCode", tagCode), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public void insert(Tag tag) {
        jdbcTemplate.update(
                "INSERT INTO system_tag (tag_id, scope_type, scope_id, tag_code, name, i18n_key, color, "
                        + "status, created_by, version, created_at, updated_at) "
                        + "VALUES (:tagId, :scopeType, :scopeId, :tagCode, :name, :i18nKey, :color, "
                        + ":status, :createdBy, :version, now(), now())",
                new MapSqlParameterSource()
                        .addValue("tagId", tag.tagId())
                        .addValue("scopeType", tag.scopeType().name())
                        .addValue("scopeId", tag.scopeId())
                        .addValue("tagCode", tag.tagCode())
                        .addValue("name", tag.name())
                        .addValue("i18nKey", tag.i18nKey())
                        .addValue("color", tag.color())
                        .addValue("status", tag.status().name())
                        .addValue("createdBy", tag.createdBy())
                        .addValue("version", tag.version()));
    }

    @Override
    public void update(Tag tag, long expectedVersion) {
        int affected = jdbcTemplate.update(
                "UPDATE system_tag SET name = :name, i18n_key = :i18nKey, color = :color, "
                        + "version = version + 1, updated_at = now() "
                        + "WHERE tag_id = :tagId AND version = :expectedVersion",
                new MapSqlParameterSource()
                        .addValue("name", tag.name())
                        .addValue("i18nKey", tag.i18nKey())
                        .addValue("color", tag.color())
                        .addValue("tagId", tag.tagId())
                        .addValue("expectedVersion", expectedVersion));
        if (affected == 0) {
            throw new ConflictException(ErrorCode.CONCURRENT_MODIFICATION,
                    "tag was concurrently modified", Map.of("tagId", tag.tagId()));
        }
    }

    @Override
    public void updateStatus(String tagId, TaxonomyStatus status, long expectedVersion) {
        int affected = jdbcTemplate.update(
                "UPDATE system_tag SET status = :status, version = version + 1, updated_at = now() "
                        + "WHERE tag_id = :tagId AND version = :expectedVersion",
                new MapSqlParameterSource()
                        .addValue("status", status.name())
                        .addValue("tagId", tagId)
                        .addValue("expectedVersion", expectedVersion));
        if (affected == 0) {
            throw new ConflictException(ErrorCode.CONCURRENT_MODIFICATION,
                    "tag was concurrently modified", Map.of("tagId", tagId));
        }
    }

    private Tag mapTag(ResultSet rs, int rowNum) throws SQLException {
        return new Tag(
                rs.getString("tag_id"),
                TagScopeType.valueOf(rs.getString("scope_type")),
                rs.getString("scope_id"),
                rs.getString("tag_code"),
                rs.getString("name"),
                rs.getString("i18n_key"),
                rs.getString("color"),
                TaxonomyStatus.valueOf(rs.getString("status")),
                rs.getString("created_by"),
                rs.getLong("version"));
    }
}
