/*
 * 功能: Token 吊销检查契约，预留主体级 tokenVersion/禁用校验钩子。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.shared.security;

/**
 * Token 吊销检查器。
 *
 * <p>身份表就绪后由 identity 模块实现：依据 {@code jti}/family 摘要与主体 {@code tokenVersion}
 * 判断 Token 是否已被吊销（改密、禁用、刷新轮换重放）。本任务身份表尚未建立，仅定义契约。
 *
 * <p>fail-closed 约定：实现缺失时不得提供"放行一切"的默认实现进入可部署 Profile。
 */
public interface TokenRevocationChecker {

    /**
     * 判断给定声明对应的 Token 是否已被吊销。
     *
     * @param claims 已通过签名校验的声明
     * @return {@code true} 表示该 Token 已被吊销，必须拒绝访问
     */
    boolean isRevoked(JwtClaims claims);
}
