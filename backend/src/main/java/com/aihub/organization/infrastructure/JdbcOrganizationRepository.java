/*
 * 功能: 组织仓储 JDBC 实现，读写 organization 表并在数据库阶段下推成员隔离查询。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.organization.infrastructure;

import com.aihub.organization.domain.Organization;
import com.aihub.organization.domain.OrganizationRepository;
import com.aihub.organization.domain.OrganizationStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 组织仓储 JDBC 实现。
 *
 * <p>成员隔离列表查询通过 join organization_member 在数据库阶段过滤，仅返回主体所属组织。
 */
@Repository
public class JdbcOrganizationRepository implements OrganizationRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcOrganizationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Organization> findByOrganizationId(String organizationId) {
        List<Organization> result = jdbcTemplate.query(
                "SELECT organization_id, code, name, description, gitea_organization, status, "
                        + "created_by, created_at, updated_at, row_version FROM organization "
                        + "WHERE organization_id = :organizationId",
                new MapSqlParameterSource("organizationId", organizationId), this::mapOrganization);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public List<Organization> findAll() {
        return jdbcTemplate.query(
                "SELECT organization_id, code, name, description, gitea_organization, status, "
                        + "created_by, created_at, updated_at, row_version FROM organization "
                        + "ORDER BY created_at DESC, id DESC",
                new MapSqlParameterSource(), this::mapOrganization);
    }

    @Override
    public List<Organization> findByPrincipalMembership(String principalId) {
        return jdbcTemplate.query(
                "SELECT o.organization_id, o.code, o.name, o.description, o.gitea_organization, "
                        + "o.status, o.created_by, o.created_at, o.updated_at, o.row_version "
                        + "FROM organization o JOIN organization_member m ON m.organization_id = o.organization_id "
                        + "WHERE m.principal_id = :principalId ORDER BY o.created_at DESC, o.id DESC",
                new MapSqlParameterSource("principalId", principalId), this::mapOrganization);
    }

    @Override
    public boolean existsByCode(String code) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(1) FROM organization WHERE code = :code",
                new MapSqlParameterSource("code", code), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public boolean existsByOrganizationId(String organizationId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(1) FROM organization WHERE organization_id = :organizationId",
                new MapSqlParameterSource("organizationId", organizationId), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public void insert(Organization org) {
        jdbcTemplate.update(
                "INSERT INTO organization (organization_id, code, name, description, gitea_organization, "
                        + "status, created_by, created_at, updated_at, row_version) "
                        + "VALUES (:organizationId, :code, :name, :description, :giteaOrganization, "
                        + ":status, :createdBy, :createdAt, :updatedAt, :rowVersion)",
                new MapSqlParameterSource()
                        .addValue("organizationId", org.organizationId())
                        .addValue("code", org.code())
                        .addValue("name", org.name())
                        .addValue("description", org.description())
                        .addValue("giteaOrganization", org.giteaOrganization())
                        .addValue("status", org.status().name())
                        .addValue("createdBy", org.createdBy())
                        .addValue("createdAt", toOffsetDateTime(org.createdAt()))
                        .addValue("updatedAt", toOffsetDateTime(org.updatedAt()))
                        .addValue("rowVersion", org.rowVersion()));
    }

    private Organization mapOrganization(ResultSet rs, int rowNum) throws SQLException {
        return new Organization(
                rs.getString("organization_id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("gitea_organization"),
                OrganizationStatus.valueOf(rs.getString("status")),
                rs.getString("created_by"),
                toInstant(rs.getObject("created_at", OffsetDateTime.class)),
                toInstant(rs.getObject("updated_at", OffsetDateTime.class)),
                rs.getInt("row_version"));
    }

    private static OffsetDateTime toOffsetDateTime(java.time.Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static java.time.Instant toInstant(OffsetDateTime offsetDateTime) {
        return offsetDateTime == null ? null : offsetDateTime.toInstant();
    }
}
