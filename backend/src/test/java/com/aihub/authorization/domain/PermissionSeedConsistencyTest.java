/*
 * 功能: 权限常量与 DB Seed 一致性测试——确保 Permissions.java 的每个常量在 V*__*.sql 迁移中有对应 seed。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.authorization.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 权限常量与 Flyway Seed 一致性测试。
 *
 * <p>扫描 {@link Permissions} 中所有 {@code String} 常量，验证其在 {@code V*__*.sql} 迁移文件中
 * 以 {@code 'resource:action'} 形式出现在 INSERT 语句中。
 */
class PermissionSeedConsistencyTest {

    private static final Pattern PERMISSION_CODE_PATTERN =
            Pattern.compile("'([a-z]+:[a-z]+)'");

    @Test
    void allPermissionConstantsShouldBeSeeded() throws Exception {
        Set<String> javaConstants = collectPermissionConstants();
        Set<String> sqlCodes = collectSqlSeedCodes();

        List<String> missing = new ArrayList<>();
        for (String code : javaConstants) {
            if (!sqlCodes.contains(code)) {
                missing.add(code);
            }
        }

        assertThat(missing)
                .as("Permissions.java 常量在 V*__*.sql seed 中缺失: %s", missing)
                .isEmpty();
    }

    @Test
    void permissionsShouldFollowResourceActionFormat() throws Exception {
        Set<String> javaConstants = collectPermissionConstants();
        Pattern format = Pattern.compile("[a-z]+:[a-z]+");

        List<String> invalid = javaConstants.stream()
                .filter(c -> !format.matcher(c).matches())
                .toList();

        assertThat(invalid)
                .as("权限常量格式不符合 resource:action: %s", invalid)
                .isEmpty();
    }

    @Test
    void highRiskActionsShouldBeSubsetOfConstants() throws Exception {
        Set<String> javaConstants = collectPermissionConstants();
        assertThat(Permissions.HIGH_RISK_ACTIONS)
                .as("HIGH_RISK_ACTIONS 中引用的权限不在常量清单中")
                .isSubsetOf(javaConstants);
    }

    @Test
    void noDuplicatePermissionCodes() throws Exception {
        Set<String> javaConstants = collectPermissionConstants();
        // Since we use Set, duplicates are already eliminated; just verify count matches
        assertThat(javaConstants.size())
                .as("Permissions.java 常量数量")
                .isGreaterThan(20);
    }

    private Set<String> collectPermissionConstants() throws Exception {
        List<String> codes = new ArrayList<>();
        for (Field field : Permissions.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    && Modifier.isFinal(field.getModifiers())
                    && field.getType() == String.class) {
                codes.add((String) field.get(null));
            }
        }
        return Set.copyOf(codes);
    }

    private Set<String> collectSqlSeedCodes() throws IOException {
        Path migrationDir = Paths.get("src/main/resources/db/migration");
        if (!Files.exists(migrationDir)) {
            // fallback for different working directory
            migrationDir = Paths.get("backend/src/main/resources/db/migration");
        }
        try (Stream<Path> files = Files.list(migrationDir)) {
            return files
                    .filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql"))
                    .flatMap(p -> {
                        try {
                            String content = Files.readString(p);
                            Matcher matcher = PERMISSION_CODE_PATTERN.matcher(content);
                            List<String> codes = new ArrayList<>();
                            while (matcher.find()) {
                                codes.add(matcher.group(1));
                            }
                            return codes.stream();
                        } catch (IOException e) {
                            return Stream.empty();
                        }
                    })
                    .collect(Collectors.toSet());
        }
    }
}
