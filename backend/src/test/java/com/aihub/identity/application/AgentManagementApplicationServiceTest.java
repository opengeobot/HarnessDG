/*
 * 功能: AgentManagementApplicationService 单元测试——Agent 注册/列表/启停/Tool 白名单替换。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.identity.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.identity.application.IdentityCommands.CreateAgentCommand;
import com.aihub.identity.domain.AgentIdentity;
import com.aihub.identity.domain.AgentIdentityRepository;
import com.aihub.identity.domain.AgentStatus;
import com.aihub.identity.domain.PrincipalAccount;
import com.aihub.identity.domain.RefreshTokenRepository;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.shared.security.PasswordHasher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * AgentManagementApplicationService 单元测试。
 *
 * <p>覆盖 Agent 注册（含凭据一次性返回）、列表、启用/禁用（含 Token 吊销）、Tool 白名单替换。
 */
class AgentManagementApplicationServiceTest {

    private AgentIdentityRepository agentRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private PasswordHasher passwordHasher;
    private IdGenerator idGenerator;
    private Clock clock;
    private AgentManagementApplicationService service;

    private static final Instant NOW = Instant.parse("2026-07-11T10:00:00Z");

    @BeforeEach
    void setUp() {
        agentRepository = mock(AgentIdentityRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        passwordHasher = mock(PasswordHasher.class);
        idGenerator = mock(IdGenerator.class);
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new AgentManagementApplicationService(
                agentRepository, refreshTokenRepository, passwordHasher, idGenerator, clock);
    }

    @Test
    void registerAgentSuccess() {
        when(idGenerator.generate(IdPrefix.PRINCIPAL)).thenReturn("prn_agent1");
        when(idGenerator.generate(IdPrefix.AGENT)).thenReturn("agt_001");
        when(passwordHasher.hash(anyString())).thenReturn("hashed_cred");

        CreateAgentCommand cmd = new CreateAgentCommand(
                "Test Agent", "MCP", "TestCorp", 3, Set.of("asset.read"));

        CreatedAgentResult result = service.registerAgent(cmd);

        assertNotNull(result.credential());
        assertEquals("agt_001", result.agent().agentId());
        assertEquals("prn_agent1", result.agent().principalId());
        assertEquals("Test Agent", result.agent().displayName());
        verify(agentRepository).create(any(PrincipalAccount.class), any(AgentIdentity.class));
    }

    @Test
    void registerAgentRejectsBlankDisplayName() {
        CreateAgentCommand cmd = new CreateAgentCommand("", "MCP", "TestCorp", 3, Set.of());

        assertThrows(ValidationException.class, () -> service.registerAgent(cmd));
        verify(agentRepository, never()).create(any(), any());
    }

    @Test
    void registerAgentRejectsBlankAgentType() {
        CreateAgentCommand cmd = new CreateAgentCommand("Agent", "", "TestCorp", 3, Set.of());

        assertThrows(ValidationException.class, () -> service.registerAgent(cmd));
    }

    @Test
    void listAgentsDelegatesToRepository() {
        AgentIdentity agent = buildTestAgent("agt_1", "prn_1", "Agent1", AgentStatus.ACTIVE);
        when(agentRepository.findAll()).thenReturn(List.of(agent));

        List<AgentView> result = service.listAgents();

        assertEquals(1, result.size());
        assertEquals("Agent1", result.get(0).displayName());
    }

    @Test
    void enableAgentSuccess() {
        AgentIdentity agent = buildTestAgent("agt_1", "prn_1", "Agent1", AgentStatus.DISABLED);
        when(agentRepository.findByAgentId("agt_1")).thenReturn(Optional.of(agent));
        when(agentRepository.update(any(AgentIdentity.class))).thenReturn(true);

        service.enableAgent("agt_1");

        verify(agentRepository).update(any(AgentIdentity.class));
    }

    @Test
    void disableAgentRevokesTokens() {
        AgentIdentity agent = buildTestAgent("agt_1", "prn_1", "Agent1", AgentStatus.ACTIVE);
        when(agentRepository.findByAgentId("agt_1")).thenReturn(Optional.of(agent));
        when(agentRepository.update(any(AgentIdentity.class))).thenReturn(true);

        service.disableAgent("agt_1");

        verify(agentRepository).update(any(AgentIdentity.class));
        verify(refreshTokenRepository).revokeAllByPrincipal(eq("prn_1"));
    }

    @Test
    void disableAgentThrowsNotFoundWhenAbsent() {
        when(agentRepository.findByAgentId("agt_missing")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.disableAgent("agt_missing"));
        verify(refreshTokenRepository, never()).revokeAllByPrincipal(anyString());
    }

    @Test
    void updateToolAllowlistSuccess() {
        AgentIdentity agent = buildTestAgent("agt_1", "prn_1", "Agent1", AgentStatus.ACTIVE);
        when(agentRepository.findByAgentId("agt_1")).thenReturn(Optional.of(agent));
        when(agentRepository.update(any(AgentIdentity.class))).thenReturn(true);

        AgentView result = service.updateToolAllowlist("agt_1", List.of("asset_search", "asset_get"));

        assertEquals(List.of("asset_search", "asset_get"), result.toolAllowlist());
        verify(agentRepository).update(any(AgentIdentity.class));
    }

    @Test
    void updateToolAllowlistThrowsNotFoundWhenAbsent() {
        when(agentRepository.findByAgentId("agt_missing")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.updateToolAllowlist("agt_missing", List.of()));
    }

    private AgentIdentity buildTestAgent(String agentId, String principalId, String displayName, AgentStatus status) {
        return new AgentIdentity.Builder()
                .agentId(agentId)
                .principalId(principalId)
                .displayName(displayName)
                .agentType("MCP")
                .vendor("Test")
                .credentialHash("hashed")
                .maxSensitivityLevel(3)
                .scopes(Set.of("asset.read"))
                .toolAllowlist(List.of())
                .tokenVersion(0L)
                .createdAt(NOW)
                .updatedAt(NOW)
                .rowVersion(0L)
                .build();
    }
}
