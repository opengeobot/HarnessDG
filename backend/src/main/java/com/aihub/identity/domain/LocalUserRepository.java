/*
 * 功能: 本地用户仓储端口，约定用户与其主体的持久化与查询能力。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.domain;

import com.aihub.shared.api.PageResult;
import java.time.Instant;
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

    /**
     * 记录一次登录失败并返回更新后的用户：失败计数自增，达阈值时按
     * {@link LocalUser#MAX_FAILED_ATTEMPTS} 锁定账户。
     *
     * <p>必须在**独立事务**中提交。原因是认证失败最终以 {@code AuthenticationException}
     * 抛出，若与调用方共用事务，失败计数与锁定状态会随异常回滚被一并撤销，
     * 使账户锁定与失败沉淀整体失效。实现还须保证并发失败不丢计数
     * （规格 REQ-IAM-002 要求「每次失败原子增加计数」）。
     *
     * @param userId 目标用户业务 ID
     * @param now    判定时刻，锁定截止时间由此推导
     * @return 更新后的用户（含最新失败计数与锁定状态）；用户不存在时为 {@link Optional#empty()}
     */
    Optional<LocalUser> recordLoginFailureAtomically(String userId, Instant now);

    Optional<LocalUser> findByUsername(String username);

    Optional<LocalUser> findByUserId(String userId);

    Optional<LocalUser> findByPrincipalId(String principalId);

    boolean existsByUsername(String username);

    long countActiveByScope(String scope);

    PageResult<LocalUser> search(String keyword, UserStatus status, int page, int size);
}
