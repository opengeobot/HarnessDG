/*
 * 功能: 幂等存储契约，约定首次结果记录与命中复用能力。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.shared.idempotency;

import java.util.Optional;

/**
 * 幂等存储。
 *
 * <p>持久化首次执行结果并在命中时返回，保证写接口"恰好一次"语义。实现必须基于持久化存储
 * （PostgreSQL {@code api_idempotency}），由后续数据迁移阶段提供，本任务仅定义契约。
 */
public interface IdempotencyStore {

    /**
     * 查找已存在的幂等记录。
     *
     * @param key 幂等键
     * @return 命中时返回首次结果，否则为空
     */
    Optional<IdempotencyRecord> find(IdempotencyKey key);

    /**
     * 记录首次执行结果；若并发下已存在则返回已存在记录。
     *
     * @param record 首次执行结果
     * @return 实际生效的记录（首次写入或并发已存在）
     */
    IdempotencyRecord save(IdempotencyRecord record);
}
