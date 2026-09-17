/*
 * 功能: job_task 插入语句的列/占位符一致性静态门禁——防止再次出现数量不一致。
 * 时间: 2026-09-17
 * 作者: AxeXie
 */
package com.aihub.job.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * {@link JdbcJobRepository#insert} 的 SQL 静态一致性门禁。
 *
 * <p>{@code job_task} 共 17 列，其中 {@code id} 为自增主键，插入需覆盖其余 16 列；
 * 列清单、{@code VALUES} 占位符与绑定参数必须三者数量一致。
 *
 * <p>历史缺陷（由 {@code d8506bf} 引入）为列 16 个、占位符 15 个，只在真实 pgjdbc 路径暴露为
 * {@code PSQLException: The column index is out of range: 16, number of columns: 15}，
 * 单元测试因不触达真实驱动而未能发现。本测试不依赖 Docker，作为该缺陷的回归门禁。
 */
class JdbcJobRepositoryInsertSqlTest {

    private static final Pattern INSERT_PATTERN = Pattern.compile(
            "INSERT\\s+INTO\\s+job_task\\s*\\((?<columns>[^)]*?)\\)\\s*VALUES\\s*\\((?<values>[^)]*?)\\)",
            Pattern.DOTALL);

    @Test
    void insertStatementBindsSameNumberOfPlaceholdersAsColumns() throws IOException {
        Matcher matcher = INSERT_PATTERN.matcher(readRepositorySource());

        assertThat(matcher.find())
                .as("JdbcJobRepository 中应存在 INSERT INTO job_task ... VALUES (...) 语句")
                .isTrue();

        List<String> columns = splitColumnList(matcher.group("columns"));
        int placeholders = countPlaceholders(matcher.group("values"));

        assertThat(placeholders)
                .as("job_task 插入的占位符数量必须与列数量一致；列=%s", columns)
                .isEqualTo(columns.size());
    }

    @Test
    void insertCoversEveryNonNullableBusinessColumn() throws IOException {
        Matcher matcher = INSERT_PATTERN.matcher(readRepositorySource());
        assertThat(matcher.find()).isTrue();

        List<String> columns = splitColumnList(matcher.group("columns"));

        assertThat(columns).hasSize(16).doesNotHaveDuplicates();
        assertThat(columns).doesNotContain("id");
        assertThat(columns).contains(
                "job_id", "type", "payload", "status", "max_attempts", "attempts", "next_run_at",
                "leased_until", "leased_by", "trace_id", "principal_id", "asset_id", "error_code",
                "created_at", "updated_at", "row_version");
    }

    private List<String> splitColumnList(String raw) {
        List<String> columns = new ArrayList<>();
        for (String token : raw.split(",")) {
            String column = token.trim().replaceAll("\\s+", "");
            if (!column.isEmpty()) {
                columns.add(column);
            }
        }
        return columns;
    }

    private int countPlaceholders(String rawValuesClause) {
        int count = 0;
        for (int index = 0; index < rawValuesClause.length(); index++) {
            if (rawValuesClause.charAt(index) == '?') {
                count++;
            }
        }
        return count;
    }

    private String readRepositorySource() throws IOException {
        Path source = Paths.get("src/main/java/com/aihub/job/infrastructure/JdbcJobRepository.java");
        if (!Files.exists(source)) {
            // 兼容从仓库根目录运行测试的工作目录
            source = Paths.get(
                    "backend/src/main/java/com/aihub/job/infrastructure/JdbcJobRepository.java");
        }
        assertThat(Files.exists(source))
                .as("未能定位 JdbcJobRepository 源码：%s", source.toAbsolutePath())
                .isTrue();
        return Files.readString(source);
    }
}
