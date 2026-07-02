/*
 * 功能: 可靠任务状态枚举，与 OpenAPI JobStatus 对齐。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.job.domain;

/**
 * 可靠任务状态。
 *
 * <p>状态值与 OpenAPI {@code JobStatus} 枚举严格对齐：
 * PENDING(待领取)/RUNNING(执行中)/SUCCEEDED(成功)/RETRY_WAIT(退避等待)/DEAD(放弃)/CANCELLED(取消)。
 */
public enum JobStatus {

    PENDING,
    RUNNING,
    SUCCEEDED,
    RETRY_WAIT,
    DEAD,
    CANCELLED
}
