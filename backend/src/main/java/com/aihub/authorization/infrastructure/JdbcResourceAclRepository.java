/*
 * 功能: 资源 ACL 仓储 JDBC 实现，读写 iam_resource_acl 并支持分组聚合、命中判定与资源 ID 下推查询。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.infrastructure;

import com.aihub.authorization.domain.ResourceAcl;
import com.aihub.authorization.domain.ResourceAclRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 资源 ACL 仓储 JDBC 实现。
 *
 * <p>同一 (资源类型,资源ID,主体) 的多条权限共享 acl_id；查询按 acl_id 分组聚合权限集合，
 * 写入为每个权限一行，删除按 acl_id 整组删除。
 */
@Repository
public class JdbcResourceAclRepository implements ResourceAclRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcResourceAclRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ResourceAcl> findAll() {
        return groupByAclId(jdbcTemplate.query(
                "SELECT acl_id, resource_type, resource_id, principal_id, permission, created_by, created_at "
                        + "FROM iam_resource_acl ORDER BY created_at DESC, id DESC",
                new MapSqlParameterSource(), this::mapRow));
    }

    @Override
    public List<ResourceAcl> findByResource(String resourceType, String resourceId) {
        return groupByAclId(jdbcTemplate.query(
                "SELECT acl_id, resource_type, resource_id, principal_id, permission, created_by, created_at "
                        + "FROM iam_resource_acl WHERE resource_type = :resourceType AND resource_id = :resourceId "
                        + "ORDER BY created_at DESC, id DESC",
                new MapSqlParameterSource()
                        .addValue("resourceType", resourceType)
                        .addValue("resourceId", resourceId),
                this::mapRow));
    }

    @Override
    public Optional<ResourceAcl> findByAclId(String aclId) {
        List<AclRow> rows = jdbcTemplate.query(
                "SELECT acl_id, resource_type, resource_id, principal_id, permission, created_by, created_at "
                        + "FROM iam_resource_acl WHERE acl_id = :aclId ORDER BY id",
                new MapSqlParameterSource("aclId", aclId), this::mapRow);
        return groupByAclId(rows).stream().findFirst();
    }

    @Override
    public boolean exists(String resourceType, String resourceId, String principalId, String permission) {
        return hasPermission(resourceType, resourceId, principalId, permission);
    }

    @Override
    public void create(ResourceAcl acl) {
        if (acl.permissions() == null || acl.permissions().isEmpty()) {
            return;
        }
        OffsetDateTime createdAt = acl.createdAt() == null ? null
                : OffsetDateTime.ofInstant(acl.createdAt(), ZoneOffset.UTC);
        for (String permission : acl.permissions()) {
            jdbcTemplate.update("INSERT INTO iam_resource_acl (acl_id, resource_type, resource_id, principal_id, "
                            + "permission, created_by, created_at) VALUES (:aclId, :resourceType, :resourceId, "
                            + ":principalId, :permission, :createdBy, :createdAt)",
                    new MapSqlParameterSource()
                            .addValue("aclId", acl.aclId())
                            .addValue("resourceType", acl.resourceType())
                            .addValue("resourceId", acl.resourceId())
                            .addValue("principalId", acl.principalId())
                            .addValue("permission", permission)
                            .addValue("createdBy", acl.createdBy())
                            .addValue("createdAt", createdAt));
        }
    }

    @Override
    public void deleteByAclId(String aclId) {
        jdbcTemplate.update("DELETE FROM iam_resource_acl WHERE acl_id = :aclId",
                new MapSqlParameterSource("aclId", aclId));
    }

    @Override
    public boolean hasPermission(String resourceType, String resourceId, String principalId, String permission) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(1) FROM iam_resource_acl WHERE "
                        + "resource_type = :resourceType AND resource_id = :resourceId "
                        + "AND principal_id = :principalId AND permission = :permission",
                new MapSqlParameterSource()
                        .addValue("resourceType", resourceType)
                        .addValue("resourceId", resourceId)
                        .addValue("principalId", principalId)
                        .addValue("permission", permission), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public Set<String> accessibleResourceIds(String resourceType, String principalId, String permission) {
        List<String> ids = jdbcTemplate.queryForList("SELECT DISTINCT resource_id FROM iam_resource_acl WHERE "
                        + "resource_type = :resourceType AND principal_id = :principalId AND permission = :permission",
                new MapSqlParameterSource()
                        .addValue("resourceType", resourceType)
                        .addValue("principalId", principalId)
                        .addValue("permission", permission), String.class);
        return new HashSet<>(ids);
    }

    private List<ResourceAcl> groupByAclId(List<AclRow> rows) {
        Map<String, ResourceAclBuilder> groups = new LinkedHashMap<>();
        for (AclRow row : rows) {
            ResourceAclBuilder builder = groups.computeIfAbsent(row.aclId, k -> new ResourceAclBuilder(row));
            builder.permissions.add(row.permission);
            if (builder.createdAt == null) {
                builder.createdAt = row.createdAt;
            }
        }
        List<ResourceAcl> result = new ArrayList<>(groups.size());
        for (ResourceAclBuilder builder : groups.values()) {
            result.add(new ResourceAcl(builder.aclId, builder.resourceType, builder.resourceId,
                    builder.principalId, Set.copyOf(builder.permissions), builder.createdBy,
                    builder.createdAt));
        }
        return result;
    }

    private AclRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
        return new AclRow(
                rs.getString("acl_id"),
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                rs.getString("principal_id"),
                rs.getString("permission"),
                rs.getString("created_by"),
                createdAt == null ? null : createdAt.toInstant());
    }

    private record AclRow(String aclId, String resourceType, String resourceId, String principalId,
                          String permission, String createdBy, java.time.Instant createdAt) {
    }

    private static final class ResourceAclBuilder {
        private final String aclId;
        private final String resourceType;
        private final String resourceId;
        private final String principalId;
        private final String createdBy;
        private final List<String> permissions = new ArrayList<>();
        private java.time.Instant createdAt;

        ResourceAclBuilder(AclRow row) {
            this.aclId = row.aclId;
            this.resourceType = row.resourceType;
            this.resourceId = row.resourceId;
            this.principalId = row.principalId;
            this.createdBy = row.createdBy;
            this.createdAt = row.createdAt;
        }
    }
}
