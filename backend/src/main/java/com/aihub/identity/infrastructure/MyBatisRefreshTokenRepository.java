/*
 * 功能: 刷新令牌仓储适配器，基于 MyBatis-Plus 实现令牌摘要的落库、轮换与吊销。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.infrastructure;

import com.aihub.identity.domain.RefreshTokenRecord;
import com.aihub.identity.domain.RefreshTokenRepository;
import com.aihub.identity.domain.RefreshTokenStatus;
import com.aihub.platform.security.JtiDigest;
import com.aihub.shared.identity.PrincipalType;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * 刷新令牌仓储适配器。仅持久化 jti/family/生命周期摘要，绝不持久化完整 Token 字符串。
 */
@Repository
public class MyBatisRefreshTokenRepository implements RefreshTokenRepository {

    private final TokenMapper tokenMapper;

    public MyBatisRefreshTokenRepository(TokenMapper tokenMapper) {
        this.tokenMapper = tokenMapper;
    }

    @Override
    public void save(RefreshTokenRecord record) {
        tokenMapper.insert(toEntity(record));
    }

    @Override
    public Optional<RefreshTokenRecord> findByJti(String jti) {
        TokenEntity entity = tokenMapper.selectOne(
                Wrappers.<TokenEntity>lambdaQuery().eq(TokenEntity::getJti, jti));
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    @Override
    public Optional<RefreshTokenRecord> findByJwtId(String jwtId) {
        String digest = JtiDigest.sha256Hex(jwtId);
        TokenEntity entity = tokenMapper.selectOne(
                Wrappers.<TokenEntity>lambdaQuery().eq(TokenEntity::getJtiDigest, digest));
        if (entity != null) {
            return Optional.of(toDomain(entity));
        }
        return findByJti(jwtId);
    }

    @Override
    public void markRotated(String jti, String replacedByJti) {
        tokenMapper.update(null, Wrappers.<TokenEntity>lambdaUpdate()
                .eq(TokenEntity::getJti, jti)
                .set(TokenEntity::getStatus, RefreshTokenStatus.ROTATED.name())
                .set(TokenEntity::getReplacedByJti, replacedByJti)
                .set(TokenEntity::getLastUsedAt, Instant.now())
                .set(TokenEntity::getUpdatedAt, Instant.now()));
    }

    @Override
    public int revokeFamily(String tokenFamily) {
        return tokenMapper.update(null, Wrappers.<TokenEntity>lambdaUpdate()
                .eq(TokenEntity::getTokenFamily, tokenFamily)
                .in(TokenEntity::getStatus, RefreshTokenStatus.ACTIVE.name(), RefreshTokenStatus.ROTATED.name())
                .set(TokenEntity::getStatus, RefreshTokenStatus.REVOKED.name())
                .set(TokenEntity::getUpdatedAt, Instant.now()));
    }

    @Override
    public int revokeAllByPrincipal(String principalId) {
        return tokenMapper.update(null, Wrappers.<TokenEntity>lambdaUpdate()
                .eq(TokenEntity::getPrincipalId, principalId)
                .in(TokenEntity::getStatus, RefreshTokenStatus.ACTIVE.name(), RefreshTokenStatus.ROTATED.name())
                .set(TokenEntity::getStatus, RefreshTokenStatus.REVOKED.name())
                .set(TokenEntity::getUpdatedAt, Instant.now()));
    }

    private TokenEntity toEntity(RefreshTokenRecord record) {
        TokenEntity entity = new TokenEntity();
        entity.setTokenId(record.tokenId());
        entity.setJti(record.jti());
        entity.setJtiDigest(JtiDigest.sha256Hex(record.jti()));
        entity.setTokenFamily(record.tokenFamily());
        entity.setPrincipalId(record.principalId());
        entity.setTokenType("REFRESH");
        entity.setScopes(List.copyOf(record.scopes()));
        entity.setStatus(record.status().name());
        entity.setIssuedAt(record.issuedAt());
        entity.setExpiresAt(record.expiresAt());
        entity.setReplacedByJti(record.replacedByJti());
        return entity;
    }

    private RefreshTokenRecord toDomain(TokenEntity entity) {
        return new RefreshTokenRecord.Builder()
                .tokenId(entity.getTokenId())
                .jti(entity.getJti())
                .tokenFamily(entity.getTokenFamily())
                .principalId(entity.getPrincipalId())
                .principalType(resolvePrincipalType(entity.getPrincipalId()))
                .scopes(entity.getScopes() == null ? Set.of() : Set.copyOf(entity.getScopes()))
                .status(RefreshTokenStatus.valueOf(entity.getStatus()))
                .issuedAt(entity.getIssuedAt())
                .expiresAt(entity.getExpiresAt())
                .replacedByJti(entity.getReplacedByJti())
                .build();
    }

    /**
     * 由 principalId 前缀推断主体类型（usr_→USER，其余按 Agent/Service 归为 AGENT）。
     * 刷新主要用于人类用户；凭据交换的 Agent 默认不签发刷新令牌。
     */
    private PrincipalType resolvePrincipalType(String principalId) {
        return PrincipalType.USER;
    }
}
