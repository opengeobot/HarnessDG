/*
 * 功能: 本地用户仓储适配器，基于 MyBatis-Plus 实现用户与主体的持久化与查询。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.infrastructure;

import com.aihub.identity.domain.LocalUser;
import com.aihub.identity.domain.LocalUserRepository;
import com.aihub.identity.domain.PrincipalAccount;
import com.aihub.identity.domain.UserStatus;
import com.aihub.shared.api.PageResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 本地用户仓储适配器。
 *
 * <p>负责领域聚合 {@link LocalUser} 与持久化实体之间的互转，杜绝实体跨模块外泄。
 * 状态更新走 row_version 乐观锁。
 */
@Repository
public class MyBatisLocalUserRepository implements LocalUserRepository {

    private final UserMapper userMapper;
    private final PrincipalMapper principalMapper;

    public MyBatisLocalUserRepository(UserMapper userMapper, PrincipalMapper principalMapper) {
        this.userMapper = userMapper;
        this.principalMapper = principalMapper;
    }

    @Override
    @Transactional
    public void create(PrincipalAccount principal, LocalUser user) {
        principalMapper.insert(toPrincipalEntity(principal));
        userMapper.insert(toEntity(user));
    }

    @Override
    @Transactional
    public boolean update(LocalUser user) {
        long currentVersion = user.rowVersion();
        int affected = userMapper.update(null, Wrappers.<UserEntity>lambdaUpdate()
                .eq(UserEntity::getUserId, user.userId())
                .eq(UserEntity::getRowVersion, currentVersion)
                .set(UserEntity::getDisplayName, user.displayName())
                .set(UserEntity::getEmail, user.email())
                .set(UserEntity::getLocale, user.locale())
                .set(UserEntity::getPasswordHash, user.passwordHash())
                .set(UserEntity::getPasswordAlgorithm, user.passwordAlgorithm())
                .set(UserEntity::getTokenVersion, user.tokenVersion())
                .set(UserEntity::getMustChangePassword, user.mustChangePassword() ? 1 : 0)
                .set(UserEntity::getFailedLoginAttempts, user.failedLoginAttempts())
                .set(UserEntity::getLockedUntil, user.lockedUntil())
                .set(UserEntity::getLastLoginAt, user.lastLoginAt())
                .set(UserEntity::getStatus, user.status().name())
                .set(UserEntity::getUpdatedAt, user.updatedAt() == null ? Instant.now() : user.updatedAt())
                .set(UserEntity::getRowVersion, currentVersion + 1));
        // 用户被禁用时同步主体状态。
        if (affected > 0) {
            syncPrincipalStatus(user.principalId(), user.status() == UserStatus.DISABLED ? "DISABLED" : "ACTIVE");
        }
        return affected > 0;
    }

