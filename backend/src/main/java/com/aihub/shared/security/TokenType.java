/*
 * 功能: Token 类型枚举，区分访问令牌与刷新令牌。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.shared.security;

/**
 * 平台签发的 Token 类型。
 */
public enum TokenType {

    /** 访问令牌（默认 15 分钟，承载粗粒度身份与 Scope）。 */
    ACCESS,

    /** 刷新令牌（默认 7 天，轮换并支持重放检测）。 */
    REFRESH
}
