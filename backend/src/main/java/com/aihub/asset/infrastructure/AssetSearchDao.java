/*
 * 功能: 资产检索显式 SQL DAO，承载字段过滤、权限可见性下推与游标键集分页。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.infrastructure;

import com.aihub.asset.domain.AssetSearchCriteria;
import com.aihub.asset.domain.AssetStatus;
import com.aihub.asset.domain.AssetSummary;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.Visibility;
import com.aihub.authorization.domain.AccessScope;
import com.aihub.shared.api.CursorPage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * 资产检索显式 SQL DAO。
 *
 * <p>依据设计 7.3/8.3 节，检索与权限过滤使用显式 SQL：可见性与状态在 {@code WHERE} 阶段下推，
 * 排序字段为服务端固定白名单（{@code created_at, id} 倒序），杜绝客户端拼接 SQL；游标采用键集分页，
 * 避免深分页 offset 退化。
 *
 * <p>tagId 过滤通过 {@code asset_tag} 关联表 EXISTS 子查询下推；tagIds 回显在分页后批量查询填充。
 */
@Repository
public class AssetSearchDao {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {
    };
    private static final String CURSOR_SEPARATOR = "|";

    private static final String BASE_SELECT = """
            SELECT a.id, a.asset_id, a.type, a.namespace, a.organization_id, a.project_id, a.name,
                   a.display_name, a.description, a.visibility, a.status,
                   a.owners::text AS owners_json, a.tags::text AS tags_json, a.license,
                   am.framework, am.task, ad.format, ad.modality,
                   a.provisioning_status, a.created_at, a.updated_at
            FROM asset a
            LEFT JOIN asset_model am ON am.asset_id = a.asset_id
            LEFT JOIN asset_dataset ad ON ad.asset_id = a.asset_id
            WHERE a.deleted = 0
            AND a.provisioning_status = 'COMPLETED'
            """;

    private static final String TAG_IDS_BATCH_SQL = """
            SELECT asset_id, tag_id FROM asset_tag WHERE asset_id IN (:assetIds)
            """;

