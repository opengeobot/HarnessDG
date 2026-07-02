/*
 * 功能: 本地用户领域聚合，承载身份属性、口令哈希、Token 版本与账户锁定的不变量与行为。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.domain;

import com.aihub.shared.security.PasswordHasher;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 本地用户领域聚合。
 *
 * <p>封装登录校验、失败计数与锁定、口令变更、启用/禁用等行为与不变量；不依赖 Spring/MyBatis。
 * 仅持有口令哈希摘要，绝不持有或返回明文口令。
 */
public final class LocalUser {

    /** 触发锁定的连续失败阈值。 */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    /** 锁定时长（分钟）。 */
    public static final int LOCK_DURATION_MINUTES = 15;

    private final String userId;
    private final String principalId;
    private final String username;
    private String displayName;
    private String email;
    private String locale;
    private String passwordHash;
    private String passwordAlgorithm;
    private final Set<String> scopes;
    private long tokenVersion;
    private boolean mustChangePassword;
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private Instant lastLoginAt;
    private UserStatus status;
    private long rowVersion;
    private Instant createdAt;
    private Instant updatedAt;

    private LocalUser(Builder builder) {
        this.userId = builder.userId;
        this.principalId = builder.principalId;
        this.username = builder.username;
        this.displayName = builder.displayName;
        this.email = builder.email;
        this.locale = builder.locale == null ? "zh-CN" : builder.locale;
        this.passwordHash = builder.passwordHash;
        this.passwordAlgorithm = builder.passwordAlgorithm == null ? "BCRYPT" : builder.passwordAlgorithm;
        this.scopes = new LinkedHashSet<>(builder.scopes == null ? Set.of() : builder.scopes);
        this.tokenVersion = builder.tokenVersion;
        this.mustChangePassword = builder.mustChangePassword;
        this.failedLoginAttempts = builder.failedLoginAttempts;
        this.lockedUntil = builder.lockedUntil;
        this.lastLoginAt = builder.lastLoginAt;
        this.status = builder.status == null ? UserStatus.ACTIVE : builder.status;
        this.rowVersion = builder.rowVersion;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    /**
     * 判断指定时刻账户是否处于锁定中。
     */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * @return 账户是否被禁用
     */
    public boolean isDisabled() {
        return status == UserStatus.DISABLED;
    }

    /**
     * 校验明文口令是否匹配（不改变状态）。
     */
    public boolean matchesPassword(CharSequence rawPassword, PasswordHasher hasher) {
        return hasher.matches(rawPassword, passwordHash);
    }

    /**
     * 记录一次登录成功：清零失败计数、解除锁定、刷新最近登录时间。
     */
    public void recordLoginSuccess(Instant now) {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.lastLoginAt = now;
        if (this.status == UserStatus.LOCKED) {
            this.status = UserStatus.ACTIVE;
        }
        touch(now);
    }

    /**
     * 记录一次登录失败：递增失败计数，达到阈值时锁定账户。
     */
    public void recordLoginFailure(Instant now) {
        this.failedLoginAttempts += 1;
        if (this.failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            this.lockedUntil = now.plusSeconds(LOCK_DURATION_MINUTES * 60L);
            this.status = UserStatus.LOCKED;
        }
        touch(now);
    }

    /**
     * 变更口令：替换哈希、递增 Token 版本（使旧 Token 失效）、清除强制改密标记。
     */
    public void changePassword(String newPasswordHash, Instant now) {
        this.passwordHash = newPasswordHash;
        this.passwordAlgorithm = "BCRYPT";
        this.mustChangePassword = false;
        this.tokenVersion += 1;
        if (this.status == UserStatus.PENDING_ACTIVATION) {
            this.status = UserStatus.ACTIVE;
        }
        touch(now);
    }

    /**
     * 管理员重置口令：设置临时哈希、强制下次修改、待激活、递增 Token 版本。
     */
    public void resetPassword(String temporaryPasswordHash, Instant now) {
        this.passwordHash = temporaryPasswordHash;
        this.passwordAlgorithm = "BCRYPT";
        this.mustChangePassword = true;
        this.status = UserStatus.PENDING_ACTIVATION;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.tokenVersion += 1;
        touch(now);
    }

    /**
     * 禁用用户：递增 Token 版本以使其所有 Token 立即失效。
     */
    public void disable(Instant now) {
        this.status = UserStatus.DISABLED;
        this.tokenVersion += 1;
        touch(now);
    }

    /**
     * 启用用户：解除锁定与失败计数。
     */
    public void enable(Instant now) {
        this.status = UserStatus.ACTIVE;
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        touch(now);
    }

    /**
     * 更新可变资料。
     */
    public void updateProfile(String displayName, String email, String locale, Instant now) {
        if (displayName != null) {
            this.displayName = displayName;
        }
        this.email = email;
        if (locale != null) {
            this.locale = locale;
        }
        touch(now);
    }

    private void touch(Instant now) {
        this.updatedAt = now;
    }

    public String userId() {
        return userId;
    }

    public String principalId() {
        return principalId;
    }

    public String username() {
        return username;
    }

    public String displayName() {
        return displayName;
    }

    public String email() {
        return email;
    }

    public String locale() {
        return locale;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String passwordAlgorithm() {
        return passwordAlgorithm;
    }

    public Set<String> scopes() {
        return Set.copyOf(scopes);
    }

    public long tokenVersion() {
        return tokenVersion;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    public int failedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant lockedUntil() {
        return lockedUntil;
    }

    public Instant lastLoginAt() {
        return lastLoginAt;
    }

    public UserStatus status() {
        return status;
    }

    public long rowVersion() {
        return rowVersion;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * 构建器，供创建与持久化重建复用。
     */
    public static final class Builder {
        private String userId;
        private String principalId;
        private String username;
        private String displayName;
        private String email;
        private String locale;
        private String passwordHash;
        private String passwordAlgorithm;
        private Set<String> scopes;
        private long tokenVersion;
        private boolean mustChangePassword;
        private int failedLoginAttempts;
        private Instant lockedUntil;
        private Instant lastLoginAt;
        private UserStatus status;
        private long rowVersion;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder principalId(String principalId) {
            this.principalId = principalId;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
            return this;
        }

        public Builder locale(String locale) {
            this.locale = locale;
            return this;
        }

        public Builder passwordHash(String passwordHash) {
            this.passwordHash = passwordHash;
            return this;
        }

        public Builder passwordAlgorithm(String passwordAlgorithm) {
            this.passwordAlgorithm = passwordAlgorithm;
            return this;
        }

        public Builder scopes(Set<String> scopes) {
            this.scopes = scopes;
            return this;
        }

        public Builder tokenVersion(long tokenVersion) {
            this.tokenVersion = tokenVersion;
            return this;
        }

        public Builder mustChangePassword(boolean mustChangePassword) {
            this.mustChangePassword = mustChangePassword;
            return this;
        }

        public Builder failedLoginAttempts(int failedLoginAttempts) {
            this.failedLoginAttempts = failedLoginAttempts;
            return this;
        }

        public Builder lockedUntil(Instant lockedUntil) {
            this.lockedUntil = lockedUntil;
            return this;
        }

        public Builder lastLoginAt(Instant lastLoginAt) {
            this.lastLoginAt = lastLoginAt;
            return this;
        }

        public Builder status(UserStatus status) {
            this.status = status;
            return this;
        }

        public Builder rowVersion(long rowVersion) {
            this.rowVersion = rowVersion;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public LocalUser build() {
            if (userId == null || principalId == null || username == null || passwordHash == null) {
                throw new IllegalArgumentException("userId/principalId/username/passwordHash are required");
            }
            return new LocalUser(this);
        }
    }
}
