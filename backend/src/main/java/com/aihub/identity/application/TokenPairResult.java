/*
 * 功能: 令牌对视图，对应契约 TokenPair；承载访问/刷新 JWT 与当前主体摘要。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.application;

/**
 * 令牌对视图。
 *
 * <p>{@code accessToken}/{@code refreshToken} 为序列化 JWT，仅用于即时下发响应/Cookie，
 * 绝不写入日志、埋点或测试 Fixture。
 *
 * @param accessToken      访问 JWT
 * @param refreshToken     刷新 JWT
 * @param expiresIn        访问 JWT 剩余秒数
 * @param refreshExpiresIn 刷新 JWT 剩余秒数
 * @param principal        当前主体摘要
 */
public record TokenPairResult(String accessToken,
                              String refreshToken,
                              long expiresIn,
                              long refreshExpiresIn,
                              CurrentPrincipalView principal) {
}
