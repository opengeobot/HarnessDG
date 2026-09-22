/*
 * 功能: JdbcJobRepository.insert SQL 占位符/列/绑定参数一致性单元测试。
 *       回归防护：修复 16 列但 15 个 ? 占位符的 bug（资产创建 500 根因）。
 * 时间: 2026-07-12
 * 作者: AxeXie
 */
package com.aihub.job.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.aihub.job.domain.Job;
import com.aihub.job.domain.JobStatus;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("JdbcJobRepository.insert SQL 占位符一致性")
class JdbcJobRepositoryTest {

    @Test
    void insertSqlPlaceholdersMatchColumnsAndBindArgs() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcJobRepository repo = new JdbcJobRepository(jdbcTemplate);

        Job job = new Job(null, "job_test_1", "REPOSITORY_PROVISION", "{\"assetId\":\"ast_1\"}",
                JobStatus.PENDING, 5, 0, Instant.now(), null, null, null,
                "prn_1", "ast_1", null, Instant.now(), Instant.now(), 0);
        repo.insert(job);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), argsCaptor.capture());

        String sql = sqlCaptor.getValue();
        Object[] args = argsCaptor.getValue();

        int columnCount = countColumns(sql);
        int placeholderCount = countPlaceholders(sql);
        assertThat(placeholderCount)
                .as("INSERT 占位符 ? 数量须等于列数（曾为 15<16 的 bug）")
                .isEqualTo(columnCount);
        assertThat(args.length)
                .as("绑定参数数量须等于占位符数量")
                .isEqualTo(placeholderCount);
        assertThat(columnCount).isEqualTo(16);
    }

    private static int countColumns(String sql) {
        int from = sql.indexOf("job_task (");
        int to = sql.indexOf(")", from);
        String cols = sql.substring(from + "job_task (".length(), to);
        return cols.split(",").length;
    }

    private static int countPlaceholders(String sql) {
        int valuesIdx = sql.indexOf("VALUES");
        String valuesPart = sql.substring(valuesIdx);
        // ?::jsonb 计为 1 个占位符
        int count = 0;
        boolean prevQ = false;
        for (int i = 0; i < valuesPart.length(); i++) {
            char c = valuesPart.charAt(i);
            if (c == '?') {
                if (!prevQ) {
                    count++;
                }
                prevQ = true;
            } else {
                prevQ = false;
            }
        }
        return count;
    }
}
