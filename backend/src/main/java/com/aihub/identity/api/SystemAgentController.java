/*
 * 功能: 系统 Agent 管理 REST 适配器，提供 Agent 查询、注册、启停与 Tool 白名单替换接口。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.api;

import com.aihub.identity.api.IdentityRequests.CreateAgentRequest;
import com.aihub.identity.api.IdentityRequests.UpdateAgentToolAllowlistRequest;
import com.aihub.identity.api.IdentityResponses.AgentPayload;
import com.aihub.identity.api.IdentityResponses.CreatedAgentPayload;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.identity.application.AgentManagementApplicationService;
import com.aihub.identity.application.CreatedAgentResult;
import com.aihub.identity.application.IdentityCommands;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.error.ValidationException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统 Agent 管理 REST 适配器。
 *
 * <p>所有端点要求认证 + 统一 {@link AuthorizationService} 权限校验（fail-closed）。
 * P0-B 合并规则：JWT 粗粒度 Scope 与 RBAC 角色解析任一命中所需权限即放行。注册响应中的一次性凭据仅返回一次。
 */
@RestController
@RequestMapping("/api/v1/system/agents")
public class SystemAgentController {

    private final AgentManagementApplicationService agentService;
    private final AuthorizationService authorizationService;

    public SystemAgentController(AgentManagementApplicationService agentService,
                                 AuthorizationService authorizationService) {
        this.agentService = agentService;
        this.authorizationService = authorizationService;
    }

    /**
     * 查询 Agent 列表。
     */
    @GetMapping
    public ApiResponse<List<AgentPayload>> listAgents() {
        authorizationService.requirePermission(Permissions.AUTHORIZATION_READ);
        return IdentityApiContext.respond(
                agentService.listAgents().stream().map(AgentPayload::from).toList());
    }

    /**
     * 注册 Agent。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CreatedAgentPayload> createAgent(@RequestBody CreateAgentRequest request) {
        authorizationService.requirePermission(Permissions.AGENT_REGISTER);
        int maxSensitivity = request.maxSensitivityLevel() == null ? 0 : request.maxSensitivityLevel();
        CreatedAgentResult result = agentService.registerAgent(new IdentityCommands.CreateAgentCommand(
                request.displayName(), request.agentType(), request.vendor(),
                maxSensitivity, request.scopes()));
        return IdentityApiContext.respond(CreatedAgentPayload.from(result));
    }

    /**
     * 启用 Agent。
     */
    @PostMapping("/{agentId}:enable")
    public ApiResponse<Void> enableAgent(@PathVariable String agentId) {
        authorizationService.requirePermission(Permissions.AGENT_AUTHORIZE);
        agentService.enableAgent(agentId);
        return IdentityApiContext.respond(null);
    }

    /**
     * 禁用 Agent 并吊销凭据/Token。
     */
    @PostMapping("/{agentId}:disable")
    public ApiResponse<Void> disableAgent(@PathVariable String agentId) {
        authorizationService.requirePermission(Permissions.AGENT_AUTHORIZE);
        agentService.disableAgent(agentId);
        return IdentityApiContext.respond(null);
    }

    /**
     * 替换 Agent MCP Tool 白名单。
     */
    @PutMapping("/{agentId}/tool-allowlist")
    public ApiResponse<AgentPayload> updateToolAllowlist(@PathVariable String agentId,
                                                         @RequestBody UpdateAgentToolAllowlistRequest request) {
        authorizationService.requirePermission(Permissions.AGENT_AUTHORIZE);
        if (request == null || request.tools() == null) {
            throw new ValidationException("tools is required");
        }
        return IdentityApiContext.respond(
                AgentPayload.from(agentService.updateToolAllowlist(agentId, request.tools())));
    }
}
