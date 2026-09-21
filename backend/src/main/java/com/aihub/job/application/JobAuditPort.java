/*
 * 功能: job 模块审计端口——DEAD 终态与人工重试/取消事件的审计出口。
 * 时间: 2026-09-21
 * 作者: AxeXie
 */
package com.aihub.job.application;

import java.util.Map;

/**
 * job 模块的审计端口。
 *
 * <p>由 job 域内的用例（人工重试/取消、Worker 的 DEAD 分支）调用，桥接适配器
 * 委托共享 {@code AuditService} 追加写（对齐 {@code TaxonomyAuditAdapter} 的
 * 模块桥接模式；禁止新建本地审计表或旁路）。
 *
 * <p>事件集合固定为契约与 REQ-JOB-001 已声明的三项：
 * {@code JOB_MANUALLY_RETRIED}、{@code JOB_MANUALLY_CANCELLED}、{@code JOB_DEAD}。
 * 摘要只含 jobId/type/attempts/errorCode/actor 等治理字段，不得包含 payload 原文。
 */
public interface JobAuditPort {

    /**
     * 记录一条任务审计事件。
     *
     * @param eventType  事件类型（JOB_MANUALLY_RETRIED / JOB_MANUALLY_CANCELLED / JOB_DEAD）
     * @param action     动作（job:retry / job:cancel，Worker 侧为 null 由适配器补全）
     * @param jobId      任务业务 ID
     * @param actorId    操作者主体 ID；Worker 侧系统触发传 null（与通知口径一致落为 system）
     * @param attributes 结构化属性（落库前由共享服务脱敏）
     */
    void record(String eventType, String action, String jobId, String actorId, Map<String, Object> attributes);
}
