/*
 * 功能: asset 持久化实体列名转义的静态门禁——防止 MySQL 风格反引号再次进入 @TableField。
 * 时间: 2026-09-17
 * 作者: AxeXie
 */
package com.aihub.asset.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * asset 模块持久化实体列名的转义门禁。
 *
 * <p>MyBatis-Plus 会把 {@code @TableField}/{@code @TableName}/{@code @TableId} 的值原样拼进 SQL。
 * 若值使用 MySQL 风格反引号（如 {@code @TableField("`precision`")}），
 * PostgreSQL 会报 {@code syntax error at or near ","}，
 * 使该实体参与的整条查询路径返回 500。
 *
 * <p>历史缺陷：{@link AssetModelEntity} 的 {@code precision} 字段曾被反引号包裹，
 * 导致 {@code GET /api/v1/assets/{id}} 读取 MODEL 资产时因
 * {@code AssetModelMapper.selectById} 语法错误而 500。
 * 本测试不依赖 Docker，作为该缺陷的回归门禁。
 */
class AssetEntityColumnEscapingTest {

    /** 匹配注解值里出现反引号的情形（`col` 或 a.`col`）。 */
    private static final Pattern BACKTICK_IN_ANNOTATION = Pattern.compile(
            "@Table(?:Field|Name|Id)\\s*\\(\\s*(?:value\\s*=\\s*)?\"[^\"]*`[^\"]*\"");

    private static final Pattern COLUMN_NAME_ANNOTATION = Pattern.compile(
            "@Table(?:Field|Name|Id)\\s*\\(\\s*(?:value\\s*=\\s*)?\"(?<name>[^\"]+)\"");

    @Test
    void noColumnNameAnnotationIsQuotedWithBackticks() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path source : infrastructureSources()) {
            String content = Files.readString(source);
            Matcher matcher = BACKTICK_IN_ANNOTATION.matcher(content);
            while (matcher.find()) {
                offenders.add(source.getFileName() + " -> " + matcher.group());
            }
        }

        assertThat(offenders)
                .as("实体列名注解不得使用 MySQL 风格反引号（PostgreSQL 会报 syntax error）：%s", offenders)
                .isEmpty();
    }

    @Test
    void precisionColumnIsMappedWithoutEscaping() throws IOException {
        assertThat(readEntitySource("AssetModelEntity.java"))
                .as("precision 作为 PostgreSQL 列名无需引用符，注解值必须是裸列名")
                .contains("@TableField(\"precision\")")
                .doesNotContain("`precision`");
    }

    @Test
    void everyEntityAnnotationValueLooksLikeABareIdentifier() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : entitySources()) {
            String content = Files.readString(source);
            Matcher matcher = COLUMN_NAME_ANNOTATION.matcher(content);
            while (matcher.find()) {
                String name = matcher.group("name");
                // 裸列名：仅小写字母、数字与下划线；排除占位符与 SpEL。
                if (!name.matches("[a-z][a-z0-9_]*")) {
                    violations.add(source.getFileName() + " -> " + name);
                }
            }
        }

        assertThat(violations)
                .as("实体列名注解值必须是小写裸列名：%s", violations)
                .isEmpty();
    }

    private List<Path> infrastructureSources() throws IOException {
        return javaSourcesUnder("src/main/java/com/aihub/asset/infrastructure");
    }

    private List<Path> entitySources() throws IOException {
        List<Path> sources = new ArrayList<>(infrastructureSources());
        sources.addAll(javaSourcesUnder("src/main/java/com/aihub/asset/discussion/infrastructure"));
        return sources;
    }

    private List<Path> javaSourcesUnder(String relativeDir) throws IOException {
        Path dir = Paths.get(relativeDir);
        if (!Files.isDirectory(dir)) {
            // 兼容从仓库根目录运行测试的工作目录
            dir = Paths.get("backend", relativeDir);
        }
        assertThat(Files.isDirectory(dir))
                .as("未能定位源码目录：%s", dir.toAbsolutePath())
                .isTrue();
        try (Stream<Path> stream = Files.walk(dir)) {
            return stream.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private String readEntitySource(String fileName) throws IOException {
        for (Path source : entitySources()) {
            if (source.getFileName().toString().equals(fileName)) {
                return Files.readString(source);
            }
        }
        throw new IllegalStateException("未能定位实体源码：" + fileName);
    }
}
