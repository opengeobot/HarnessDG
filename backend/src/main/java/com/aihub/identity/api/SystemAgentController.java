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
import com.aihub.job.application.IdempotencyService;
import com.aihub.shared.api.ApiResponse;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.idempotency.IdempotencyKey;
import com.aihub.shared.idempotency.IdempotencySupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final IdempotencyService idempotencyService;
    private final IdempotencySupport idempotency;

    public SystemAgentController(AgentManagementApplicationService agentService,
                                 AuthorizationService authorizationService,
                                 IdempotencyService idempotencyService,
                                 ObjectMapper objectMapper) {
        this.agentService = agentService;
        this.authorizationService = authorizationService;
        this.idempotencyService = idempotencyService;
        this.idempotency = new IdempotencySupport(objectMapper);
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
    public ApiResponse<CreatedAgentPayload> createAgent(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue,
            @RequestBody CreateAgentRequest request) {
        authorizationService.requirePermission(Permissions.AGENT_REGISTER);
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                IdentityApiContext.principalId(), "POST", "/api/v1/system/agents");
        String fingerprint = idempotency.sha256Digest(request);
        AtomicReference<CreatedAgentPayload> ref = new AtomicReference<>();
        idempotencyService.execute(key, fingerprint, () -> {
            int maxSensitivity = request.maxSensitivityLevel() == null ? 0 : request.maxSensitivityLevel();
            CreatedAgentResult result = agentService.registerAgent(new IdentityCommands.CreateAgentCommand(
                    request.displayName(), request.agentType(), request.vendor(),
                    maxSensitivity, request.scopes()));
            CreatedAgentPayload payload = CreatedAgentPayload.from(result);
            ref.set(payload);
            return new IdempotencyService.IdempotencyResponse(201, idempotency.serialize(payload));
        });
        return IdentityApiContext.respond(ref.get());
    }

    /**
     * 启用 Agent。
     */
    @PostMapping("/{agentId}:enable")
    public ApiResponse<Void> enableAgent(
            @PathVariable String agentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue) {
        authorizationService.requirePermission(Permissions.AGENT_AUTHORIZE);
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                IdentityApiContext.principalId(), "POST", "/api/v1/system/agents/" + agentId + ":enable");
        idempotencyService.execute(key, null, () -> {
            agentService.enableAgent(agentId);
            return new IdempotencyService.IdempotencyResponse(200, "");
        });
        return IdentityApiContext.respond(null);
    }

    /**
     * 禁用 Agent 并吊销凭据/Token。
     */
    @PostMapping("/{agentId}:disable")
    public ApiResponse<Void> disableAgent(
            @PathVariable String agentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue) {
        authorizationService.requirePermission(Permissions.AGENT_AUTHORIZE);
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                IdentityApiContext.principalId(), "POST", "/api/v1/system/agents/" + agentId + ":disable");
        idempotencyService.execute(key, null, () -> {
            agentService.disableAgent(agentId);
            return new IdempotencyService.IdempotencyResponse(200, "");
        });
        return IdentityApiContext.respond(null);
    }

    /**
     * 替换 Agent MCP Tool 白名单。
     */
    @PutMapping("/{agentId}/tool-allowlist")
    public ApiResponse<AgentPayload> updateToolAllowlist(
            @PathVariable String agentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyValue,
            @RequestBody UpdateAgentToolAllowlistRequest request) {
        authorizationService.requirePermission(Permissions.AGENT_AUTHORIZE);
        if (request == null || request.tools() == null) {
            throw new ValidationException("tools is required");
        }
        IdempotencyKey key = idempotency.buildKey(idempotencyKeyValue,
                IdentityApiContext.principalId(), "PUT",
                "/api/v1/system/agents/" + agentId + "/tool-allowlist");
        String fingerprint = idempotency.sha256Digest(request);
        AtomicReference<AgentPayload> ref = new AtomicReference<>();
        idempotencyService.execute(key, fingerprint, () -> {
            AgentPayload payload = AgentPayload.from(
                    agentService.updateToolAllowlist(agentId, request.tools()));
            ref.set(payload);
            return new IdempotencyService.IdempotencyResponse(200, idempotency.serialize(payload));
        });
        return IdentityApiContext.respond(ref.get());
    }
}
