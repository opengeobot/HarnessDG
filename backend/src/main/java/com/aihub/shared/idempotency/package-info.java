/*
 * 功能: shared.idempotency 包说明——写接口幂等的纯抽象契约。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */

/**
 * 写接口幂等契约。
 *
 * <p>仅定义 {@code Idempotency-Key} 解析、首次结果记录与命中复用的抽象与 DTO；
 * 真实持久化表 {@code api_idempotency} 与 Store 实现由后续可靠任务/数据迁移阶段落地。
 *
 * <p>遵循 fail-closed 与"禁止内存可靠任务回退"约束：本包不提供可进入可部署 Profile 的
 * 内存实现，避免把不可靠的幂等保证固化进生产路径。
 */
package com.aihub.shared.idempotency;
