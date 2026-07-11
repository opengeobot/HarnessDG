/*
 * 功能: 资产血缘关系 JDBC 仓储实现。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.asset.infrastructure;

import com.aihub.asset.domain.AssetRelation;
import com.aihub.asset.domain.AssetRelationRepository;
import com.aihub.asset.domain.AssetRelationType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 资产血缘关系 JDBC 仓储。
 */
@Repository
public class JdbcAssetRelationRepository implements AssetRelationRepository {

    private static final String SELECT_COLUMNS =
            "relation_id, parent_asset_id, child_asset_id, relation_type, created_by, created_at";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAssetRelationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AssetRelation> findByParentAssetId(String parentAssetId) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM asset_relation WHERE parent_asset_id = :assetId",
                new MapSqlParameterSource("assetId", parentAssetId),
                this::mapRelation);
    }

    @Override
    public List<AssetRelation> findByChildAssetId(String childAssetId) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM asset_relation WHERE child_asset_id = :assetId",
                new MapSqlParameterSource("assetId", childAssetId),
                this::mapRelation);
    }

    private AssetRelation mapRelation(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
        return new AssetRelation(
                rs.getString("relation_id"),
                rs.getString("parent_asset_id"),
                rs.getString("child_asset_id"),
                AssetRelationType.valueOf(rs.getString("relation_type")),
                rs.getString("created_by"),
                createdAt == null ? null : createdAt.toInstant());
    }
}
