/*
 * 功能: platform.concurrency 模块说明——受管线程池与上下文传播装饰器。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */

/**
 * 平台受管并发模块（platform-concurrency）。
 *
 * <p>提供进程内短任务的受管 Executor（{@code ioExecutor}/{@code notificationExecutor}/
 * {@code auditExecutor}/{@code schedulerExecutor}），统一拒绝策略、Micrometer 指标绑定与
 * {@link com.aihub.shared.identity.PrincipalContext}/MDC 上下文传播。
 *
 * <p>本模块仅承载"进程内短任务"；DVC 转存、发布、Webhook、对账等不可丢失的可靠任务必须走
 * 持久化任务系统，不得使用这些 Executor。
 */
package com.aihub.platform.concurrency;
