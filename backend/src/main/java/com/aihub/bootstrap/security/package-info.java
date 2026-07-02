/*
 * 功能: bootstrap.security 包说明——Spring Security fail-closed 装配与 JWT 认证适配。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */

/**
 * 安全装配适配层。
 *
 * <p>承载 Spring Security 过滤链装配（{@link com.aihub.bootstrap.security.SecurityConfiguration}）、
 * JWT 认证过滤器（{@link com.aihub.bootstrap.security.JwtAuthenticationFilter}）以及统一的
 * 401/403 响应处理器。属于启动/适配层，复用 shared 的安全契约与主体上下文。
 */
package com.aihub.bootstrap.security;
