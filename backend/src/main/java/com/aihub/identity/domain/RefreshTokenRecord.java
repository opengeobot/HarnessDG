/*
 * 功能: 刷新令牌摘要领域模型，记录 jti、Token Family 与生命周期，用于轮换与重放检测。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.domain;

import com.aihub.shared.identity.PrincipalType;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 刷新令牌摘要领域模型。
 *
 * <p>仅承载 jti/family/生命周期等摘要信息，绝不持有完整 Token 字符串。
 */
public final class RefreshTokenRecord {

    private final String tokenId;
    private final String jti;
    private final String tokenFamily;
    private final String principalId;
    private final PrincipalType principalType;
    private final Set<String> scopes;
    private RefreshTokenStatus status;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private String replacedByJti;

    private RefreshTokenRecord(Builder builder) {
        this.tokenId = builder.tokenId;
        this.jti = builder.jti;
        this.tokenFamily = builder.tokenFamily;
        this.principalId = builder.principalId;
        this.principalType = builder.principalType;
        this.scopes = new LinkedHashSet<>(builder.scopes == null ? Set.of() : builder.scopes);
        this.status = builder.status == null ? RefreshTokenStatus.ACTIVE : builder.status;
        this.issuedAt = builder.issuedAt;
        this.expiresAt = builder.expiresAt;
        this.replacedByJti = builder.replacedByJti;
    }

    /**
     * @return 是否处于可刷新的有效状态
     */
    public boolean isActive() {
        return status == RefreshTokenStatus.ACTIVE;
    }

    /**
     * 标记为已轮换并记录后继 jti。
     */
    public void markRotated(String newJti) {
        this.status = RefreshTokenStatus.ROTATED;
        this.replacedByJti = newJti;
    }

    public String tokenId() {
        return tokenId;
    }

    public String jti() {
        return jti;
    }

    public String tokenFamily() {
        return tokenFamily;
    }

    public String principalId() {
        return principalId;
    }

    public PrincipalType principalType() {
        return principalType;
    }

    public Set<String> scopes() {
        return Set.copyOf(scopes);
    }

    public RefreshTokenStatus status() {
        return status;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public String replacedByJti() {
        return replacedByJti;
    }

    /**
     * 构建器。
     */
    public static final class Builder {
        private String tokenId;
        private String jti;
        private String tokenFamily;
        private String principalId;
        private PrincipalType principalType;
        private Set<String> scopes;
        private RefreshTokenStatus status;
        private Instant issuedAt;
        private Instant expiresAt;
        private String replacedByJti;

        public Builder tokenId(String tokenId) {
            this.tokenId = tokenId;
            return this;
        }

        public Builder jti(String jti) {
            this.jti = jti;
            return this;
        }

        public Builder tokenFamily(String tokenFamily) {
            this.tokenFamily = tokenFamily;
            return this;
        }

        public Builder principalId(String principalId) {
            this.principalId = principalId;
            return this;
        }

        public Builder principalType(PrincipalType principalType) {
            this.principalType = principalType;
            return this;
        }

        public Builder scopes(Set<String> scopes) {
            this.scopes = scopes;
            return this;
        }

        public Builder status(RefreshTokenStatus status) {
            this.status = status;
            return this;
        }

        public Builder issuedAt(Instant issuedAt) {
            this.issuedAt = issuedAt;
            return this;
        }

        public Builder expiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
            return this;
        }

        public Builder replacedByJti(String replacedByJti) {
            this.replacedByJti = replacedByJti;
            return this;
        }

        public RefreshTokenRecord build() {
            if (jti == null || tokenFamily == null || principalId == null) {
                throw new IllegalArgumentException("jti/tokenFamily/principalId are required");
            }
            return new RefreshTokenRecord(this);
        }
    }
}
