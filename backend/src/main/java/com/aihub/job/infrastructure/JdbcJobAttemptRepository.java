/*
 * 功能: 任务尝试 JDBC 仓储，追加每次执行结果。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.job.infrastructure;

import com.aihub.job.domain.JobAttempt;
import com.aihub.job.domain.JobAttemptRepository;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 任务尝试 JDBC 仓储。
 */
@Repository
public class JdbcJobAttemptRepository implements JobAttemptRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcJobAttemptRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void insert(JobAttempt attempt) {
        jdbcTemplate.update("""
                INSERT INTO job_attempt (job_id, attempt_no, started_at, ended_at, status,
                    error_message, error_code, duration_ms)
                VALUES (?,?,?,?,?,?,?,?)
                """,
                attempt.jobId(), attempt.attemptNo(), Timestamp.from(attempt.startedAt()),
                attempt.endedAt() == null ? null : Timestamp.from(attempt.endedAt()),
                attempt.status(), attempt.errorMessage(), attempt.errorCode(), attempt.durationMs());
    }
}
