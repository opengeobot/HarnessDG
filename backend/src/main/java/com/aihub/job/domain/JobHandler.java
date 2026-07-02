/*
 * 功能: 幂等任务处理器端口，按 type 路由执行可靠任务副作用。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.job.domain;

/**
 * 幂等任务处理器端口。
 *
 * <p>每个 Handler 声明其处理的任务 {@link #type()}，Worker 按 type 路由。Handler <b>必须幂等</b>：
 * 重复执行同一任务不得产生重复副作用。抛出异常视为本次失败，由 Worker 按指数退避重试，超阈值进入 DEAD。
 *
 * <p>真实业务 Handler（DVC 校验、版本发布、Webhook 消费、对账）属后续阶段；本任务交付框架 + 示例 Handler。
 */
public interface JobHandler {

    /** @return 该 Handler 处理的任务类型 */
    String type();

    /**
     * 执行任务（必须幂等）。
     *
     * @param context 任务执行上下文
     * @throws Exception 执行失败，触发重试
     */
    void handle(JobContext context) throws Exception;
}
