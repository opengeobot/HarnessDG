/*
 * 功能: upload_file / upload_part JDBC 仓储实现。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.transfer.infrastructure;

import com.aihub.transfer.domain.UploadFile;
import com.aihub.transfer.domain.UploadFile.UploadFileStatus;
import com.aihub.transfer.domain.UploadFileRepository;
import com.aihub.transfer.domain.UploadPart;
import com.aihub.transfer.domain.UploadPartRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** upload_file 表 JDBC 仓储。 */
@Repository
public class JdbcUploadFileRepository implements UploadFileRepository {

    private static final RowMapper<UploadFile> ROW_MAPPER = (rs, rowNum) -> mapFile(rs);

    private final JdbcTemplate jdbcTemplate;

    public JdbcUploadFileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(UploadFile file) {
        jdbcTemplate.update("""
                INSERT INTO upload_file (file_id, session_id, path, size, sha256, media_type, part_count, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                file.fileId(), file.sessionId(), file.path(), file.size(),
                file.sha256(), file.mediaType(), file.partCount(), file.status().name());
    }

    @Override
    public void update(UploadFile file) {
        jdbcTemplate.update("""
                UPDATE upload_file SET path = ?, size = ?, sha256 = ?, media_type = ?,
                    part_count = ?, status = ? WHERE file_id = ?
                """,
                file.path(), file.size(), file.sha256(), file.mediaType(),
                file.partCount(), file.status().name(), file.fileId());
    }

    @Override
    public Optional<UploadFile> findByFileId(String fileId) {
        List<UploadFile> rows = jdbcTemplate.query(
                "SELECT file_id, session_id, path, size, sha256, media_type, part_count, status "
                        + "FROM upload_file WHERE file_id = ?",
                ROW_MAPPER, fileId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<UploadFile> listBySessionId(String sessionId) {
        return jdbcTemplate.query(
                "SELECT file_id, session_id, path, size, sha256, media_type, part_count, status "
                        + "FROM upload_file WHERE session_id = ? ORDER BY file_id",
                ROW_MAPPER, sessionId);
    }

    @Override
    public Optional<UploadFile> findSessionBundleFile(String sessionId) {
        List<UploadFile> rows = jdbcTemplate.query(
                "SELECT file_id, session_id, path, size, sha256, media_type, part_count, status "
                        + "FROM upload_file WHERE session_id = ? AND path = ? LIMIT 1",
                ROW_MAPPER, sessionId, "_session_bundle");
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private static UploadFile mapFile(ResultSet rs) throws SQLException {
        return new UploadFile(
                rs.getString("file_id"),
                rs.getString("session_id"),
                rs.getString("path"),
                rs.getLong("size"),
                rs.getString("sha256"),
                rs.getString("media_type"),
                rs.getInt("part_count"),
                UploadFileStatus.valueOf(rs.getString("status")));
    }
}

/** upload_part 表 JDBC 仓储。 */
@Repository
class JdbcUploadPartRepository implements UploadPartRepository {

    private static final RowMapper<UploadPart> ROW_MAPPER = (rs, rowNum) -> mapPart(rs);

    private final JdbcTemplate jdbcTemplate;

    JdbcUploadPartRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void upsert(UploadPart part) {
        jdbcTemplate.update("""
                INSERT INTO upload_part (file_id, part_number, size, etag, presigned_url, uploaded_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (file_id, part_number) DO UPDATE SET
                    presigned_url = EXCLUDED.presigned_url,
                    size = COALESCE(EXCLUDED.size, upload_part.size)
                """,
                part.fileId(), part.partNumber(), part.size(), part.etag(),
                part.presignedUrl(), part.uploadedAt() == null ? null : Timestamp.from(part.uploadedAt()));
    }

    @Override
    public void markUploaded(String fileId, int partNumber, String etag, long size) {
        jdbcTemplate.update("""
                UPDATE upload_part SET etag = ?, size = ?, uploaded_at = now()
                WHERE file_id = ? AND part_number = ?
                """, etag, size, fileId, partNumber);
    }

    @Override
    public List<UploadPart> listByFileId(String fileId) {
        return jdbcTemplate.query(
                "SELECT file_id, part_number, size, etag, presigned_url, uploaded_at "
                        + "FROM upload_part WHERE file_id = ? ORDER BY part_number",
                ROW_MAPPER, fileId);
    }

    @Override
    public int countPendingBySessionId(String sessionId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM upload_part up
                JOIN upload_file uf ON up.file_id = uf.file_id
                WHERE uf.session_id = ? AND up.uploaded_at IS NULL
                """, Integer.class, sessionId);
        return count == null ? 0 : count;
    }

    private static UploadPart mapPart(ResultSet rs) throws SQLException {
        Timestamp uploadedAt = rs.getTimestamp("uploaded_at");
        return new UploadPart(
                rs.getString("file_id"),
                rs.getInt("part_number"),
                rs.getLong("size"),
                rs.getString("etag"),
                rs.getString("presigned_url"),
                uploadedAt == null ? null : uploadedAt.toInstant());
    }
}
