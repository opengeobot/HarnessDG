/*
 * 功能: job 模块通知出站端口，任务进入 DEAD 时通知 notification 模块发送 JOB_DEAD 通知。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.job.application;

/**
 * job 通知出站端口。
 *
 * <p>job 模块不直接依赖 notification 模块，通过本端口解耦；由 notification 模块提供实现。
 * 任务进入 DEAD 时调用 {@link #notifyJobDead} 触发 JOB_DEAD 通知。
 */
public interface JobNotificationPort {

    /**
     * 通知任务死亡。
     *
     * @param jobId      任务业务 ID
     * @param jobType    任务类型
     * @param retryCount 累计尝试次数
     * @param errorCode  错误码
     * @param principalId 触发主体 ID（可空）
     */
    void notifyJobDead(String jobId, String jobType, int retryCount, String errorCode, String principalId);
}
