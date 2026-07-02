/*
 * 功能: 角色仓储 JDBC 实现，读写 iam_role 与 iam_role_permission，并解析角色权限编码集合。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.infrastructure;

import com.aihub.authorization.domain.Role;
import com.aihub.authorization.domain.RoleRepository;
import com.aihub.authorization.domain.ScopeType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色仓储 JDBC 实现。
 *
 * <p>角色与其权限编码集合一同读写；更新走 row_version 乐观锁。查询先取角色行再加载权限集合，
 * 避免在打开的 ResultSet 上发起嵌套查询。
 */
@Repository
public class JdbcRoleRepository implements RoleRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcRoleRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Role> findAll() {
        List<RoleRow> rows = jdbcTemplate.query(
                "SELECT role_id, code, name, description, scope_type, scope_id, builtin, status, "
                        + "created_at, updated_at, row_version FROM iam_role ORDER BY code",
                new MapSqlParameterSource(), this::mapRow);
        List<Role> roles = new ArrayList<>(rows.size());
        for (RoleRow row : rows) {
            roles.add(toRole(row, loadPermissionCodes(row.roleId())));
        }
        return List.copyOf(roles);
    }

    @Override
    public Optional<Role> findByRoleId(String roleId) {
        return findOne("role_id", roleId);
    }

    @Override
    public Optional<Role> findByCode(String code) {
        return findOne("code", code);
    }

    @Override
    public boolean existsByCode(String code) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(1) FROM iam_role WHERE code = :code",
                new MapSqlParameterSource("code", code), Integer.class);
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void create(Role role) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("roleId", role.roleId())
                .addValue("code", role.code())
                .addValue("name", role.name())
                .addValue("description", role.description())
                .addValue("scopeType", role.scopeType().name())
                .addValue("scopeId", role.scopeId())
                .addValue("builtin", role.builtin() ? 1 : 0)
                .addValue("status", role.status())
                .addValue("createdAt", toOffset(role.createdAt()))
                .addValue("updatedAt", toOffset(role.updatedAt()))
                .addValue("rowVersion", role.rowVersion());
        jdbcTemplate.update("INSERT INTO iam_role (role_id, code, name, description, scope_type, scope_id, "
                + "builtin, status, created_at, updated_at, row_version) VALUES (:roleId, :code, :name, "
                + ":description, :scopeType, :scopeId, :builtin, :status, :createdAt, :updatedAt, :rowVersion)",
                params);
        insertPermissions(role.roleId(), role.permissionCodes());
    }

    @Override
    @Transactional
    public boolean update(Role role) {
        long previousVersion = role.rowVersion() - 1;
        int affected = jdbcTemplate.update("UPDATE iam_role SET name = :name, description = :description, "
                        + "updated_at = :updatedAt, row_version = :rowVersion "
                        + "WHERE role_id = :roleId AND row_version = :previousVersion",
                new MapSqlParameterSource()
                        .addValue("name", role.name())
                        .addValue("description", role.description())
                        .addValue("updatedAt", toOffset(role.updatedAt()))
                        .addValue("rowVersion", role.rowVersion())
                        .addValue("roleId", role.roleId())
                        .addValue("previousVersion", previousVersion));
        if (affected == 0) {
            return false;
        }
        jdbcTemplate.update("DELETE FROM iam_role_permission WHERE role_id = :roleId",
                new MapSqlParameterSource("roleId", role.roleId()));
        insertPermissions(role.roleId(), role.permissionCodes());
        return true;
    }

    @Override
    @Transactional
    public void deleteByRoleId(String roleId) {
        // iam_role_permission 与 iam_role_binding 均 ON DELETE CASCADE，删主表即可。
        jdbcTemplate.update("DELETE FROM iam_role WHERE role_id = :roleId",
                new MapSqlParameterSource("roleId", roleId));
    }

    @Override
    public long countBindingsByRoleId(String roleId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(1) FROM iam_role_binding WHERE role_id = :roleId",
                new MapSqlParameterSource("roleId", roleId), Long.class);
        return count == null ? 0L : count;
    }

    private void insertPermissions(String roleId, Set<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return;
        }
        for (String code : permissionCodes) {
            jdbcTemplate.update("INSERT INTO iam_role_permission (role_id, permission_id) "
                            + "SELECT :roleId, permission_id FROM iam_permission WHERE code = :code "
                            + "ON CONFLICT (role_id, permission_id) DO NOTHING",
                    new MapSqlParameterSource().addValue("roleId", roleId).addValue("code", code));
        }
    }

    private Set<String> loadPermissionCodes(String roleId) {
        List<String> codes = jdbcTemplate.queryForList("SELECT p.code FROM iam_role_permission rp "
                        + "JOIN iam_permission p ON p.permission_id = rp.permission_id "
                        + "WHERE rp.role_id = :roleId ORDER BY p.code",
                new MapSqlParameterSource("roleId", roleId), String.class);
        return new TreeSet<>(codes);
    }

    private Optional<Role> findOne(String column, String value) {
        List<RoleRow> rows = jdbcTemplate.query(
                "SELECT role_id, code, name, description, scope_type, scope_id, builtin, status, "
                        + "created_at, updated_at, row_version FROM iam_role WHERE " + column + " = :value",
                new MapSqlParameterSource("value", value), this::mapRow);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        RoleRow row = rows.get(0);
        return Optional.of(toRole(row, loadPermissionCodes(row.roleId())));
    }

    private Role toRole(RoleRow row, Set<String> permissionCodes) {
        return new Role(row.roleId(), row.code(), row.name(), row.description(), row.scopeType(),
                row.scopeId(), row.builtin(), row.status(), permissionCodes,
                row.createdAt(), row.updatedAt(), row.rowVersion());
    }

    private RoleRow mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new RoleRow(
                rs.getString("role_id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                ScopeType.valueOf(rs.getString("scope_type")),
                rs.getString("scope_id"),
                rs.getInt("builtin") == 1,
                rs.getString("status"),
                toInstant(rs.getObject("created_at", OffsetDateTime.class)),
                toInstant(rs.getObject("updated_at", OffsetDateTime.class)),
                rs.getLong("row_version"));
    }

    private OffsetDateTime toOffset(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private record RoleRow(String roleId, String code, String name, String description,
                           ScopeType scopeType, String scopeId, boolean builtin, String status,
                           Instant createdAt, Instant updatedAt, long rowVersion) {
    }
}
