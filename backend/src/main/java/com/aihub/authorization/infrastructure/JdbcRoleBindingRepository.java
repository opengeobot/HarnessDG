/*
 * 功能: 角色绑定仓储 JDBC 实现，读写 iam_role_binding 并在数据库阶段解析主体权限与作用域集合。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.infrastructure;

import com.aihub.authorization.domain.RoleBinding;
import com.aihub.authorization.domain.RoleBindingRepository;
import com.aihub.authorization.domain.ScopeType;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 角色绑定仓储 JDBC 实现。
 *
 * <p>权限解析在库内 join 完成（绑定→角色→权限），仅返回去重后的权限编码集合，避免全量加载到内存过滤。
 */
@Repository
public class JdbcRoleBindingRepository implements RoleBindingRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcRoleBindingRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<RoleBinding> findAll() {
        return jdbcTemplate.query("SELECT binding_id, principal_id, role_id, scope_type, scope_id, "
                        + "created_by, created_at FROM iam_role_binding ORDER BY created_at DESC, id DESC",
                new MapSqlParameterSource(), this::mapBinding);
    }

    @Override
    public Optional<RoleBinding> findByBindingId(String bindingId) {
        List<RoleBinding> result = jdbcTemplate.query("SELECT binding_id, principal_id, role_id, scope_type, "
                        + "scope_id, created_by, created_at FROM iam_role_binding WHERE binding_id = :bindingId",
                new MapSqlParameterSource("bindingId", bindingId), this::mapBinding);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public boolean exists(String principalId, String roleId, ScopeType scopeType, String scopeId) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(1) FROM iam_role_binding WHERE "
                        + "principal_id = :principalId AND role_id = :roleId AND scope_type = :scopeType "
                        + "AND COALESCE(scope_id, '') = COALESCE(:scopeId, '')",
                new MapSqlParameterSource()
                        .addValue("principalId", principalId)
                        .addValue("roleId", roleId)
                        .addValue("scopeType", scopeType.name())
                        .addValue("scopeId", scopeId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public void create(RoleBinding binding) {
        jdbcTemplate.update("INSERT INTO iam_role_binding (binding_id, principal_id, role_id, scope_type, "
                        + "scope_id, created_by, created_at) VALUES (:bindingId, :principalId, :roleId, "
                        + ":scopeType, :scopeId, :createdBy, :createdAt)",
                new MapSqlParameterSource()
                        .addValue("bindingId", binding.bindingId())
                        .addValue("principalId", binding.principalId())
                        .addValue("roleId", binding.roleId())
                        .addValue("scopeType", binding.scopeType().name())
                        .addValue("scopeId", binding.scopeId())
                        .addValue("createdBy", binding.createdBy())
                        .addValue("createdAt", binding.createdAt() == null ? null
                                : OffsetDateTime.ofInstant(binding.createdAt(), ZoneOffset.UTC)));
    }

    @Override
    public void deleteByBindingId(String bindingId) {
        jdbcTemplate.update("DELETE FROM iam_role_binding WHERE binding_id = :bindingId",
                new MapSqlParameterSource("bindingId", bindingId));
    }

    @Override
    public Set<String> resolvePermissionCodes(String principalId) {
        List<String> codes = jdbcTemplate.queryForList("SELECT DISTINCT p.code FROM iam_role_binding rb "
                        + "JOIN iam_role r ON r.role_id = rb.role_id AND r.status = 'ACTIVE' "
                        + "JOIN iam_role_permission rp ON rp.role_id = r.role_id "
                        + "JOIN iam_permission p ON p.permission_id = rp.permission_id "
                        + "WHERE rb.principal_id = :principalId",
                new MapSqlParameterSource("principalId", principalId), String.class);
        return new HashSet<>(codes);
    }

    @Override
    public Set<String> resolveOrganizationScopes(String principalId) {
        return resolveScopes(principalId, ScopeType.ORGANIZATION);
    }

    @Override
    public Set<String> resolveProjectScopes(String principalId) {
        return resolveScopes(principalId, ScopeType.PROJECT);
    }

    private Set<String> resolveScopes(String principalId, ScopeType scopeType) {
        List<String> ids = jdbcTemplate.queryForList("SELECT DISTINCT scope_id FROM iam_role_binding "
                        + "WHERE principal_id = :principalId AND scope_type = :scopeType AND scope_id IS NOT NULL",
                new MapSqlParameterSource()
                        .addValue("principalId", principalId)
                        .addValue("scopeType", scopeType.name()), String.class);
        return new HashSet<>(ids);
    }

    private RoleBinding mapBinding(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
        return new RoleBinding(
                rs.getString("binding_id"),
                rs.getString("principal_id"),
                rs.getString("role_id"),
                ScopeType.valueOf(rs.getString("scope_type")),
                rs.getString("scope_id"),
                rs.getString("created_by"),
                createdAt == null ? null : createdAt.toInstant());
    }
}