    private static final String FACET_FROM = """
            FROM asset a
            LEFT JOIN asset_model am ON am.asset_id = a.asset_id
            LEFT JOIN asset_dataset ad ON ad.asset_id = a.asset_id
            WHERE a.deleted = 0 AND a.provisioning_status = 'COMPLETED'
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AssetSearchDao(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按条件检索资产摘要并以游标分页返回。
     *
     * @param criteria 检索条件
     * @return 游标分页摘要
     */
    public CursorPage<AssetSummary> search(AssetSearchCriteria criteria) {
        StringBuilder sql = new StringBuilder(BASE_SELECT);
        MapSqlParameterSource params = new MapSqlParameterSource();

        appendCommonFilters(sql, params, criteria);
        appendCursor(sql, params, criteria.cursor());

        sql.append(" ORDER BY CASE a.status WHEN 'DEPRECATED' THEN 1 ELSE 0 END, a.created_at DESC, a.id DESC LIMIT :limit");
        params.addValue("limit", criteria.limit() + 1);

        List<SearchRow> rows = jdbcTemplate.query(sql.toString(), params, this::mapRow);
        return toPage(rows, criteria.limit());
    }

    /**
     * 按维度统计资产数量（Facet），使用 SQL GROUP BY + UNION ALL 消除内存计数。
     *
     * @param criteria 检索条件（仅使用过滤和权限部分，忽略分页）
     * @return 各维度计数映射
     */
    public Map<String, Map<String, Long>> facet(AssetSearchCriteria criteria) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder();
        appendCommonFilters(where, params, criteria);
        String w = where.toString();

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT 'types' AS dim, a.type AS val, COUNT(*) AS cnt").append(FACET_FROM).append(w).append(" GROUP BY a.type");
        sql.append(" UNION ALL SELECT 'licenses', a.license, COUNT(*)").append(FACET_FROM).append(w).append(" GROUP BY a.license");
        sql.append(" UNION ALL SELECT 'frameworks', am.framework, COUNT(*)").append(FACET_FROM).append(w).append(" GROUP BY am.framework");
        sql.append(" UNION ALL SELECT 'tasks', am.task, COUNT(*)").append(FACET_FROM).append(w).append(" GROUP BY am.task");
        sql.append(" UNION ALL SELECT 'formats', ad.format, COUNT(*)").append(FACET_FROM).append(w).append(" GROUP BY ad.format");
        sql.append(" UNION ALL SELECT 'modalities', ad.modality, COUNT(*)").append(FACET_FROM).append(w).append(" GROUP BY ad.modality");
        sql.append(" UNION ALL SELECT 'sensitivities', COALESCE(am.sensitivity_code, ad.sensitivity_code), COUNT(*)").append(FACET_FROM).append(w).append(" GROUP BY COALESCE(am.sensitivity_code, ad.sensitivity_code)");
        sql.append(" UNION ALL SELECT 'sizeBuckets', ad.size_bucket_code, COUNT(*)").append(FACET_FROM).append(w).append(" GROUP BY ad.size_bucket_code");
        sql.append(" UNION ALL SELECT 'taskCodes', t.val, COUNT(*)").append(FACET_FROM).append(w)
                .append(" AND ad.task_codes IS NOT NULL CROSS JOIN jsonb_array_elements_text(ad.task_codes) t(val) GROUP BY t.val");
        sql.append(" UNION ALL SELECT 'modalityCodes', m.val, COUNT(*)").append(FACET_FROM).append(w)
                .append(" AND ad.modality_codes IS NOT NULL CROSS JOIN jsonb_array_elements_text(ad.modality_codes) m(val) GROUP BY m.val");
        sql.append(" UNION ALL SELECT 'formatCodes', f.val, COUNT(*)").append(FACET_FROM).append(w)
                .append(" AND ad.format_codes IS NOT NULL CROSS JOIN jsonb_array_elements_text(ad.format_codes) f(val) GROUP BY f.val");
        sql.append(" UNION ALL SELECT 'languageCodes', l.val, COUNT(*)").append(FACET_FROM).append(w)
                .append(" AND ad.language_codes IS NOT NULL CROSS JOIN jsonb_array_elements_text(ad.language_codes) l(val) GROUP BY l.val");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params);

        Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        result.put("types", new LinkedHashMap<>());
        result.put("licenses", new LinkedHashMap<>());
        result.put("frameworks", new LinkedHashMap<>());
        result.put("tasks", new LinkedHashMap<>());
        result.put("formats", new LinkedHashMap<>());
        result.put("modalities", new LinkedHashMap<>());
        result.put("sensitivities", new LinkedHashMap<>());
        result.put("sizeBuckets", new LinkedHashMap<>());
        result.put("taskCodes", new LinkedHashMap<>());
        result.put("modalityCodes", new LinkedHashMap<>());
        result.put("formatCodes", new LinkedHashMap<>());
        result.put("languageCodes", new LinkedHashMap<>());

        long total = 0;
        for (Map<String, Object> row : rows) {
            String dim = (String) row.get("dim");
            Object val = row.get("val");
            long cnt = ((Number) row.get("cnt")).longValue();
            if (val != null && result.containsKey(dim)) {
                result.get(dim).put(String.valueOf(val), cnt);
            }
            if ("types".equals(dim)) {
                total += cnt;
            }
        }
        result.put("_total", Map.of("count", total));
        return result;
    }

    /**
     * 追加 search 和 facet 共用的 WHERE 过滤条件。
     */
    private void appendCommonFilters(StringBuilder sql, MapSqlParameterSource params,
                                     AssetSearchCriteria criteria) {
        sql.append(" AND a.status IN (:statuses)");
        params.addValue("statuses", criteria.statusNames());
        sql.append(" AND a.visibility IN (:visibilities)");
        params.addValue("visibilities", criteria.visibilityNames());

        appendEquals(sql, params, "a.type", "type", typeName(criteria.type()));
        appendEquals(sql, params, "a.namespace", "namespace", criteria.namespace());
        appendEquals(sql, params, "a.organization_id", "organizationId", criteria.organizationId());
        appendEquals(sql, params, "a.project_id", "projectId", criteria.projectId());
        appendEquals(sql, params, "a.owner_team_id", "teamId", criteria.teamId());
        appendEquals(sql, params, "am.framework", "framework", criteria.framework());
        appendEquals(sql, params, "am.task", "task", criteria.task());
        appendEquals(sql, params, "ad.format", "format", criteria.format());
        appendEquals(sql, params, "ad.modality", "modality", criteria.modality());
        appendKeyword(sql, params, criteria.keyword());
        appendTagIdFilter(sql, params, criteria.tagId());
        appendMultiTagIdFilter(sql, params, criteria.tagIds());
        appendJsonContains(sql, params, "a.owners", "owner", criteria.owner());
        appendJsonArrayContains(sql, params, "ad.task_codes", "taskCodes", criteria.taskCodes());
        appendJsonArrayContains(sql, params, "ad.modality_codes", "modalityCodes", criteria.modalityCodes());
        appendJsonArrayContains(sql, params, "ad.format_codes", "formatCodes", criteria.formatCodes());
        appendJsonArrayContains(sql, params, "ad.language_codes", "languageCodes", criteria.languageCodes());
        appendEquals(sql, params, "COALESCE(am.sensitivity_code, ad.sensitivity_code)", "sensitivity", criteria.sensitivity());
        appendAccessScopeFilter(sql, params, criteria.accessScope());
    }

    private void appendEquals(StringBuilder sql, MapSqlParameterSource params,
                              String column, String key, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        sql.append(" AND ").append(column).append(" = :").append(key);
        params.addValue(key, value.trim());
    }

    private void appendKeyword(StringBuilder sql, MapSqlParameterSource params, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        sql.append(" AND (a.name ILIKE :kw OR a.display_name ILIKE :kw OR a.description ILIKE :kw"
                + " OR EXISTS (SELECT 1 FROM jsonb_array_elements_text(a.tags) t WHERE t ILIKE :kw))");
        params.addValue("kw", "%" + keyword.trim() + "%");
    }

    private void appendTagIdFilter(StringBuilder sql, MapSqlParameterSource params, String tagId) {
        if (!StringUtils.hasText(tagId)) {
            return;
        }
        sql.append(" AND EXISTS (SELECT 1 FROM asset_tag at WHERE at.asset_id = a.asset_id"
                + " AND at.tag_id = :tagId)");
        params.addValue("tagId", tagId.trim());
    }

    private void appendMultiTagIdFilter(StringBuilder sql, MapSqlParameterSource params, List<String> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        sql.append(" AND EXISTS (SELECT 1 FROM asset_tag at WHERE at.asset_id = a.asset_id"
                + " AND at.tag_id IN (:multiTagIds))");
        params.addValue("multiTagIds", tagIds);
    }

    private void appendJsonArrayContains(StringBuilder sql, MapSqlParameterSource params,
                                         String column, String key, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        sql.append(" AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(").append(column)
                .append(") e WHERE e IN (:").append(key).append("))");
        params.addValue(key, values);
    }

    private void appendAccessScopeFilter(StringBuilder sql, MapSqlParameterSource params,
                                         AccessScope scope) {
        if (scope == null || scope.platformAdmin()) {
            return;
        }
        StringBuilder clause = new StringBuilder();
        clause.append(" AND (a.visibility = 'PUBLIC'");
        if (!scope.organizationIds().isEmpty()) {
            clause.append(" OR a.organization_id IN (:scopeOrgIds)");
            params.addValue("scopeOrgIds", scope.organizationIdList());
        }
        if (!scope.projectIds().isEmpty()) {
            clause.append(" OR a.project_id IN (:scopeProjectIds)");
            params.addValue("scopeProjectIds", scope.projectIdList());
        }
        if (!scope.accessibleResourceIds().isEmpty()) {
            clause.append(" OR a.asset_id IN (:scopeAclIds)");
            params.addValue("scopeAclIds", scope.accessibleResourceIdList());
        }
        clause.append(")");
        sql.append(clause);
    }

    private void appendJsonContains(StringBuilder sql, MapSqlParameterSource params,
                                    String column, String key, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        sql.append(" AND EXISTS (SELECT 1 FROM jsonb_array_elements_text(").append(column)
                .append(") e WHERE e = :").append(key).append(")");
        params.addValue(key, value.trim());
    }

    private void appendCursor(StringBuilder sql, MapSqlParameterSource params, String cursor) {
        Cursor decoded = decodeCursor(cursor);
        if (decoded == null) {
            return;
        }
        sql.append(" AND (a.created_at, a.id) < (:cursorCreatedAt, :cursorId)");
        params.addValue("cursorCreatedAt", OffsetDateTime.ofInstant(decoded.createdAt(), ZoneOffset.UTC));
        params.addValue("cursorId", decoded.id());
    }

    private CursorPage<AssetSummary> toPage(List<SearchRow> rows, int limit) {
        boolean hasMore = rows.size() > limit;
        List<SearchRow> pageRows = hasMore ? rows.subList(0, limit) : rows;
        Map<String, List<String>> tagIdsByAsset = batchLoadTagIds(pageRows);
        List<AssetSummary> summaries = pageRows.stream()
                .map(row -> row.toSummary(tagIdsByAsset.getOrDefault(row.assetId, List.of())))
                .toList();
        if (!hasMore || pageRows.isEmpty()) {
            return CursorPage.last(summaries);
        }
        SearchRow lastRow = pageRows.get(pageRows.size() - 1);
        return new CursorPage<>(summaries, encodeCursor(lastRow.createdAt(), lastRow.id()), true);
    }

    private Map<String, List<String>> batchLoadTagIds(List<SearchRow> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<String> assetIds = rows.stream().map(r -> r.assetId).distinct().toList();
        MapSqlParameterSource params = new MapSqlParameterSource("assetIds", assetIds);
        List<Map<String, Object>> tagRows = jdbcTemplate.queryForList(TAG_IDS_BATCH_SQL, params);
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : tagRows) {
            String aid = (String) row.get("asset_id");
            String tagId = (String) row.get("tag_id");
            result.computeIfAbsent(aid, k -> new ArrayList<>()).add(tagId);
        }
        return result;
    }

    private SearchRow mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        String assetId = rs.getString("asset_id");
        AssetType type = AssetType.valueOf(rs.getString("type"));
        String organizationId = rs.getString("organization_id");
        String projectId = rs.getString("project_id");
        String framework = rs.getString("framework");
        String task = rs.getString("task");
        String format = rs.getString("format");
        String modality = rs.getString("modality");
        Instant updatedAt = rs.getObject("updated_at", OffsetDateTime.class).toInstant();
        Instant createdAt = rs.getObject("created_at", OffsetDateTime.class).toInstant();
        long id = rs.getLong("id");
        return new SearchRow(assetId, type, organizationId, projectId,
                rs.getString("namespace"), rs.getString("name"), rs.getString("display_name"),
                rs.getString("description"), Visibility.valueOf(rs.getString("visibility")),
                AssetStatus.valueOf(rs.getString("status")),
                parseJsonList(rs.getString("owners_json")),
                parseJsonList(rs.getString("tags_json")),
                rs.getString("license"), framework, task, format, modality, updatedAt, createdAt, id);
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json, LIST_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to parse jsonb array", ex);
        }
    }

    private String typeName(AssetType type) {
        return type == null ? null : type.name();
    }

    private String encodeCursor(Instant createdAt, long id) {
        String raw = createdAt.toString() + CURSOR_SEPARATOR + id;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int separator = raw.lastIndexOf(CURSOR_SEPARATOR);
            if (separator < 0) {
                return null;
            }
            Instant createdAt = Instant.parse(raw.substring(0, separator));
            long id = Long.parseLong(raw.substring(separator + 1));
            return new Cursor(createdAt, id);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private record SearchRow(String assetId, AssetType type, String organizationId, String projectId,
                            String namespace, String name, String displayName, String description,
                            Visibility visibility, AssetStatus status,
                            List<String> owners, List<String> tags, String license,
                            String framework, String task, String format, String modality,
                            Instant updatedAt, Instant createdAt, long id) {

        AssetSummary toSummary(List<String> tagIds) {
            return new AssetSummary(assetId, type, namespace, organizationId, projectId, name,
                    displayName, description, visibility, status, owners, tags, tagIds, license,
                    framework, task, format, modality, updatedAt);
        }
    }

    private record Cursor(Instant createdAt, long id) {
    }
}
