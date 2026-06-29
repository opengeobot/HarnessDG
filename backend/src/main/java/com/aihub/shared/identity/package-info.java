/*
 * 功能: shared.identity 包说明——统一主体上下文抽象。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */

/**
 * 公共身份契约：主体类型 {@link com.aihub.shared.identity.PrincipalType}、
 * 主体上下文 {@link com.aihub.shared.identity.PrincipalContext} 与
 * 线程级持有者 {@link com.aihub.shared.identity.PrincipalContextHolder}。
 *
 * <p>本包属于 shared-kernel，业务模块禁止解析 JWT/Cookie/Token，只读取上下文。
 */
package com.aihub.shared.identity;
