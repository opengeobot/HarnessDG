package com.aihub.mcp.api;

import com.aihub.mcp.application.McpResourceHandler;
import com.aihub.mcp.application.McpToolCatalog;
import com.aihub.authorization.application.AuthorizationService;
import com.aihub.platform.observability.application.PlatformMetrics;
import com.aihub.shared.identity.PrincipalContextHolder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final PlatformMetrics platformMetrics;

    public McpController(McpToolCatalog toolCatalog,
                         McpResourceHandler resourceHandler,
                         AuthorizationService authorizationService,
                         PlatformMetrics platformMetrics) {
        this.toolCatalog = toolCatalog;
        this.resourceHandler = resourceHandler;
        this.authorizationService = authorizationService;
        this.platformMetrics = platformMetrics;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> handleJsonRpc(@RequestBody Map<String, Object> request) {
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
                yield ResponseEntity.ok(successResponse(id, handleToolsCall(params)));
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
        List<Map<String, Object>> tools = toolCatalog.listTools().stream()
                .map(tool -> {
                    Map<String, Object> toolDef = new LinkedHashMap<>();
                    toolDef.put("name", tool.name());
                    toolDef.put("description", tool.description());
                    toolDef.put("inputSchema", tool.inputSchema());
                    return toolDef;
                })
                .toList();
        return Map.of("tools", tools);
    }

    private Map<String, Object> handleToolsCall(Map<String, Object> params) {
        String toolName = (String) params.get("name");
        if (toolName == null) {
            return Map.of("isError", true, "content",
                    List.of(Map.of("type", "text", "text", "Missing tool name")));
        }

        // 授权检查（fail-closed：principalId 为 null 时拒绝）
        String principalId = PrincipalContextHolder.current()
                .map(c -> c.principalId())
                .orElse(null);
        if (principalId == null) {
            return Map.of("isError", true, "content",
                    List.of(Map.of("type", "text", "text", "Unauthenticated")));
        }
        try {
            authorizationService.requireToolAllowed(principalId, toolName);
        } catch (Exception e) {
            return Map.of("isError", true, "content",
                    List.of(Map.of("type", "text", "text", "Tool not allowed: " + toolName)));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = params.get("arguments") instanceof Map m ? m : Map.of();

        try {
            Object result = toolCatalog.callTool(toolName, arguments);
            platformMetrics.recordToolCall(toolName, "success");
            return Map.of("isError", false, "content",
                    List.of(Map.of("type", "text", "text", String.valueOf(result))));
        } catch (Exception e) {
            LOG.warn("MCP tool call failed tool={} error={}", toolName, e.getMessage());
            platformMetrics.recordToolCall(toolName, "error");
            return Map.of("isError", true, "content",
                    List.of(Map.of("type", "text", "text", "Error: " + e.getMessage())));
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
