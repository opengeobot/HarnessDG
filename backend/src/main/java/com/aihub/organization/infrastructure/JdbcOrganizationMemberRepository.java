/*
 * 功能: 组织成员仓储 JDBC 实现，读写 organization_member 表并在数据库阶段下推成员隔离查询。
 *       同时实现跨模块出站端口 OrganizationMembershipPort，供 authorization 模块接通 AccessScope。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.organization.infrastructure;

import com.aihub.organization.application.OrganizationMembershipPort;
import com.aihub.organization.domain.MemberRole;
import com.aihub.organization.domain.OrganizationMember;
import com.aihub.organization.domain.OrganizationMemberRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 组织成员仓储 JDBC 实现。
 *
 * <p>同时实现领域端口 {@link OrganizationMemberRepository} 与跨模块出站端口
 * {@link OrganizationMembershipPort}：按 principalId 解析所属组织 ID 的作用域查询在数据库阶段完成，
 * 供 {@code AuthorizationService} 的 AccessScope 与资产等下推过滤使用。
 */
@Repository
public class JdbcOrganizationMemberRepository implements OrganizationMemberRepository, OrganizationMembershipPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcOrganizationMemberRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<OrganizationMember> findByOrganizationId(String organizationId) {
        return jdbcTemplate.query(
                "SELECT organization_id, principal_id, role, created_by, created_at "
                        + "FROM organization_member WHERE organization_id = :organizationId "
                        + "ORDER BY created_at DESC, id DESC",
                new MapSqlParameterSource("organizationId", organizationId), this::mapMember);
    }

    @Override
    public boolean exists(String organizationId, String principalId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(1) FROM organization_member "
                        + "WHERE organization_id = :organizationId AND principal_id = :principalId",
                new MapSqlParameterSource()
                        .addValue("organizationId", organizationId)
                        .addValue("principalId", principalId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public void add(OrganizationMember member) {
        jdbcTemplate.update(
                "INSERT INTO organization_member (organization_id, principal_id, role, created_by, created_at) "
                        + "VALUES (:organizationId, :principalId, :role, :createdBy, :createdAt)",
                new MapSqlParameterSource()
                        .addValue("organizationId", member.organizationId())
                        .addValue("principalId", member.principalId())
                        .addValue("role", member.role().name())
                        .addValue("createdBy", member.createdBy())
                        .addValue("createdAt", toOffsetDateTime(member.createdAt())));
    }

    @Override
    public boolean remove(String organizationId, String principalId) {
        int affected = jdbcTemplate.update(
                "DELETE FROM organization_member "
                        + "WHERE organization_id = :organizationId AND principal_id = :principalId",
                new MapSqlParameterSource()
                        .addValue("organizationId", organizationId)
                        .addValue("principalId", principalId));
        return affected > 0;
    }

    @Override
    public Set<String> findOrganizationIdsByPrincipal(String principalId) {
        List<String> ids = jdbcTemplate.queryForList(
                "SELECT DISTINCT organization_id FROM organization_member WHERE principal_id = :principalId",
                new MapSqlParameterSource("principalId", principalId), String.class);
        return new HashSet<>(ids);
    }

    private OrganizationMember mapMember(ResultSet rs, int rowNum) throws SQLException {
        return new OrganizationMember(
                rs.getString("organization_id"),
                rs.getString("principal_id"),
                MemberRole.valueOf(rs.getString("role")),
                rs.getString("created_by"),
                toInstant(rs.getObject("created_at", OffsetDateTime.class)));
    }

    private static OffsetDateTime toOffsetDateTime(java.time.Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static java.time.Instant toInstant(OffsetDateTime offsetDateTime) {
        return offsetDateTime == null ? null : offsetDateTime.toInstant();
    }
}
