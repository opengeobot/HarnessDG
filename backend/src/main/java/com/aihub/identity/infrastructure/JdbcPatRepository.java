/*
 * 功能: PAT JDBC 仓储实现，读写 iam_token 表中 token_type=PAT 记录。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.identity.infrastructure;

import com.aihub.identity.domain.PatRepository;
import com.aihub.identity.domain.PatStatus;
import com.aihub.identity.domain.PersonalAccessToken;
import com.aihub.platform.security.JtiDigest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * PAT JDBC 仓储。
 */
@Repository
public class JdbcPatRepository implements PatRepository {

    private static final String PAT_TYPE = "PAT";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcPatRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(PersonalAccessToken pat) {
        String scopesJson = toScopesJson(pat.scopes());
        jdbcTemplate.update("""
                INSERT INTO iam_token (
                    token_id, jti, jti_digest, token_family, principal_id,
                    token_type, name, scopes, status, issued_at, expires_at, last_used_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                """,
                pat.tokenId(),
                pat.jti(),
                JtiDigest.sha256Hex(pat.jti()),
                pat.tokenId(),
                pat.principalId(),
                PAT_TYPE,
                pat.name(),
                scopesJson,
                pat.status().name(),
                Timestamp.from(pat.issuedAt()),
                Timestamp.from(pat.expiresAt()),
                pat.lastUsedAt() == null ? null : Timestamp.from(pat.lastUsedAt()));
    }

    @Override
    public Optional<PersonalAccessToken> findByTokenId(String tokenId) {
        List<PersonalAccessToken> rows = jdbcTemplate.query(
                selectSql() + " WHERE token_id = ? AND token_type = ?",
                rowMapper(), tokenId, PAT_TYPE);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public Optional<PersonalAccessToken> findByJti(String jti) {
        String digest = JtiDigest.sha256Hex(jti);
        List<PersonalAccessToken> rows = jdbcTemplate.query(
                selectSql() + " WHERE (jti = ? OR jti_digest = ?) AND token_type = ?",
                rowMapper(), jti, digest, PAT_TYPE);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public List<PersonalAccessToken> listByPrincipalId(String principalId) {
        return jdbcTemplate.query(
                selectSql() + " WHERE principal_id = ? AND token_type = ? ORDER BY issued_at DESC",
                rowMapper(), principalId, PAT_TYPE);
    }

    @Override
    public int revoke(String tokenId, String principalId) {
        return jdbcTemplate.update("""
                UPDATE iam_token
                SET status = ?, updated_at = now()
                WHERE token_id = ? AND principal_id = ? AND token_type = ? AND status = ?
                """,
                PatStatus.REVOKED.name(), tokenId, principalId, PAT_TYPE, PatStatus.ACTIVE.name());
    }

    private String selectSql() {
        return """
                SELECT token_id, jti, principal_id, name, scopes, status,
                       issued_at, expires_at, last_used_at
                FROM iam_token
                """;
    }

    private RowMapper<PersonalAccessToken> rowMapper() {
        return (rs, rowNum) -> mapRow(rs);
    }

    private PersonalAccessToken mapRow(ResultSet rs) throws SQLException {
        return new PersonalAccessToken(
                rs.getString("token_id"),
                rs.getString("jti"),
                rs.getString("principal_id"),
                rs.getString("name"),
                parseScopes(rs.getString("scopes")),
                PatStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("issued_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("last_used_at") == null
                        ? null : rs.getTimestamp("last_used_at").toInstant());
    }

    private Set<String> parseScopes(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<>() {});
            return Set.copyOf(new LinkedHashSet<>(list));
        } catch (Exception ex) {
            return Set.of();
        }
    }

    private String toScopesJson(Set<String> scopes) {
        try {
            return objectMapper.writeValueAsString(scopes == null ? List.of() : List.copyOf(scopes));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize scopes", ex);
        }
    }
}
