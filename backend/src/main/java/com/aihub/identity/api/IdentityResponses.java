/*
 * 功能: identity REST 响应体 DTO 集合，对应 OpenAPI 契约的 response schema（camelCase）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.api;

import com.aihub.identity.application.AgentView;
import com.aihub.identity.application.CreatedAgentResult;
import com.aihub.identity.application.CurrentPrincipalView;
import com.aihub.identity.application.PrincipalSummaryView;
import com.aihub.identity.application.TokenPairResult;
import com.aihub.identity.application.UserView;
import java.time.Instant;
import java.util.List;

/**
 * identity REST 响应体 DTO 集合。
 *
 * <p>严格按契约字段名输出；绝不包含口令哈希、凭据摘要等敏感信息。
 */
public final class IdentityResponses {

    private IdentityResponses() {
    }

    /** 令牌对响应（TokenPair）。{@code refreshToken} 浏览器场景置 null（仅通过 Cookie 下发）。 */
    public record TokenPairPayload(String tokenType,
                                   String accessToken,
                                   String refreshToken,
                                   long expiresIn,
                                   long refreshExpiresIn,
                                   CurrentPrincipalPayload principal) {

        /**
         * @param result        令牌对结果
         * @param includeRefresh 是否在响应体包含 refreshToken（非浏览器客户端为 true）
         */
        public static TokenPairPayload from(TokenPairResult result, boolean includeRefresh) {
            return new TokenPairPayload(
                    "Bearer",
                    result.accessToken(),
                    includeRefresh ? result.refreshToken() : null,
                    result.expiresIn(),
                    result.refreshExpiresIn(),
                    CurrentPrincipalPayload.from(result.principal()));
        }
    }

    /** 当前主体响应（CurrentPrincipal）。 */
    public record CurrentPrincipalPayload(String principalId,
                                          String userId,
                                          String principalType,
                                          String subject,
                                          String displayName,
                                          String organizationId,
                                          List<String> roles,
                                          List<String> scopes,
                                          String locale,
                                          boolean forcePasswordChange) {

        public static CurrentPrincipalPayload from(CurrentPrincipalView view) {
            return new CurrentPrincipalPayload(
                    view.principalId(),
                    view.userId(),
                    view.principalType().name(),
                    view.subject(),
                    view.displayName(),
                    view.organizationId(),
                    view.roles(),
                    view.scopes(),
                    view.locale(),
                    view.forcePasswordChange());
        }
    }

    /** 主体摘要响应（PrincipalSummary）。 */
    public record PrincipalSummaryPayload(String principalId,
                                          String principalType,
                                          String subject,
                                          String displayName,
                                          String status) {

        public static PrincipalSummaryPayload from(PrincipalSummaryView view) {
            return new PrincipalSummaryPayload(view.principalId(), view.principalType().name(),
                    view.subject(), view.displayName(), view.status());
        }
    }

    /** 用户响应（UserView）。 */
    public record UserPayload(String userId,
                              String principalId,
                              String username,
                              String displayName,
                              String email,
                              String locale,
                              String status,
                              boolean forcePasswordChange,
                              Instant lastLoginAt,
                              Instant createdAt,
                              Instant updatedAt) {

        public static UserPayload from(UserView view) {
            return new UserPayload(view.userId(), view.principalId(), view.username(), view.displayName(),
                    view.email(), view.locale(), view.status().name(), view.forcePasswordChange(),
                    view.lastLoginAt(), view.createdAt(), view.updatedAt());
        }
    }

    /** 用户分页响应（PageResultUser）。 */
    public record PageResultUserPayload(List<UserPayload> items, int page, int size, long total) {
    }

    /** Agent 响应（AgentView）。 */
    public record AgentPayload(String agentId,
                               String principalId,
                               String displayName,
                               String agentType,
                               String vendor,
                               String status,
                               int maxSensitivityLevel,
                               List<String> scopes,
                               List<String> toolAllowlist) {

        public static AgentPayload from(AgentView view) {
            return new AgentPayload(view.agentId(), view.principalId(), view.displayName(), view.agentType(),
                    view.vendor(), view.status().name(), view.maxSensitivityLevel(),
                    view.scopes(), view.toolAllowlist());
        }
    }

    /** 新建 Agent 响应（CreatedAgent，含一次性凭据）。 */
    public record CreatedAgentPayload(String agentId,
                                      String principalId,
                                      String displayName,
                                      String agentType,
                                      String vendor,
                                      String status,
                                      int maxSensitivityLevel,
                                      List<String> scopes,
                                      List<String> toolAllowlist,
                                      String credential) {

        public static CreatedAgentPayload from(CreatedAgentResult result) {
            AgentView view = result.agent();
            return new CreatedAgentPayload(view.agentId(), view.principalId(), view.displayName(),
                    view.agentType(), view.vendor(), view.status().name(), view.maxSensitivityLevel(),
                    view.scopes(), view.toolAllowlist(), result.credential());
        }
    }
}
