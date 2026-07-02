/*
 * 功能: 审计仓储不可篡改测试——验证 AuditRepository 无 UPDATE/DELETE 方法。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * 审计仓储不可篡改测试。
 *
 * <p>验证 {@link AuditRepository} 接口不暴露任何 UPDATE/DELETE 方法，保证审计追加写不可篡改。
 */
class AuditRepositoryImmutabilityTest {

    @Test
    void shouldNotExposeUpdateOrDeleteMethods() {
        Method[] methods = AuditRepository.class.getDeclaredMethods();
        for (Method method : methods) {
            String name = method.getName().toLowerCase();
            assertThat(name)
                    .as("AuditRepository must not expose update/delete methods: %s", method.getName())
                    .doesNotContain("update")
                    .doesNotContain("delete")
                    .doesNotContain("remove")
                    .doesNotContain("modify");
        }
    }

    @Test
    void shouldExposeOnlyAppendAndQuery() {
        Method[] methods = AuditRepository.class.getDeclaredMethods();
        assertThat(methods).hasSize(3);
        for (Method method : methods) {
            String name = method.getName();
            assertThat(name)
                    .as("AuditRepository should only expose append, list, search methods: %s", name)
                    .isIn("append", "list", "search");
        }
    }
}
