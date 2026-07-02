/*
 * 功能: Outbox 仓储端口，定义事件原子写入与轮询领取能力。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.notification.domain;

import java.time.Instant;
import java.util.List;

/**
 * Outbox 仓储端口。
 *
 * <p>{@link #append} 在业务事务内调用，保证事件与业务数据原子提交。
 * {@link #claimPending} 以 FOR UPDATE SKIP LOCKED 领取待投递事件，保证多投递器不重复领取。
 */
public interface OutboxRepository {

    /** 追加一条 Outbox 事件（在业务事务内调用）。 */
    void append(OutboxEvent event);

    /**
     * 领取待投递事件（processed_at IS NULL）。
     *
     * @param limit 最多领取条数
     * @return 领取到的事件列表
     */
    List<OutboxEvent> claimPending(int limit);

    /** 标记事件已处理。 */
    void markProcessed(String eventId, Instant now);
}
