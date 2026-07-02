/*
 * 功能: Token 吊销检查器实现，依据主体 token_version 与状态判断访问令牌是否已被吊销。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.infrastructure;

import com.aihub.identity.domain.AgentIdentityRepository;
import com.aihub.identity.domain.LocalUserRepository;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.JwtClaims;
import com.aihub.shared.security.TokenRevocationChecker;
import org.springframework.stereotype.Component;

/**
 * Token 吊销检查器实现。
 *
 * <p>身份表就绪后实现主体级吊销：依据 {@link JwtClaims#principalId()} 查用户/Agent，
 * 当声明中的 {@code tokenVersion} 落后于当前主体 {@code tokenVersion}（改密/禁用/重置触发递增）、
 * 或主体已被禁用时，判定该 Token 已被吊销并拒绝访问。
 *
 * <p>性能：按 principalId 主键等值查询，避免全表扫描；P0-B 可接受每请求一次主键查询。
 */
@Component
public class IdentityTokenRevocationChecker implements TokenRevocationChecker {

    private final LocalUserRepository userRepository;
    private final AgentIdentityRepository agentRepository;

    public IdentityTokenRevocationChecker(LocalUserRepository userRepository,
                                          AgentIdentityRepository agentRepository) {
        this.userRepository = userRepository;
        this.agentRepository = agentRepository;
    }

    @Override
    public boolean isRevoked(JwtClaims claims) {
        if (claims.principalType() == PrincipalType.USER) {
            return userRepository.findByPrincipalId(claims.principalId())
                    .map(user -> user.isDisabled() || claims.tokenVersion() < user.tokenVersion())
                    // 主体不存在视为已吊销（fail-closed）。
                    .orElse(true);
        }
        if (claims.principalType() == PrincipalType.AGENT) {
            return agentRepository.findByPrincipalId(claims.principalId())
                    .map(agent -> agent.isDisabled() || claims.tokenVersion() < agent.tokenVersion())
                    .orElse(true);
        }
        // 其他主体类型（SERVICE/API_CLIENT/WORKER）暂无本地记录：fail-closed 拒绝。
        return true;
    }
}
