/*
 * 功能: AssetSearchDao 单元测试——验证 DEPRECATED 资产搜索降权排序。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.asset.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aihub.asset.domain.AssetSearchCriteria;
import com.aihub.asset.domain.AssetStatus;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.Visibility;
import com.aihub.authorization.domain.AccessScope;
import com.aihub.shared.api.CursorPage;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class AssetSearchDaoTest {

    private NamedParameterJdbcTemplate jdbcTemplate;
    private AssetSearchDao dao;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        dao = new AssetSearchDao(jdbcTemplate, Caffeine.newBuilder().maximumSize(10).build());
    }

    @Test
    void searchSqlDemotesDeprecatedBeforeActive() {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        when(jdbcTemplate.query(sqlCaptor.capture(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        AssetSearchCriteria criteria = new AssetSearchCriteria(
                null, AssetType.MODEL, null, null, null, null,
                null, null, null, null, null, null,
                Set.of(AssetStatus.ACTIVE, AssetStatus.DEPRECATED),
                Set.of(Visibility.PUBLIC),
                new AccessScope(null, true, Set.of(), Set.of(), Set.of()),
                null, null, null, null, null, null, null, 20);

        CursorPage<?> page = dao.search(criteria);

        assertThat(page.items()).isEmpty();
        String sql = sqlCaptor.getValue();
        assertThat(sql).contains("ORDER BY CASE a.status WHEN 'DEPRECATED' THEN 1 ELSE 0 END");
        assertThat(sql.indexOf("DEPRECATED")).isLessThan(sql.indexOf("created_at DESC"));
    }

    @Test
    void searchReturnsDeprecatedAfterActiveWhenMapped() throws SQLException {
        when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<Object> mapper = invocation.getArgument(2);
                    ResultSet active = mockRow("ast_active", "ACTIVE", 2L);
                    ResultSet deprecated = mockRow("ast_deprecated", "DEPRECATED", 1L);
                    return List.of(mapper.mapRow(active, 0), mapper.mapRow(deprecated, 1));
                });
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of());

        AssetSearchCriteria criteria = new AssetSearchCriteria(
                null, null, null, null, null, null,
                null, null, null, null, null, null,
                Set.of(AssetStatus.ACTIVE, AssetStatus.DEPRECATED),
                Set.of(Visibility.PUBLIC),
                new AccessScope(null, true, Set.of(), Set.of(), Set.of()),
                null, null, null, null, null, null, null, 20);

        var page = dao.search(criteria);

        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).status()).isEqualTo(AssetStatus.ACTIVE);
        assertThat(page.items().get(1).status()).isEqualTo(AssetStatus.DEPRECATED);
    }

    private ResultSet mockRow(String assetId, String status, long id) throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("asset_id")).thenReturn(assetId);
        when(rs.getString("type")).thenReturn("MODEL");
        when(rs.getString("organization_id")).thenReturn("org_1");
        when(rs.getString("project_id")).thenReturn(null);
        when(rs.getString("namespace")).thenReturn("ai-lab");
        when(rs.getString("name")).thenReturn(assetId);
        when(rs.getString("display_name")).thenReturn(assetId);
        when(rs.getString("description")).thenReturn(null);
        when(rs.getString("visibility")).thenReturn("PUBLIC");
        when(rs.getString("status")).thenReturn(status);
        when(rs.getString("owners_json")).thenReturn("[]");
        when(rs.getString("tags_json")).thenReturn("[]");
        when(rs.getString("task_codes_json")).thenReturn("[]");
        when(rs.getString("license")).thenReturn(null);
        when(rs.getString("framework")).thenReturn(null);
        when(rs.getString("task")).thenReturn(null);
        when(rs.getString("format")).thenReturn(null);
        when(rs.getString("modality")).thenReturn(null);
        when(rs.getObject(eq("updated_at"), eq(OffsetDateTime.class)))
                .thenReturn(OffsetDateTime.parse("2026-07-01T00:00:00Z"));
        when(rs.getObject(eq("created_at"), eq(OffsetDateTime.class)))
                .thenReturn(OffsetDateTime.parse("2026-07-02T00:00:00Z"));
        when(rs.getLong("id")).thenReturn(id);
        return rs;
    }
}
