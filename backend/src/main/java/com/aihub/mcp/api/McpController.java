package com.aihub.mcp.api;

import com.aihub.audit.application.AuditEvent;
import com.aihub.audit.application.AuditService;
import com.aihub.audit.domain.AuditResult;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.Permissions;
import com.aihub.job.application.IdempotencyService;
import com.aihub.mcp.application.McpResourceHandler;
import com.aihub.mcp.application.McpToolCatalog;
import com.aihub.mcp.application.McpToolCatalog.ToolDefinition;
import com.aihub.platform.observability.application.PlatformMetrics;
import com.aihub.shared.error.AuthorizationException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.idempotency.IdempotencyKey;
import com.aihub.shared.idempotency.IdempotencySupport;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP Streamable HTTP 控制器。
 *
 * <p>实现 MCP 协议的 JSON-RPC 端点：initialize、tools/list、tools/call、resources/list、resources/read。
 * JWT 认证复用 {@code PrincipalContext}。响应体最大 1MB，工具调用超时 30s。
 */
@RestController
@RequestMapping("/mcp")
public class McpController {

    private static final Logger LOG = LoggerFactory.getLogger(McpController.class);
    private static final String MCP_PROTOCOL_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "aihub-mcp-server";
    private static final String SERVER_VERSION = "0.1.0";

    private final McpToolCatalog toolCatalog;
    private final McpResourceHandler resourceHandler;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final PlatformMetrics platformMetrics;
    private final IdempotencyService idempotencyService;
    private final IdempotencySupport idempotencySupport;
    private final ObjectMapper objectMapper;

    public McpController(McpToolCatalog toolCatalog,
                         McpResourceHandler resourceHandler,
                         AuthorizationService authorizationService,
                         AuditService auditService,
                         PlatformMetrics platformMetrics,
                         IdempotencyService idempotencyService,
                         ObjectMapper objectMapper) {
        this.toolCatalog = toolCatalog;
        this.resourceHandler = resourceHandler;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.platformMetrics = platformMetrics;
        this.idempotencyService = idempotencyService;
        this.idempotencySupport = new IdempotencySupport(objectMapper);
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> handleJsonRpc(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyHeader) {
        String method = (String) request.get("method");
        Object id = request.get("id");

        if (method == null) {
            return ResponseEntity.badRequest().body(errorResponse(id, -32600, "Invalid Request: missing method"));
        }

        LOG.info("MCP JSON-RPC method={} principal={}", method,
                PrincipalContextHolder.current().map(c -> c.principalId()).orElse("anonymous"));

        return switch (method) {
            case "initialize" -> ResponseEntity.ok(successResponse(id, handleInitialize()));
            case "tools/list" -> ResponseEntity.ok(successResponse(id, handleToolsList()));
            case "tools/call" -> {
                Map<String, Object> params = getParams(request);
                yield ResponseEntity.ok(successResponse(id, handleToolsCall(params, idempotencyHeader)));
            }
            case "resources/list" -> ResponseEntity.ok(successResponse(id, handleResourcesList()));
            case "resources/read" -> {
                Map<String, Object> params = getParams(request);
                yield ResponseEntity.ok(successResponse(id, handleResourcesRead(params)));
            }
            default -> ResponseEntity.ok(errorResponse(id, -32601, "Method not found: " + method));
        };
    }

    private Map<String, Object> handleInitialize() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("protocolVersion", MCP_PROTOCOL_VERSION);
        result.put("serverInfo", Map.of("name", SERVER_NAME, "version", SERVER_VERSION));
        result.put("capabilities", Map.of(
                "tools", Map.of("listChanged", false),
                "resources", Map.of("subscribe", false, "listChanged", false)));
        return result;
    }

    private Map<String, Object> handleToolsList() {
        PrincipalContext context = requireAuthenticatedPrincipal();
        try {
            authorizationService.requirePermission(context, Permissions.MCP_INVOKE);
        } catch (AuthorizationException e) {
            return toolError("Missing required scope: mcp:invoke");
        }

        List<Map<String, Object>> tools = toolCatalog.listVisibleTools().stream()
                .map(this::toToolListEntry)
                .toList();
        return Map.of("tools", tools);
    }

