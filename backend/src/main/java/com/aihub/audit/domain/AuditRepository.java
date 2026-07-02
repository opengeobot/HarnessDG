/*
 * 功能: 审计仓储端口，定义追加写与游标查询能力（无 UPDATE/DELETE，保证不可篡改）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.audit.domain;

import java.time.Instant;
import java.util.List;

/**
 * 审计仓储端口。
 *
 * <p><b>追加写、不可篡改</b>：仅提供 {@code append} 与查询，<b>不提供任何 UPDATE/DELETE 方法</b>，
 * 应用层无更新路径；数据库层另有触发器兜底阻止改/删。
 */
public interface AuditRepository {

    /** 追加一条审计记录（不可修改/删除）。 */
    void append(AuditRecord record);

    /**
     * 游标分页查询审计记录（按 occurred_at, id 键集，降序——最新优先）。
     *
     * @param principalId  主体过滤（可空）
     * @param action       动作过滤（可空）
     * @param resourceId   资源过滤（可空）
     * @param cursorTime   游标发生时间（可空）
     * @param cursorId     游标内部 ID（可空）
     * @param limit        每页条数
     * @return 该页记录
     */
    List<AuditRecord> list(String principalId, String action, String resourceId,
                           Instant cursorTime, Long cursorId, int limit);

    /**
     * 扩展过滤游标分页查询审计记录（按 occurred_at, id 键集，降序——最新优先）。
     *
     * @param eventType     事件类型过滤（可空）
     * @param principalId   主体过滤（可空）
     * @param resourceType  资源类型过滤（可空）
     * @param resourceId    资源过滤（可空）
     * @param createdAfter  发生时间下界（可空，含）
     * @param createdBefore 发生时间上界（可空，不含）
     * @param cursorTime    游标发生时间（可空）
     * @param cursorId      游标内部 ID（可空）
     * @param limit         每页条数
     * @return 该页记录
     */
    List<AuditRecord> search(String eventType, String principalId, String resourceType, String resourceId,
                             Instant createdAfter, Instant createdBefore,
                             Instant cursorTime, Long cursorId, int limit);
}
