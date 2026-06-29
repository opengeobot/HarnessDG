/*
 * 功能: shared.id 包说明——统一业务 ID 生成。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */

/**
 * 公共 ID 契约：前缀枚举 {@link com.aihub.shared.id.IdPrefix}、生成器接口
 * {@link com.aihub.shared.id.IdGenerator} 与 ULID 实现
 * {@link com.aihub.shared.id.UlidIdGenerator}，统一产出"业务前缀_ULID"。
 *
 * <p>本包属于 shared-kernel，禁止依赖任何业务模块或基础设施 SDK。
 */
package com.aihub.shared.id;
