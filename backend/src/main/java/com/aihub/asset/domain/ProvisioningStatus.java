/*
 * 功能: 建仓异步状态枚举，驱动 Saga 异步建仓流程。
 * 时间: 2026-07-02
 * 作者: AxeXie
 */
package com.aihub.asset.domain;

/**
 * 建仓异步状态。
 *
 * <p>对应 {@code asset.provisioning_status} 列。新登记资产初始为 {@link #NONE}，
 * Job 驱动的 Saga 流程依次推进至 PENDING → IN_PROGRESS → COMPLETED/FAILED。
 */
public enum ProvisioningStatus {
    /** 未触发建仓（默认值）。 */
    NONE,
    /** 等待执行。 */
    PENDING,
    /** 执行中。 */
    IN_PROGRESS,
    /** 完成。 */
    COMPLETED,
    /** 失败。 */
    FAILED
}
