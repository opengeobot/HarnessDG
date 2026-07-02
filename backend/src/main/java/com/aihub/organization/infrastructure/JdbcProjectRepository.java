/*
 * 功能: 项目仓储 JDBC 实现，读写 project 表并提供按组织查询。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.organization.infrastructure;

import com.aihub.organization.domain.OrganizationStatus;
import com.aihub.organization.domain.Project;
import com.aihub.organization.domain.ProjectRepository;
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
 * 项目仓储 JDBC 实现。
 */
@Repository
public class JdbcProjectRepository implements ProjectRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcProjectRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Project> findByProjectId(String projectId) {
        List<Project> result = jdbcTemplate.query(
                "SELECT project_id, organization_id, code, name, description, status, created_by, "
                        + "created_at, updated_at, row_version FROM project WHERE project_id = :projectId",
                new MapSqlParameterSource("projectId", projectId), this::mapProject);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public List<Project> findByOrganizationId(String organizationId) {
        return jdbcTemplate.query(
                "SELECT project_id, organization_id, code, name, description, status, created_by, "
                        + "created_at, updated_at, row_version FROM project "
                        + "WHERE organization_id = :organizationId ORDER BY created_at DESC, id DESC",
                new MapSqlParameterSource("organizationId", organizationId), this::mapProject);
    }

    @Override
    public boolean existsByOrganizationIdAndCode(String organizationId, String code) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(1) FROM project WHERE organization_id = :organizationId AND code = :code",
                new MapSqlParameterSource()
                        .addValue("organizationId", organizationId)
                        .addValue("code", code), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public void insert(Project project) {
        jdbcTemplate.update(
                "INSERT INTO project (project_id, organization_id, code, name, description, status, "
                        + "created_by, created_at, updated_at, row_version) "
                        + "VALUES (:projectId, :organizationId, :code, :name, :description, :status, "
                        + ":createdBy, :createdAt, :updatedAt, :rowVersion)",
                new MapSqlParameterSource()
                        .addValue("projectId", project.projectId())
                        .addValue("organizationId", project.organizationId())
                        .addValue("code", project.code())
                        .addValue("name", project.name())
                        .addValue("description", project.description())
                        .addValue("status", project.status().name())
                        .addValue("createdBy", project.createdBy())
                        .addValue("createdAt", toOffsetDateTime(project.createdAt()))
                        .addValue("updatedAt", toOffsetDateTime(project.updatedAt()))
                        .addValue("rowVersion", project.rowVersion()));
    }

    private Project mapProject(ResultSet rs, int rowNum) throws SQLException {
        return new Project(
                rs.getString("project_id"),
                rs.getString("organization_id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
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
