/*
 * 功能: Team 仓储 JDBC 实现，读写 team / team_member 表。
 *       同时实现跨模块出站端口 TeamMembershipPort，供 authorization 模块接通 AccessScope.teamIds。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.organization.infrastructure;

import com.aihub.organization.application.TeamMembershipPort;
import com.aihub.organization.domain.Team;
import com.aihub.organization.domain.TeamMember;
import com.aihub.organization.domain.TeamRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
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
 * Team 仓储 JDBC 实现。
 *
 * <p>同时实现领域端口 {@link TeamRepository} 与跨模块出站端口 {@link TeamMembershipPort}：
 * 按 principalId 解析所属 Team ID 的作用域查询在数据库阶段完成，供 {@code AuthorizationService}
 * 的 AccessScope 与资产 PRIVATE 可见性的 Owner 判定使用。
 */
@Repository
public class JdbcTeamRepository implements TeamRepository, TeamMembershipPort {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcTeamRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Team> findByTeamId(String teamId) {
        List<Team> result = jdbcTemplate.query(
                "SELECT team_id, organization_id, name, description, status, "
                        + "created_by, created_at, updated_at, row_version "
                        + "FROM team WHERE team_id = :teamId",
                new MapSqlParameterSource("teamId", teamId), this::mapTeam);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public List<Team> findByOrganizationId(String organizationId) {
        return jdbcTemplate.query(
                "SELECT team_id, organization_id, name, description, status, "
                        + "created_by, created_at, updated_at, row_version "
                        + "FROM team WHERE organization_id = :organizationId "
                        + "ORDER BY created_at DESC, id DESC",
                new MapSqlParameterSource("organizationId", organizationId), this::mapTeam);
    }

    @Override
    public boolean existsByName(String organizationId, String name) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(1) FROM team WHERE organization_id = :organizationId AND name = :name",
                new MapSqlParameterSource()
                        .addValue("organizationId", organizationId)
                        .addValue("name", name), Integer.class);
        return count != null && count > 0;
    }

    @Override
    public void insert(Team team) {
        jdbcTemplate.update(
                "INSERT INTO team (team_id, organization_id, name, description, status, "
                        + "created_by, created_at, updated_at, row_version) "
                        + "VALUES (:teamId, :organizationId, :name, :description, :status, "
                        + ":createdBy, :createdAt, :updatedAt, :rowVersion)",
                new MapSqlParameterSource()
                        .addValue("teamId", team.teamId())
                        .addValue("organizationId", team.organizationId())
                        .addValue("name", team.name())
                        .addValue("description", team.description())
                        .addValue("status", team.status())
                        .addValue("createdBy", team.createdBy())
                        .addValue("createdAt", toOffsetDateTime(team.createdAt()))
                        .addValue("updatedAt", toOffsetDateTime(team.updatedAt()))
                        .addValue("rowVersion", team.rowVersion()));
    }

    @Override
    public void update(Team team) {
        int rows = jdbcTemplate.update(
                "UPDATE team SET name = :name, description = :description, status = :status, "
                        + "updated_at = :updatedAt, row_version = row_version + 1 "
                        + "WHERE team_id = :teamId AND row_version = :rowVersion",
                new MapSqlParameterSource()
                        .addValue("name", team.name())
                        .addValue("description", team.description())
                        .addValue("status", team.status())
                        .addValue("updatedAt", toOffsetDateTime(team.updatedAt()))
                        .addValue("teamId", team.teamId())
                        .addValue("rowVersion", team.rowVersion()));
        if (rows == 0) {
            throw new com.aihub.shared.error.ConflictException(
                    com.aihub.shared.error.ErrorCode.CONCURRENT_MODIFICATION,
                    "Team version conflict: " + team.teamId(),
                    java.util.Map.of("teamId", team.teamId()));
        }
    }

    @Override
    public List<TeamMember> findMembersByTeamId(String teamId) {
        return jdbcTemplate.query(
                "SELECT team_id, principal_id, role, created_by, joined_at "
                        + "FROM team_member WHERE team_id = :teamId ORDER BY joined_at",
                new MapSqlParameterSource("teamId", teamId), this::mapMember);
    }

    @Override
    public Optional<TeamMember> findMember(String teamId, String principalId) {
        List<TeamMember> result = jdbcTemplate.query(
                "SELECT team_id, principal_id, role, created_by, joined_at "
                        + "FROM team_member WHERE team_id = :teamId AND principal_id = :principalId",
                new MapSqlParameterSource()
                        .addValue("teamId", teamId)
                        .addValue("principalId", principalId), this::mapMember);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    @Override
    public void addMember(TeamMember member) {
        jdbcTemplate.update(
                "INSERT INTO team_member (team_id, principal_id, role, created_by, joined_at) "
                        + "VALUES (:teamId, :principalId, :role, :createdBy, :joinedAt)",
                new MapSqlParameterSource()
                        .addValue("teamId", member.teamId())
                        .addValue("principalId", member.principalId())
                        .addValue("role", member.role())
                        .addValue("createdBy", member.createdBy())
                        .addValue("joinedAt", toOffsetDateTime(member.joinedAt())));
    }

    @Override
    public void removeMember(String teamId, String principalId) {
        jdbcTemplate.update(
                "DELETE FROM team_member WHERE team_id = :teamId AND principal_id = :principalId",
                new MapSqlParameterSource()
                        .addValue("teamId", teamId)
                        .addValue("principalId", principalId));
    }

    @Override
    public Set<String> findTeamIdsByPrincipal(String principalId) {
        List<String> ids = jdbcTemplate.queryForList(
                "SELECT DISTINCT team_id FROM team_member WHERE principal_id = :principalId",
                new MapSqlParameterSource("principalId", principalId), String.class);
        return new HashSet<>(ids);
    }

    private Team mapTeam(ResultSet rs, int rowNum) throws SQLException {
        return new Team(
                rs.getString("team_id"),
                rs.getString("organization_id"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("status"),
                rs.getString("created_by"),
                toInstant(rs.getObject("created_at", OffsetDateTime.class)),
                toInstant(rs.getObject("updated_at", OffsetDateTime.class)),
                rs.getInt("row_version"));
    }

    private TeamMember mapMember(ResultSet rs, int rowNum) throws SQLException {
        return new TeamMember(
                rs.getString("team_id"),
                rs.getString("principal_id"),
                rs.getString("role"),
                rs.getString("created_by"),
                toInstant(rs.getObject("joined_at", OffsetDateTime.class)));
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant toInstant(OffsetDateTime odt) {
        return odt == null ? null : odt.toInstant();
    }
}
