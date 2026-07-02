/*
 * 功能: identity REST 请求体 DTO 集合，对应 OpenAPI 契约的 request schema（camelCase）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.api;

import java.util.List;
import java.util.Set;

/**
 * identity REST 请求体 DTO 集合。
 *
 * <p>仅承载传输结构；字段语义校验由 application/domain 层完成。口令/凭据等敏感字段绝不进入日志或审计正文。
 */
public final class IdentityRequests {

    private IdentityRequests() {
    }

    /** 登录请求（LoginRequest）。 */
    public record LoginRequest(String username, String password) {
    }

    /** 刷新请求（RefreshTokenRequest），浏览器优先使用 Cookie。 */
    public record RefreshTokenRequest(String refreshToken) {
    }

    /** 凭据交换请求（ClientCredentialTokenRequest）。 */
    public record ClientCredentialTokenRequest(String grantType, String subjectId, String credential) {
    }

    /** 改密请求（ChangePasswordRequest）。 */
    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }

    /** 创建用户请求（CreateUserRequest）。 */
    public record CreateUserRequest(String username,
                                    String displayName,
                                    String email,
                                    String locale,
                                    String temporaryPassword,
                                    Set<String> scopes) {
    }

    /** 更新用户请求（UpdateUserRequest）。 */
    public record UpdateUserRequest(String displayName, String email, String locale) {
    }

    /** 重置口令请求（ResetPasswordRequest）。 */
    public record ResetPasswordRequest(String temporaryPassword) {
    }

    /** 注册 Agent 请求（CreateAgentRequest）。 */
    public record CreateAgentRequest(String displayName,
                                     String agentType,
                                     String vendor,
                                     Integer maxSensitivityLevel,
                                     Set<String> scopes) {
    }

    /** 更新 Agent Tool 白名单请求（UpdateAgentToolAllowlistRequest）。 */
    public record UpdateAgentToolAllowlistRequest(List<String> tools) {
    }
}
