/*
 * 功能: Token 吊销检查器实现，依据主体 token_version 与状态判断访问令牌是否已被吊销。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.infrastructure;

import com.aihub.identity.domain.AgentIdentityRepository;
import com.aihub.identity.domain.LocalUserRepository;
import com.aihub.identity.domain.PatRepository;
import com.aihub.identity.domain.PersonalAccessToken;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.JwtClaims;
import com.aihub.shared.security.TokenRevocationChecker;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Token 吊销检查器实现。
 *
 * <p>身份表就绪后实现主体级吊销：依据 {@link JwtClaims#principalId()} 查用户/Agent，
 * 当声明中的 {@code tokenVersion} 落后于当前主体 {@code tokenVersion}（改密/禁用/重置触发递增）、
 * 或主体已被禁用时，判定该 Token 已被吊销并拒绝访问。
 *
 * <p>对 PAT（存于 iam_token.token_type=PAT）额外按 jti 检查单令牌吊销状态。
 */
@Component
public class IdentityTokenRevocationChecker implements TokenRevocationChecker {

    private final LocalUserRepository userRepository;
    private final AgentIdentityRepository agentRepository;
    private final PatRepository patRepository;
    private final Clock clock;

    public IdentityTokenRevocationChecker(LocalUserRepository userRepository,
                                          AgentIdentityRepository agentRepository,
                                          PatRepository patRepository,
                                          Clock clock) {
        this.userRepository = userRepository;
        this.agentRepository = agentRepository;
        this.patRepository = patRepository;
        this.clock = clock;
    }

    @Override
    public boolean isRevoked(JwtClaims claims) {
        if (isPatRevoked(claims)) {
            return true;
        }
        if (claims.principalType() == PrincipalType.USER) {
            return userRepository.findByPrincipalId(claims.principalId())
                    .map(user -> user.isDisabled() || claims.tokenVersion() < user.tokenVersion())
                    .orElse(true);
        }
        if (claims.principalType() == PrincipalType.AGENT) {
            return agentRepository.findByPrincipalId(claims.principalId())
                    .map(agent -> agent.isDisabled() || claims.tokenVersion() < agent.tokenVersion())
                    .orElse(true);
        }
        return true;
    }

    private boolean isPatRevoked(JwtClaims claims) {
        return patRepository.findByJti(claims.jwtId())
                .map(pat -> !pat.isActive(clock.instant()))
                .orElse(false);
    }
}
