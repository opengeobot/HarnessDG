/*
 * 功能: 本地用户仓储端口，约定用户与其主体的持久化与查询能力。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.domain;

import com.aihub.shared.api.PageResult;
import java.util.Optional;

/**
 * 本地用户仓储端口。
 *
 * <p>领域层只依赖本端口；实现位于 infrastructure。创建用户时同时落地其 {@link PrincipalAccount}。
 */
public interface LocalUserRepository {

    /**
     * 创建主体与用户（同一事务）。
     */
    void create(PrincipalAccount principal, LocalUser user);

    /**
     * 持久化用户状态变更（基于 row_version 乐观锁）。
     *
     * @return 是否更新成功（false 表示并发冲突）
     */
    boolean update(LocalUser user);

    Optional<LocalUser> findByUsername(String username);

    Optional<LocalUser> findByUserId(String userId);

    Optional<LocalUser> findByPrincipalId(String principalId);

    boolean existsByUsername(String username);

    long countActiveByScope(String scope);

    PageResult<LocalUser> search(String keyword, UserStatus status, int page, int size);
}
