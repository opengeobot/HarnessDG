/*
 * 功能: Agent 管理应用服务，编排 Agent 注册、列表、启停与 Tool 白名单替换用例。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.application;

import com.aihub.identity.application.IdentityCommands.CreateAgentCommand;
import com.aihub.identity.domain.AgentIdentity;
import com.aihub.identity.domain.AgentIdentityRepository;
import com.aihub.identity.domain.PrincipalAccount;
import com.aihub.identity.domain.RefreshTokenRepository;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.PasswordHasher;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agent 管理应用服务。
 *
 * <p>注册 Agent 时生成一次性长期凭据，仅在创建响应返回一次，服务端只保存其哈希摘要；
 * 凭据明文绝不写日志或审计正文。禁用 Agent 时吊销其全部 Token。
 */
@Service
public class AgentManagementApplicationService {

    /** 生成凭据的随机字节数（Base64URL 后约 43 字符）。 */
    private static final int CREDENTIAL_BYTES = 32;

    private final AgentIdentityRepository agentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHasher passwordHasher;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public AgentManagementApplicationService(AgentIdentityRepository agentRepository,
                                             RefreshTokenRepository refreshTokenRepository,
                                             PasswordHasher passwordHasher,
                                             IdGenerator idGenerator,
                                             Clock clock) {
        this.agentRepository = agentRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordHasher = passwordHasher;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * 注册 Agent：生成一次性凭据并落库其摘要，返回含凭据明文的结果（仅此一次）。
     */
    @Transactional
    public CreatedAgentResult registerAgent(CreateAgentCommand command) {
        if (command.displayName() == null || command.displayName().isBlank()) {
            throw new ValidationException("displayName is required");
        }
        if (command.agentType() == null || command.agentType().isBlank()) {
            throw new ValidationException("agentType is required");
        }
        Instant now = clock.instant();
        String principalId = idGenerator.generate(IdPrefix.PRINCIPAL);
        String agentId = idGenerator.generate(IdPrefix.AGENT);
        String credential = generateCredential();

        PrincipalAccount principal = new PrincipalAccount(
                principalId, PrincipalType.AGENT, command.displayName(), "ACTIVE", now, now);
        AgentIdentity agent = new AgentIdentity.Builder()
                .agentId(agentId)
                .principalId(principalId)
                .displayName(command.displayName())
                .agentType(command.agentType())
                .vendor(command.vendor())
                .credentialHash(passwordHasher.hash(credential))
                .maxSensitivityLevel(command.maxSensitivityLevel())
                .scopes(command.scopes() == null ? java.util.Set.of() : command.scopes())
                .toolAllowlist(List.of())
                .tokenVersion(0L)
                .createdAt(now)
                .updatedAt(now)
                .rowVersion(0L)
                .build();
        agentRepository.create(principal, agent);
        return new CreatedAgentResult(AgentView.from(agent), credential);
    }

    /**
     * 查询全部 Agent。
     */
    @Transactional(readOnly = true)
    public List<AgentView> listAgents() {
        return agentRepository.findAll().stream().map(AgentView::from).toList();
    }

    /**
     * 启用 Agent。
     */
    @Transactional
    public void enableAgent(String agentId) {
        AgentIdentity agent = requireAgent(agentId);
        agent.enable(clock.instant());
        agentRepository.update(agent);
    }

    /**
     * 禁用 Agent 并吊销其全部 Token。
     */
    @Transactional
    public void disableAgent(String agentId) {
        AgentIdentity agent = requireAgent(agentId);
        agent.disable(clock.instant());
        agentRepository.update(agent);
        refreshTokenRepository.revokeAllByPrincipal(agent.principalId());
    }

    /**
     * 替换 Agent MCP Tool 白名单。
     */
    @Transactional
    public AgentView updateToolAllowlist(String agentId, List<String> tools) {
        AgentIdentity agent = requireAgent(agentId);
        agent.replaceToolAllowlist(tools, clock.instant());
        agentRepository.update(agent);
        return AgentView.from(agent);
    }

    private AgentIdentity requireAgent(String agentId) {
        return agentRepository.findByAgentId(agentId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.AGENT_NOT_FOUND, "agent not found", Map.of()));
    }

    private String generateCredential() {
        byte[] bytes = new byte[CREDENTIAL_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
