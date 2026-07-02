/*
 * 功能: BCrypt 口令哈希单元测试——验证不可逆哈希与匹配语义。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.aihub.shared.security.PasswordHasher;
import org.junit.jupiter.api.Test;

/**
 * {@link BCryptPasswordHasher} 离线单元测试。
 */
class BCryptPasswordHasherTest {

    private final PasswordHasher hasher = new BCryptPasswordHasher();

    @Test
    void hashesAndMatchesPasswordIrreversibly() {
        String raw = "Str0ng-Passw0rd!";
        String encoded = hasher.hash(raw);
        assertThat(encoded).isNotEqualTo(raw);
        assertThat(hasher.matches(raw, encoded)).isTrue();
        assertThat(hasher.matches("wrong-password", encoded)).isFalse();
        assertThat(hasher.matches(raw, null)).isFalse();
    }
}
