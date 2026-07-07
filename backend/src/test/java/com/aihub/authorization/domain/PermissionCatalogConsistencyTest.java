/*
 * 功能: 权限目录一致性测试——验证 Permissions.java 常量与 V4+V14+V20 Seed 无漂移（TASK-P0BR-003）。
 * 时间: 2026-07-07
 * 作者: AI
 */
package com.aihub.authorization.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 验证 {@link Permissions} 常量清单与数据库 Seed（V4 + V14 + V20）完全一致，
 * 防止 Java 常量和数据库权限之间出现漂移（AUD-016）。
 */
class PermissionCatalogConsistencyTest {

    /**
     * V4 + V14 + V20 中全部 iam_permission Seed 的 code 集合。
     * 任何新增权限必须同步更新此集合和 Permissions.java 常量。
     */
    private static final Set<String> SEED_PERMISSIONS = Set.of(
            // V4 (30 permissions)
            "organization:manage",
            "project:view",
            "project:manage",
            "user:read",
            "user:manage",
            "authorization:read",
            "authorization:manage",
            "dictionary:read",
            "dictionary:manage",
            "tag:read",
            "tag:manage",
            "asset:create",
            "asset:read",
            "asset:update",
            "asset:upload",
            "asset:download",
            "asset:submit",
            "asset:review",
            "asset:publish",
            "asset:deprecate",
            "asset:delete",
            "agent:register",
            "agent:authorize",
            "token:create",
            "audit:read",
            "job:read",
            "job:manage",
            "notification:read",
            "system:configure",
            "system:observe",
            "mcp:invoke",
            // V14 (2 additional)
            "asset:discuss",
            "asset:moderate",
            // V20 (2 additional)
            "team:manage",
            "asset:manage"
    );

    @Test
    void permissionsConstantsMatchDatabaseSeed() throws Exception {
        // 反射提取 Permissions 中所有 public static final String 常量值
        Set<String> javaConstants = extractStringConstants(Permissions.class);

        // 移除非权限编码常量（HIGH_RISK_ACTIONS 是 Set<String>，不会被提取）
        // 验证 Java 常量与 Seed 完全一致
        assertThat(javaConstants)
                .as("Permissions.java constants must exactly match V4+V14+V20 iam_permission seed codes")
                .containsExactlyInAnyOrderElementsOf(SEED_PERMISSIONS);
    }

    @Test
    void noMissingPermissionsInJavaConstants() throws Exception {
        Set<String> javaConstants = extractStringConstants(Permissions.class);

        Set<String> missingInJava = SEED_PERMISSIONS.stream()
                .filter(p -> !javaConstants.contains(p))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(missingInJava)
                .as("Seed permissions missing from Permissions.java constants")
                .isEmpty();
    }

    @Test
    void noExtraPermissionsInJavaConstants() throws Exception {
        Set<String> javaConstants = extractStringConstants(Permissions.class);

        Set<String> extraInJava = javaConstants.stream()
                .filter(p -> !SEED_PERMISSIONS.contains(p))
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(extraInJava)
                .as("Permissions.java constants not present in database seed")
                .isEmpty();
    }

    @Test
    void seedPermissionCountIsCorrect() {
        // 30 (V4) + 2 (V14) + 2 (V20) = 34... wait, let me count V4:
        // organization:manage, project:view, project:manage, user:read, user:manage,
        // authorization:read, authorization:manage, dictionary:read, dictionary:manage,
        // tag:read, tag:manage, asset:create, asset:read, asset:update, asset:upload,
        // asset:download, asset:submit, asset:review, asset:publish, asset:deprecate,
        // asset:delete, agent:register, agent:authorize, token:create, audit:read,
        // job:read, job:manage, notification:read, system:configure, system:observe,
        // mcp:invoke = 31
        // V14: asset:discuss, asset:moderate = 2
        // V20: team:manage, asset:manage = 2
        // Total: 31 + 2 + 2 = 35
        assertThat(SEED_PERMISSIONS).hasSize(35);
    }

    @Test
    void highRiskActionsAreSubsetOfPermissions() {
        assertThat(Permissions.HIGH_RISK_ACTIONS)
                .as("HIGH_RISK_ACTIONS must only contain valid permission codes")
                .isSubsetOf(SEED_PERMISSIONS);
    }

    private static Set<String> extractStringConstants(Class<?> clazz) throws Exception {
        Set<String> result = new TreeSet<>();
        for (Field field : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    && Modifier.isFinal(field.getModifiers())
                    && Modifier.isPublic(field.getModifiers())
                    && field.getType() == String.class) {
                field.setAccessible(true);
                result.add((String) field.get(null));
            }
        }
        return result;
    }
}
