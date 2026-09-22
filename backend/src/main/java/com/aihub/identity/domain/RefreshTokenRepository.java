/*
 * 功能: 刷新令牌仓储端口，约定令牌摘要的落库、轮换、吊销与重放检测查询能力。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.domain;

import java.util.Optional;

/**
 * 刷新令牌仓储端口。
 *
 * <p>只持久化 jti/family/生命周期摘要；登出与重放整族吊销通过 {@link #revokeFamily} 完成。
 */
public interface RefreshTokenRepository {

    /**
     * 落库一条刷新令牌摘要（ACTIVE）。
     */
    void save(RefreshTokenRecord record);

    Optional<RefreshTokenRecord> findByJti(String jti);

    /**
     * 按 jti 摘要或明文 jti 查找（摘要优先，明文 jti 回退兼容历史记录）。
     */
    Optional<RefreshTokenRecord> findByJwtId(String jwtId);

    /**
     * 以原子 compare-and-set 抢占轮换权：仅当该 jti 仍为 ACTIVE 时将其置为 ROTATED。
     *
     * <p>并发刷新时只有把 ACTIVE 成功翻转的请求成为唯一胜者；返回 {@code false} 表示
     * 该 jti 已被消费或吊销，调用方须按重放语义处理（REQ-IAM-004「以原子 compare-and-set
     * 将 jti 从 ACTIVE 置为 ROTATED」「只有一个并发请求可以成功」）。
     *
     * @return 是否抢占成功（true 表示本次调用是唯一胜者）
     */
    boolean claimForRotation(String jti);

    /**
     * 将旧 jti 标记为已轮换并记录后继 jti。
     */
    void markRotated(String jti, String replacedByJti);

    /**
     * 吊销整个 Token Family（登出、重放检测）。
     *
     * @return 受影响行数
     */
    int revokeFamily(String tokenFamily);

    /**
     * 吊销某主体的全部刷新令牌（禁用、改密）。
     *
     * @return 受影响行数
     */
    int revokeAllByPrincipal(String principalId);
}
