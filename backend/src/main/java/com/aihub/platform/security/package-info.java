/*
 * 功能: platform.security 模块说明——平台认证安全能力的具体实现（依赖 JJWT/Spring Security）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */

/**
 * 平台认证安全实现模块（platform-security）。
 *
 * <p>实现 shared 定义的纯抽象契约：
 * <ul>
 *   <li>{@link com.aihub.shared.security.TokenSigner}/{@link com.aihub.shared.security.TokenVerifier}
 *       —— 基于 JJWT 的非对称（RSA）签名，JWT 头携带 {@code kid}；</li>
 *   <li>{@link com.aihub.shared.security.PasswordHasher} —— 基于 Spring Security BCrypt。</li>
 * </ul>
 *
 * <p>密钥来源为部署 Secret（配置项）；未配置时仅在非生产场景生成临时密钥对并告警，
 * 绝不硬编码生产私钥，绝不将私钥写入日志。
 */
package com.aihub.platform.security;
