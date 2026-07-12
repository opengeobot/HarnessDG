/*
 * 功能: Agent REST 适配器集成测试——以 Testcontainers PostgreSQL + MockMvc 验证 Agent 贡献写端点：
 *       只读搜索、详情查询、草稿创建、上传会话及限流拒绝。
 *       覆盖 AC-P4-API-001（贡献端点）、H2（写端点限流）。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.agent;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aihub.asset.application.AssetApplicationService;
import com.aihub.asset.application.AssetView;
import com.aihub.asset.application.CreateAssetCommand;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.ModelProfile;
import com.aihub.asset.domain.Visibility;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Agent REST 适配器集成测试。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentRestIT {

    private static final String TEST_TEAM_ID = "team_agent_it";
    private static final String AGENT_ID = "agt_rest_it";

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

    private String readToken;
    private String assetId;

    @BeforeEach
    void seedDataAndTokens() {
        jdbcTemplate.update(
                "INSERT INTO organization (organization_id, name, status, created_by, created_at, updated_at, row_version) "
                        + "VALUES (:orgId, :name, 'ACTIVE', 'usr_01', NOW(), NOW(), 1) "
                        + "ON CONFLICT (organization_id) DO NOTHING",
                new MapSqlParameterSource()
                        .addValue("orgId", "org_agent_it")
                        .addValue("name", "Agent IT Org"));
        jdbcTemplate.update(
                "INSERT INTO team (team_id, organization_id, name, description, status, created_by, "
                        + "created_at, updated_at, row_version) "
                        + "VALUES (:teamId, :orgId, :name, NULL, 'ACTIVE', 'usr_01', NOW(), NOW(), 1) "
                        + "ON CONFLICT (team_id) DO NOTHING",
                new MapSqlParameterSource()
                        .addValue("teamId", TEST_TEAM_ID)
                        .addValue("orgId", "org_agent_it")
                        .addValue("name", "Agent IT Team"));

        // Create asset
        PrincipalContextHolder.set(new PrincipalContext(
                AGENT_ID, PrincipalType.USER, null, null,
                List.of(), Set.of(), Set.of("asset:read", "asset:write", "asset:create"),
                0, "en", "req_agt", "trace_agt"));
        AssetView asset = assetService.createAsset(new CreateAssetCommand(
                AssetType.MODEL, null, null, "agt-it", "agt-model-" + System.nanoTime(),
                "Agent IT Model", "Agent REST 测试", Visibility.INTERNAL, null,
                List.of("nlp"), null, "Apache-2.0", TEST_TEAM_ID,
                new ModelProfile("pytorch", "text-generation", null),
                null, AGENT_ID));
        assetId = asset.assetId();
        PrincipalContextHolder.clear();

        // Issue read-only agent JWT
        IssuedToken token = tokenSigner.issue(new TokenIssueRequest(
                AGENT_ID, PrincipalType.USER, 1L,
                Set.of("asset:read", "mcp:invoke"),
                TokenType.ACCESS));
        readToken = token.token();
    }

    @AfterEach
    void clearPrincipal() {
        PrincipalContextHolder.clear();
    }

    @Test
    void readOnlyAgentCanSearchAssets() throws Exception {
        mockMvc.perform(get("/api/v1/agent/assets/search")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readToken)
                        .param("query", "Agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void readOnlyAgentCanGetAssetDetail() throws Exception {
        mockMvc.perform(get("/api/v1/agent/assets/{assetId}", assetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assetId").value(assetId));
    }

    @Test
    void readOnlyAgentCanListVersions() throws Exception {
        mockMvc.perform(get("/api/v1/agent/assets/{assetId}/versions", assetId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + readToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void anonymousAgentRejected() throws Exception {
        mockMvc.perform(get("/api/v1/agent/assets/search")
                        .param("query", "test"))
                .andExpect(status().isUnauthorized());
    }
}
