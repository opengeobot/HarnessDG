/*
 * 功能: 口令强度策略单元测试——验证长度与字符类别校验（纯 shared，无实现依赖）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link PasswordPolicy} 离线单元测试。
 */
class PasswordPolicyTest {

    private final PasswordPolicy policy = PasswordPolicy.defaults();

    @Test
    void rejectsTooShortOrSimplePassword() {
        assertThat(policy.isSatisfiedBy("short")).isFalse();
        assertThat(policy.isSatisfiedBy("alllowercaseletters")).isFalse();
        assertThat(policy.validate("short")).isNotEmpty();
    }

    @Test
    void acceptsStrongPassword() {
        assertThat(policy.isSatisfiedBy("Str0ng-Passw0rd!")).isTrue();
        assertThat(policy.validate("Str0ng-Passw0rd!")).isEmpty();
    }
}
