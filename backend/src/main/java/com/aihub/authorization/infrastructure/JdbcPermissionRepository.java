/*
 * 功能: 权限定义仓储 JDBC 实现，读取 iam_permission 并校验权限编码存在性。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.authorization.infrastructure;

import com.aihub.authorization.domain.PermissionDefinition;
import com.aihub.authorization.domain.PermissionRepository;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 权限定义仓储 JDBC 实现。
 */
@Repository
public class JdbcPermissionRepository implements PermissionRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcPermissionRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<PermissionDefinition> findAll() {
        return jdbcTemplate.query(
                "SELECT code, resource, action FROM iam_permission ORDER BY code",
                new MapSqlParameterSource(),
                (rs, rowNum) -> new PermissionDefinition(
                        rs.getString("code"), rs.getString("resource"), rs.getString("action")));
    }

    @Override
    public List<String> findUnknownCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        List<String> existing = jdbcTemplate.queryForList(
                "SELECT code FROM iam_permission WHERE code IN (:codes)",
                new MapSqlParameterSource("codes", codes),
                String.class);
        return codes.stream().distinct().filter(code -> !existing.contains(code)).toList();
    }
}
