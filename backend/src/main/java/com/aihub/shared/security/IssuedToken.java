/*
 * 功能: 已签发 Token 结果，承载序列化 Token 字符串与解析后的声明。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.shared.security;

/**
 * Token 签发结果。
 *
 * <p>{@code token} 为序列化后的紧凑 JWT 字符串；严禁写入日志、埋点或测试 Fixture。
 *
 * @param token  序列化 JWT（紧凑串）
 * @param claims 解析后的声明，便于调用方回填响应（如 expiresAt）
 */
public record IssuedToken(String token, JwtClaims claims) {
}