    private Map<String, Object> handleToolsCall(Map<String, Object> params, String idempotencyHeader) {
        String toolName = (String) params.get("name");
        if (toolName == null) {
            return toolError("Missing tool name");
        }

        PrincipalContext context = PrincipalContextHolder.current().orElse(null);
        if (context == null || context.principalId() == null) {
            auditToolDenied(null, null, toolName, ErrorCode.AUTH_UNAUTHENTICATED.name());
            return toolError("Unauthenticated");
        }

        ToolDefinition toolDef = toolCatalog.findTool(toolName).orElse(null);
        if (toolDef == null) {
            auditToolDenied(context.principalId(), context.principalType(), toolName,
                    ErrorCode.MCP_TOOL_NOT_ALLOWED.name());
            return toolError("Unknown tool: " + toolName);
        }

        try {
            authorizationService.requirePermission(context, Permissions.MCP_INVOKE);
            authorizationService.requirePermission(context, toolDef.requiredPermission());
            if (context.principalType() == PrincipalType.AGENT) {
                authorizationService.requireToolAllowed(context.principalId(), toolName);
            }
        } catch (AuthorizationException e) {
            auditToolDenied(context.principalId(), context.principalType(), toolName, e.errorCode().name());
            return toolError("Tool not allowed: " + toolName);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = params.get("arguments") instanceof Map m ? m : Map.of();

        try {
            if (toolDef.write()) {
                String idempotencyKeyValue = resolveIdempotencyKey(params, idempotencyHeader);
                if (idempotencyKeyValue == null || idempotencyKeyValue.isBlank()) {
                    return toolError("Idempotency key required for write tools");
                }
                IdempotencyKey key = idempotencySupport.buildKey(
                        idempotencyKeyValue, context.principalId(), "POST", "/mcp/tools/call/" + toolName);
                String fingerprint = idempotencySupport.sha256Digest(arguments);
                var idempotent = idempotencyService.execute(key, fingerprint, () -> {
                    Object toolResult = toolCatalog.callTool(toolName, arguments);
                    return new IdempotencyService.IdempotencyResponse(200, serializeResult(toolResult));
                });
                platformMetrics.recordToolCall(toolName, "success");
                return Map.of("isError", false, "content",
                        List.of(Map.of("type", "text", "text", idempotent.response().body())));
            }

            Object result = toolCatalog.callTool(toolName, arguments);
            platformMetrics.recordToolCall(toolName, "success");
            return Map.of("isError", false, "content",
                    List.of(Map.of("type", "text", "text", serializeResult(result))));
        } catch (Exception e) {
            LOG.warn("MCP tool call failed tool={} error={}", toolName, e.getMessage());
            platformMetrics.recordToolCall(toolName, "error");
            return toolError("Error: " + e.getMessage());
        }
    }

    private static String resolveIdempotencyKey(Map<String, Object> params, String headerValue) {
        Object fromParams = params.get("_idempotencyKey");
        if (fromParams != null && !fromParams.toString().isBlank()) {
            return fromParams.toString();
        }
        return headerValue;
    }

    private PrincipalContext requireAuthenticatedPrincipal() {
        return PrincipalContextHolder.current()
                .filter(ctx -> ctx.principalId() != null)
                .orElseThrow(() -> new AuthorizationException(
                        ErrorCode.AUTH_UNAUTHENTICATED, "no authenticated principal", Map.of()));
    }

    private void auditToolDenied(String principalId, PrincipalType principalType,
                                 String toolName, String errorCode) {
        auditService.record(new AuditEvent(
                "AGENT_ACCESS_DENIED",
                "mcp:tool:call",
                principalId,
                principalType == null ? null : principalType.name(),
                "MCP_TOOL",
                toolName,
                null,
                null,
                AuditResult.DENIED,
                errorCode,
                Map.of("tool", toolName)));
    }

    private Map<String, Object> toToolListEntry(ToolDefinition tool) {
        Map<String, Object> toolDef = new LinkedHashMap<>();
        toolDef.put("name", tool.name());
        toolDef.put("description", tool.description());
        toolDef.put("inputSchema", tool.inputSchema());
        return toolDef;
    }

    private String serializeResult(Object result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return String.valueOf(result);
        }
    }

    private Map<String, Object> handleResourcesList() {
        return Map.of("resourceTemplates", resourceHandler.listResources());
    }

    private Map<String, Object> handleResourcesRead(Map<String, Object> params) {
        String uri = (String) params.get("uri");
        if (uri == null) {
            return Map.of("contents", List.of());
        }
        try {
            Map<String, Object> content = resourceHandler.readResource(uri);
            return Map.of("contents", List.of(content));
        } catch (Exception e) {
            LOG.warn("MCP resource read failed uri={} error={}", uri, e.getMessage());
            return Map.of("contents", List.of(Map.of(
                    "uri", uri, "mimeType", "application/json",
                    "text", "{\"error\": \"" + e.getMessage() + "\"}")));
        }
    }

    private static Map<String, Object> toolError(String message) {
        return Map.of("isError", true, "content",
                List.of(Map.of("type", "text", "text", message)));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getParams(Map<String, Object> request) {
        Object params = request.get("params");
        return params instanceof Map ? (Map<String, Object>) params : Map.of();
    }

    private Map<String, Object> successResponse(Object id, Map<String, Object> result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return response;
    }

    private Map<String, Object> errorResponse(Object id, int code, String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("error", Map.of("code", code, "message", message));
        return response;
    }
}
