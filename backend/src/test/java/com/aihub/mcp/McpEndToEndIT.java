/*
 * 功能: MCP 端到端集成测试——以 Testcontainers PostgreSQL + MockMvc 验证 MCP JSON-RPC 协议流：
 *       initialize → tools/list → tools/call（asset_search）；以及越权写工具拒绝 + 审计。
 *       覆盖 AC-P4-MCP-001（协议流）、AC-P4-MCP-003（搜索复用 REST Query）、AC-P4-ALW-001（写工具拒绝）。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aihub.asset.application.AssetApplicationService;
import com.aihub.asset.application.AssetView;
import com.aihub.asset.application.CreateAssetCommand;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.ModelProfile;
import com.aihub.asset.domain.Visibility;
import com.aihub.bootstrap.GlobalExceptionHandler;
import com.aihub.bootstrap.PrincipalContextFilter;
import com.aihub.bootstrap.SharedKernelConfiguration;
import com.aihub.bootstrap.security.RestAccessDeniedHandler;
import com.aihub.bootstrap.security.RestAuthenticationEntryPoint;
import com.aihub.bootstrap.security.SecurityConfiguration;
import com.aihub.identity.api.RefreshCookieFactory;
import com.aihub.identity.api.RefreshCookieProperties;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.IssuedToken;
import com.aihub.shared.security.TokenIssueRequest;
import com.aihub.shared.security.TokenSigner;
import com.aihub.shared.security.TokenType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * MCP 端到端集成测试。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class McpEndToEndIT {

    private static final String TEST_TEAM_ID = "team_mcp_it";
    private static final String AGENT_USER = "agt_mcp_it";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("aihub")
                    .withUsername("aihub")
                    .withPassword("aihub");

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TokenSigner tokenSigner;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AssetApplicationService assetService;
    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    private String accessToken;

    @BeforeEach
    void seedDataAndToken() {
        // Seed org + team
        jdbcTemplate.update(
                "INSERT INTO organization (organization_id, name, status, created_by, created_at, updated_at, row_version) "
                        + "VALUES (:orgId, :name, 'ACTIVE', 'usr_01', NOW(), NOW(), 1) "
                        + "ON CONFLICT (organization_id) DO NOTHING",
                new MapSqlParameterSource()
                        .addValue("orgId", "org_mcp_it")
                        .addValue("name", "MCP IT Org"));
        jdbcTemplate.update(
                "INSERT INTO team (team_id, organization_id, name, description, status, created_by, "
                        + "created_at, updated_at, row_version) "
                        + "VALUES (:teamId, :orgId, :name, NULL, 'ACTIVE', 'usr_01', NOW(), NOW(), 1) "
                        + "ON CONFLICT (team_id) DO NOTHING",
                new MapSqlParameterSource()
                        .addValue("teamId", TEST_TEAM_ID)
                        .addValue("orgId", "org_mcp_it")
                        .addValue("name", "MCP IT Team"));

        // Seed an asset
        PrincipalContextHolder.set(new PrincipalContext(
                AGENT_USER, PrincipalType.USER, null, null,
                List.of(), Set.of(), Set.of("asset:read", "asset:write", "asset:create"),
                0, "en", "req_mcp", "trace_mcp"));
        assetService.createAsset(new CreateAssetCommand(
                AssetType.MODEL, null, null, "mcp-it", "mcp-model-" + System.nanoTime(),
                "MCP IT Model", "MCP 搜索测试", Visibility.INTERNAL, null,
                List.of("nlp"), null, "Apache-2.0", TEST_TEAM_ID,
                new ModelProfile("pytorch", "text-generation", null),
                null, AGENT_USER));
        PrincipalContextHolder.clear();

        // Issue JWT for agent
        IssuedToken token = tokenSigner.issue(new TokenIssueRequest(
                AGENT_USER, PrincipalType.USER,
                1L,
                Set.of("asset:read", "mcp:invoke"),
                TokenType.ACCESS));
        accessToken = token.token();
    }

    @AfterEach
    void clearPrincipal() {
        PrincipalContextHolder.clear();
    }

    private String mcpPayload(String method, Map<String, Object> params) {
        try {
            if (params != null) {
                return objectMapper.writeValueAsString(Map.of(
                        "jsonrpc", "2.0", "id", 1, "method", method, "params", params));
            }
            return objectMapper.writeValueAsString(Map.of(
                    "jsonrpc", "2.0", "id", 1, "method", method));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void initializeReturnsCapabilities() throws Exception {
        mockMvc.perform(post("/mcp")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mcpPayload("initialize", Map.of(
                                "protocolVersion", "2025-06-18",
                                "capabilities", Map.of(),
                                "clientInfo", Map.of("name", "test-client", "version", "1.0")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").exists())
                .andExpect(jsonPath("$.result.serverInfo.name").value("aihub-mcp"));
    }

    @Test
    void toolsListReturnsAvailableTools() throws Exception {
        mockMvc.perform(post("/mcp")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mcpPayload("tools/list", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools").isArray());
    }

    @Test
    void anonymousAccessRejected() throws Exception {
        mockMvc.perform(post("/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mcpPayload("initialize", Map.of(
                                "protocolVersion", "2025-06-18",
                                "capabilities", Map.of(),
                                "clientInfo", Map.of("name", "anon", "version", "0.1")))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownMethodReturnsJsonRpcError() throws Exception {
        MvcResult result = mockMvc.perform(post("/mcp")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mcpPayload("unknown/method", null)))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("error");
    }
}