    @Override
    public Optional<LocalUser> findByUsername(String username) {
        return findOne(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getUsername, username));
    }

    @Override
    public Optional<LocalUser> findByUserId(String userId) {
        return findOne(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getUserId, userId));
    }

    @Override
    public Optional<LocalUser> findByPrincipalId(String principalId) {
        return findOne(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getPrincipalId, principalId));
    }

    @Override
    public boolean existsByUsername(String username) {
        Long count = userMapper.selectCount(
                Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getUsername, username));
        return count != null && count > 0;
    }

    @Override
    public long countActiveByScope(String scope) {
        // scopes 为 jsonb 数组，使用 @> 包含判断；统计活跃用户中持有该 scope 的数量。
        Long count = userMapper.selectCount(Wrappers.<UserEntity>query()
                .eq("status", UserStatus.ACTIVE.name())
                .apply("scopes @> {0}::jsonb", "[\"" + scope + "\"]"));
        return count == null ? 0L : count;
    }

    @Override
    public PageResult<LocalUser> search(String keyword, UserStatus status, int page, int size) {
        int safePage = page < 1 ? 1 : page;
        int safeSize = size < 1 ? 20 : Math.min(size, 100);
        Long total = userMapper.selectCount(buildSearchWrapper(keyword, status));
        long totalCount = total == null ? 0L : total;
        LambdaQueryWrapper<UserEntity> wrapper = buildSearchWrapper(keyword, status)
                .orderByDesc(UserEntity::getCreatedAt)
                .last("LIMIT " + safeSize + " OFFSET " + ((long) (safePage - 1) * safeSize));
        List<LocalUser> items = userMapper.selectList(wrapper).stream().map(this::toDomain).toList();
        return new PageResult<>(items, totalCount, safePage, safeSize);
    }

    private LambdaQueryWrapper<UserEntity> buildSearchWrapper(String keyword, UserStatus status) {
        LambdaQueryWrapper<UserEntity> wrapper = Wrappers.lambdaQuery();
        if (keyword != null && !keyword.isBlank()) {
            String like = keyword.trim();
            wrapper.and(w -> w.like(UserEntity::getUsername, like).or().like(UserEntity::getDisplayName, like));
        }
        if (status != null) {
            wrapper.eq(UserEntity::getStatus, status.name());
        }
        return wrapper;
    }

    private Optional<LocalUser> findOne(LambdaQueryWrapper<UserEntity> wrapper) {
        UserEntity entity = userMapper.selectOne(wrapper);
        return entity == null ? Optional.empty() : Optional.of(toDomain(entity));
    }

    private void syncPrincipalStatus(String principalId, String status) {
        principalMapper.update(null, Wrappers.<PrincipalEntity>lambdaUpdate()
                .eq(PrincipalEntity::getPrincipalId, principalId)
                .set(PrincipalEntity::getStatus, status)
                .set(PrincipalEntity::getUpdatedAt, Instant.now()));
    }

    private PrincipalEntity toPrincipalEntity(PrincipalAccount principal) {
        PrincipalEntity entity = new PrincipalEntity();
        entity.setPrincipalId(principal.principalId());
        entity.setPrincipalType(principal.principalType().name());
        entity.setDisplayName(principal.displayName());
        entity.setStatus(principal.status());
        entity.setCreatedAt(principal.createdAt());
        entity.setUpdatedAt(principal.updatedAt());
        entity.setRowVersion(0);
        return entity;
    }

    private UserEntity toEntity(LocalUser user) {
        UserEntity entity = new UserEntity();
        entity.setUserId(user.userId());
        entity.setPrincipalId(user.principalId());
        entity.setUsername(user.username());
        entity.setDisplayName(user.displayName());
        entity.setEmail(user.email());
        entity.setLocale(user.locale());
        entity.setPasswordHash(user.passwordHash());
        entity.setPasswordAlgorithm(user.passwordAlgorithm());
        entity.setScopes(List.copyOf(user.scopes()));
        entity.setTokenVersion(user.tokenVersion());
        entity.setMustChangePassword(user.mustChangePassword() ? 1 : 0);
        entity.setFailedLoginAttempts(user.failedLoginAttempts());
        entity.setLockedUntil(user.lockedUntil());
        entity.setLastLoginAt(user.lastLoginAt());
        entity.setStatus(user.status().name());
        entity.setCreatedAt(user.createdAt());
        entity.setUpdatedAt(user.updatedAt());
        entity.setRowVersion(user.rowVersion());
        return entity;
    }

    private LocalUser toDomain(UserEntity entity) {
        return new LocalUser.Builder()
                .userId(entity.getUserId())
                .principalId(entity.getPrincipalId())
                .username(entity.getUsername())
                .displayName(entity.getDisplayName())
                .email(entity.getEmail())
                .locale(entity.getLocale())
                .passwordHash(entity.getPasswordHash())
                .passwordAlgorithm(entity.getPasswordAlgorithm())
                .scopes(entity.getScopes() == null ? java.util.Set.of() : java.util.Set.copyOf(entity.getScopes()))
                .tokenVersion(entity.getTokenVersion() == null ? 0L : entity.getTokenVersion())
                .mustChangePassword(entity.getMustChangePassword() != null && entity.getMustChangePassword() == 1)
                .failedLoginAttempts(entity.getFailedLoginAttempts() == null ? 0 : entity.getFailedLoginAttempts())
                .lockedUntil(entity.getLockedUntil())
                .lastLoginAt(entity.getLastLoginAt())
                .status(UserStatus.valueOf(entity.getStatus()))
                .rowVersion(entity.getRowVersion() == null ? 0L : entity.getRowVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
