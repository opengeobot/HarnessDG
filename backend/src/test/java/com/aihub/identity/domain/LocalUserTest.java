/*
 * 功能: LocalUser 领域单元测试——校验登录失败锁定、成功复位、改密/重置/禁用对 Token 版本的影响。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.aihub.shared.security.PasswordHasher;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link LocalUser} 领域单元测试。
 */
class LocalUserTest {

    /** 简单可控的口令哈希器：哈希为 "hashed:"+明文，匹配按此规则比较。 */
    private static final PasswordHasher HASHER = new PasswordHasher() {
        @Override
        public String hash(CharSequence rawPassword) {
            return "hashed:" + rawPassword;
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encodedPassword != null && encodedPassword.equals("hashed:" + rawPassword);
        }
    };

    private LocalUser newUser() {
        Instant now = Instant.parse("2026-06-30T00:00:00Z");
        return new LocalUser.Builder()
                .userId("usr_1").principalId("prn_1").username("alice")
                .displayName("Alice").passwordHash("hashed:Sup3rSecret!23")
                .scopes(Set.of("user:read")).tokenVersion(3L)
                .status(UserStatus.ACTIVE).createdAt(now).updatedAt(now).rowVersion(0L)
                .build();
    }

    @Test
    void locksAccountAfterMaxFailedAttempts() {
        LocalUser user = newUser();
        Instant now = Instant.parse("2026-06-30T01:00:00Z");
        for (int i = 0; i < LocalUser.MAX_FAILED_ATTEMPTS; i++) {
            user.recordLoginFailure(now);
        }
        assertThat(user.status()).isEqualTo(UserStatus.LOCKED);
        assertThat(user.isLocked(now)).isTrue();
        assertThat(user.isLocked(now.plusSeconds(LocalUser.LOCK_DURATION_MINUTES * 60L + 1))).isFalse();
    }

    @Test
    void loginSuccessResetsFailuresAndLock() {
        LocalUser user = newUser();
        Instant now = Instant.parse("2026-06-30T01:00:00Z");
        user.recordLoginFailure(now);
        user.recordLoginSuccess(now);
        assertThat(user.failedLoginAttempts()).isZero();
        assertThat(user.lockedUntil()).isNull();
        assertThat(user.lastLoginAt()).isEqualTo(now);
    }

    @Test
    void changePasswordIncrementsTokenVersionAndClearsForceFlag() {
        LocalUser user = newUser();
        long before = user.tokenVersion();
        user.changePassword(HASHER.hash("N3wPassw0rd!x"), Instant.now());
        assertThat(user.tokenVersion()).isEqualTo(before + 1);
        assertThat(user.mustChangePassword()).isFalse();
        assertThat(user.matchesPassword("N3wPassw0rd!x", HASHER)).isTrue();
    }

    @Test
    void disableIncrementsTokenVersion() {
        LocalUser user = newUser();
        long before = user.tokenVersion();
        user.disable(Instant.now());
        assertThat(user.status()).isEqualTo(UserStatus.DISABLED);
        assertThat(user.isDisabled()).isTrue();
        assertThat(user.tokenVersion()).isEqualTo(before + 1);
    }

    @Test
    void resetPasswordForcesChangeAndPendingActivation() {
        LocalUser user = newUser();
        long before = user.tokenVersion();
        user.resetPassword(HASHER.hash("T3mpPassw0rd!"), Instant.now());
        assertThat(user.mustChangePassword()).isTrue();
        assertThat(user.status()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(user.tokenVersion()).isEqualTo(before + 1);
    }
}
