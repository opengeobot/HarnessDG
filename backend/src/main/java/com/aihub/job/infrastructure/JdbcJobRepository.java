/*
 * 功能: 可靠任务 JDBC 仓储——持久化任务状态机，以 FOR UPDATE SKIP LOCKED 领取，键集游标分页。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.job.infrastructure;

import com.aihub.job.domain.Job;
import com.aihub.job.domain.JobRepository;
import com.aihub.job.domain.JobStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 可靠任务 JDBC 仓储。
 *
 * <p>领取采用单语句 {@code UPDATE ... WHERE id = (SELECT ... FOR UPDATE SKIP LOCKED LIMIT 1) RETURNING *},
 * 保证多 Worker 并发不重复领取，且无需显式事务。租约到期（leased_until < now 且 RUNNING）可重新领取。
 */
@Repository
public class JdbcJobRepository implements JobRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final RowMapper<Job> JOB_MAPPER = (rs, rowNum) -> mapJob(rs);

    private static Job mapJob(ResultSet rs) throws SQLException {
        return new Job(
                rs.getLong("id"),
                rs.getString("job_id"),
                rs.getString("type"),
                rs.getString("payload"),
                JobStatus.valueOf(rs.getString("status")),
                rs.getInt("max_attempts"),
                rs.getInt("attempts"),
                getInstant(rs, "next_run_at"),
                getInstant(rs, "leased_until"),
                rs.getString("leased_by"),
                rs.getString("trace_id"),
                rs.getString("principal_id"),
                rs.getString("asset_id"),
                rs.getString("error_code"),
                getInstant(rs, "created_at"),
                getInstant(rs, "updated_at"),
                rs.getInt("row_version"));
    }

    private static Instant getInstant(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toInstant();
    }

    @Override
    public void insert(Job job) {
        jdbcTemplate.update("""
                INSERT INTO job_task (job_id, type, payload, status, max_attempts, attempts, next_run_at,
                    leased_until, leased_by, trace_id, principal_id, asset_id, error_code,
                    created_at, updated_at, row_version)
                VALUES (?,?,?::jsonb,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                job.jobId(), job.type(), job.payload(), job.status().name(),
                job.maxAttempts(), job.attempts(), Timestamp.from(job.nextRunAt()),
                null, null, job.traceId(), job.principalId(), job.assetId(), null,
                Timestamp.from(job.createdAt()), Timestamp.from(job.updatedAt()), 0);
    }

    @Override
    public Optional<Job> findByJobId(String jobId) {
        List<Job> rows = jdbcTemplate.query("SELECT * FROM job_task WHERE job_id = ?", JOB_MAPPER, jobId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Job> list(String status, Instant cursorTime, Long cursorId, int limit) {
        if (cursorTime == null) {
            if (status == null) {
                return jdbcTemplate.query(
                        "SELECT * FROM job_task ORDER BY created_at, id LIMIT ?",
                        JOB_MAPPER, limit);
            }
            return jdbcTemplate.query(
                    "SELECT * FROM job_task WHERE status = ? ORDER BY created_at, id LIMIT ?",
                    JOB_MAPPER, status, limit);
        }
        if (status == null) {
            return jdbcTemplate.query(
                    "SELECT * FROM job_task WHERE (created_at, id) > (?, ?) ORDER BY created_at, id LIMIT ?",
                    JOB_MAPPER, Timestamp.from(cursorTime), cursorId, limit);
        }
        return jdbcTemplate.query(
                "SELECT * FROM job_task WHERE status = ? AND (created_at, id) > (?, ?) ORDER BY created_at, id LIMIT ?",
                JOB_MAPPER, status, Timestamp.from(cursorTime), cursorId, limit);
    }

    @Override
    public Optional<Job> claimNext(String workerId, Instant now, long leaseSeconds) {
        Instant leasedUntil = now.plusSeconds(leaseSeconds);
        List<Job> rows = jdbcTemplate.query("""
                UPDATE job_task
                SET status = 'RUNNING', leased_by = ?, leased_until = ?, updated_at = ?, row_version = row_version + 1
                WHERE id = (
                    SELECT id FROM job_task
                    WHERE (status IN ('PENDING','RETRY_WAIT') AND next_run_at <= ?)
                       OR (status = 'RUNNING' AND leased_until < ?)
                    ORDER BY next_run_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                RETURNING *
                """, JOB_MAPPER, workerId, Timestamp.from(leasedUntil), Timestamp.from(now),
                Timestamp.from(now), Timestamp.from(now));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public void markSucceeded(String jobId, Instant now) {
        jdbcTemplate.update(
                "UPDATE job_task SET status = 'SUCCEEDED', leased_until = NULL, leased_by = NULL, "
                        + "updated_at = ?, row_version = row_version + 1 WHERE job_id = ?",
                Timestamp.from(now), jobId);
    }

    @Override
    public void markRetryWait(String jobId, int attempts, Instant nextRunAt, Instant now) {
        jdbcTemplate.update(
                "UPDATE job_task SET status = 'RETRY_WAIT', attempts = ?, next_run_at = ?, "
                        + "leased_until = NULL, leased_by = NULL, updated_at = ?, row_version = row_version + 1 "
                        + "WHERE job_id = ?",
                attempts, Timestamp.from(nextRunAt), Timestamp.from(now), jobId);
    }

    @Override
    public void markDead(String jobId, String errorCode, Instant now) {
        jdbcTemplate.update(
                "UPDATE job_task SET status = 'DEAD', error_code = ?, leased_until = NULL, leased_by = NULL, "
                        + "updated_at = ?, row_version = row_version + 1 WHERE job_id = ?",
                errorCode, Timestamp.from(now), jobId);
    }

    @Override
    public boolean resetToPending(String jobId, Instant now) {
        int updated = jdbcTemplate.update(
                "UPDATE job_task SET status = 'PENDING', next_run_at = ?, error_code = NULL, "
                        + "leased_until = NULL, leased_by = NULL, updated_at = ?, row_version = row_version + 1 "
                        + "WHERE job_id = ? AND status IN ('DEAD','RETRY_WAIT')",
                Timestamp.from(now), Timestamp.from(now), jobId);
        return updated > 0;
    }

    @Override
    public boolean cancel(String jobId, Instant now) {
        int updated = jdbcTemplate.update(
                "UPDATE job_task SET status = 'CANCELLED', leased_until = NULL, leased_by = NULL, "
                        + "updated_at = ?, row_version = row_version + 1 "
                        + "WHERE job_id = ? AND status IN ('PENDING','RETRY_WAIT')",
                Timestamp.from(now), jobId);
        return updated > 0;
    }

    @Override
    public long countByStatus(String status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM job_task WHERE status = ?", Long.class, status);
        return count == null ? 0 : count;
    }
}
