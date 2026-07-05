/*
 * 功能: MCP JSON-RPC 控制器 Web 切片测试——initialize、tools/list、tools/call（含授权拒绝）、
 *       resources/list、resources/read、未知方法。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.mcp.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.domain.AgentToolRepository;
import com.aihub.authorization.domain.ResourceAclRepository;
import com.aihub.authorization.domain.RoleBindingRepository;
import com.aihub.bootstrap.GlobalExceptionHandler;
import com.aihub.bootstrap.PrincipalContextFilter;
import com.aihub.bootstrap.SharedKernelConfiguration;
import com.aihub.bootstrap.security.RestAccessDeniedHandler;
import com.aihub.bootstrap.security.RestAuthenticationEntryPoint;
import com.aihub.bootstrap.security.SecurityConfiguration;
import com.aihub.identity.api.RefreshCookieFactory;
import com.aihub.identity.api.RefreshCookieProperties;
import com.aihub.mcp.application.McpResourceHandler;
import com.aihub.mcp.application.McpToolCatalog;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.IssuedToken;
import com.aihub.shared.security.TokenIssueRequest;
import com.aihub.shared.security.TokenSigner;
import com.aihub.shared.security.TokenType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MCP JSON-RPC 控制器 Web 切片测试。
 *
 * <p>授权走真实 {@link AuthorizationService}，{@code AgentToolRepository} 以空集桩返回控制工具允许/拒绝。
 */
@WebMvcTest(McpController.class)
@Import({McpController.class,
        PrincipalContextFilter.class, SharedKernelConfiguration.class, GlobalExceptionHandler.class,
        SecurityConfiguration.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        RefreshCookieFactory.class, AuthorizationService.class})
class McpControllerTest {

    @Configuration
    @org.springframework.boot.context.properties.EnableConfigurationProperties(RefreshCookieProperties.class)
    static class TestConfig {
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenSigner tokenSigner;

    @MockitoBean
    private McpToolCatalog toolCatalog;
    @MockitoBean
    private McpResourceHandler resourceHandler;
    @MockitoBean
    private RoleBindingRepository roleBindingRepository;
    @MockitoBean
    private ResourceAclRepository resourceAclRepository;
    @MockitoBean
    private AgentToolRepository agentToolRepository;

    @BeforeEach
    void stubAuthorizationRepositories() {
        given(roleBindingRepository.resolvePermissionCodes(any())).willReturn(Set.of());
        // 默认：所有工具允许
        given(agentToolRepository.isToolAllowed(anyString(), anyString())).willReturn(true);
    }

    private String bearer(Set<String> scopes) {
        IssuedToken token = tokenSigner.issue(new TokenIssueRequest(
                "prn_agent", PrincipalType.AGENT, 0L, scopes, TokenType.ACCESS));
        return "Bearer " + token.token();
    }

    private String jsonRpc(String method) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\"}";
    }

    private String jsonRpcWithParams(String method, String params) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"" + method + "\",\"params\":" + params + "}";
    }

    @Test
    void initializeReturnsServerInfo() throws Exception {
        mockMvc.perform(post("/mcp")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("mcp:invoke")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRpc("initialize")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.result.protocolVersion").value("2024-11-05"))
                .andExpect(jsonPath("$.result.serverInfo.name").value("aihub-mcp-server"));
    }

    @Test
    void toolsListReturnsRegisteredTools() throws Exception {
        given(toolCatalog.listTools()).willReturn(List.of(
                new McpToolCatalog.ToolDefinition("asset_search", "Search assets",
                        Map.of("type", "object"), false, "asset:read")));
        mockMvc.perform(post("/mcp")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("mcp:invoke")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRpc("tools/list")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools[0].name").value("asset_search"));
    }

    @Test
    void toolsCallReturnsErrorWhenToolNotAllowed() throws Exception {
        // 拒绝所有工具调用
        given(agentToolRepository.isToolAllowed(anyString(), anyString())).willReturn(false);
        mockMvc.perform(post("/mcp")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("mcp:invoke")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRpcWithParams("tools/call",
                                "{\"name\":\"dangerous_tool\",\"arguments\":{}}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(true));
    }

    @Test
    void toolsCallSucceedsWhenAuthorized() throws Exception {
        given(toolCatalog.callTool(eq("asset_search"), anyMap()))
                .willReturn("search results");
        mockMvc.perform(post("/mcp")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("mcp:invoke")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRpcWithParams("tools/call",
                                "{\"name\":\"asset_search\",\"arguments\":{\"keyword\":\"test\"}}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(false));
    }

    @Test
    void resourcesListReturnsTemplates() throws Exception {
        given(resourceHandler.listResources()).willReturn(List.of(
                Map.of("uriTemplate", "aih://asset/{assetId}", "name", "Asset details",
                        "mimeType", "application/json")));
        mockMvc.perform(post("/mcp")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("mcp:invoke")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRpc("resources/list")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.resourceTemplates[0].name").value("Asset details"));
    }

    @Test
    void resourcesReadReturnsContent() throws Exception {
        given(resourceHandler.readResource("aih://asset/ast_01"))
                .willReturn(Map.of("uri", "aih://asset/ast_01", "mimeType", "application/json",
                        "text", "{\"id\":\"ast_01\"}"));
        mockMvc.perform(post("/mcp")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("mcp:invoke")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRpcWithParams("resources/read",
                                "{\"uri\":\"aih://asset/ast_01\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.contents[0].uri").value("aih://asset/ast_01"));
    }

    @Test
    void unknownMethodReturnsMethodNotFound() throws Exception {
        mockMvc.perform(post("/mcp")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("mcp:invoke")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRpc("unknown/method")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32601))
                .andExpect(jsonPath("$.error.message").value("Method not found: unknown/method"));
    }

    @Test
    void missingMethodReturnsInvalidRequest() throws Exception {
        mockMvc.perform(post("/mcp")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Set.of("mcp:invoke")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jsonrpc\":\"2.0\",\"id\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(-32600));
    }
}
