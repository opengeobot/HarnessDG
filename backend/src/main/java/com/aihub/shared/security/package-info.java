/*
 * 功能: shared.security 包说明——平台认证安全的纯抽象契约（不依赖具体加密库）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */

/**
 * 公共认证安全契约。
 *
 * <p>本包仅定义 JWT 签发/校验、口令哈希、口令策略与 Token 吊销检查的"纯抽象/契约"，
 * 不依赖 Spring、JJWT、MyBatis 等具体实现库；依赖具体库的实现位于
 * {@code com.aihub.platform.security}。
 *
 * <p>属于 shared-kernel：业务模块禁止自行解析 Token，只通过
 * {@link com.aihub.shared.identity.PrincipalContext} 读取主体。
 */
package com.aihub.shared.security;
