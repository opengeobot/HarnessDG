/*
 * 功能: PAT 应用服务——创建、列出与吊销个人访问令牌。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.identity.application;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.application.EffectiveScopeResolver;
import com.aihub.authorization.domain.Permissions;
import com.aihub.identity.domain.LocalUser;
import com.aihub.identity.domain.LocalUserRepository;
import com.aihub.identity.domain.PatRepository;
import com.aihub.identity.domain.PatStatus;
import com.aihub.identity.domain.PersonalAccessToken;
import com.aihub.platform.security.JwtProperties;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.shared.identity.PrincipalContext;
import com.aihub.shared.identity.PrincipalContextHolder;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.IssuedToken;
import com.aihub.shared.security.TokenIssueRequest;
import com.aihub.shared.security.TokenSigner;
import com.aihub.shared.security.TokenType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PAT 应用服务。
 */
@Service
public class PatApplicationService {

    private static final Duration MAX_PAT_TTL = Duration.ofDays(365);
    private static final Duration MIN_PAT_TTL = Duration.ofDays(1);

    private final PatRepository patRepository;
    private final LocalUserRepository userRepository;
    private final TokenSigner tokenSigner;
    private final AuthorizationService authorizationService;
    private final EffectiveScopeResolver effectiveScopeResolver;
    private final IdGenerator idGenerator;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public PatApplicationService(PatRepository patRepository,
                                 LocalUserRepository userRepository,
                                 TokenSigner tokenSigner,
                                 AuthorizationService authorizationService,
                                 EffectiveScopeResolver effectiveScopeResolver,
                                 IdGenerator idGenerator,
                                 JwtProperties jwtProperties,
                                 Clock clock) {
        this.patRepository = patRepository;
        this.userRepository = userRepository;
        this.tokenSigner = tokenSigner;
        this.authorizationService = authorizationService;
        this.effectiveScopeResolver = effectiveScopeResolver;
        this.idGenerator = idGenerator;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    /** 为当前用户创建 PAT，完整 Token 仅返回一次。 */
    @Transactional
    public CreatedPatView createPat(String name, Set<String> requestedScopes, Integer ttlDays) {
        authorizationService.requirePermission(Permissions.TOKEN_CREATE);
        PrincipalContext principal = PrincipalContextHolder.require();
        if (principal.principalType() != PrincipalType.USER) {
            throw new ValidationException("PAT can only be created for USER principals");
        }
        if (name == null || name.isBlank()) {
            throw new ValidationException("name is required");
        }

        LocalUser user = userRepository.findByPrincipalId(principal.principalId())
                .orElseThrow(() -> new NotFoundException("user not found"));
        if (user.isDisabled()) {
            throw new ValidationException("user is disabled");
        }

        Set<String> effectiveScopes = effectiveScopeResolver.resolve(
                principal.principalId(), user.scopes());
        Set<String> patScopes = resolvePatScopes(requestedScopes, effectiveScopes);

        Duration ttl = resolveTtl(ttlDays);
        Instant now = clock.instant();
        String tokenId = idGenerator.generate(IdPrefix.TOKEN);

        TokenIssueRequest issueRequest = new TokenIssueRequest(
                principal.principalId(), PrincipalType.USER, user.tokenVersion(),
                patScopes, TokenType.ACCESS);
        IssuedToken issued = tokenSigner.issueWithTtl(issueRequest, ttl);

        PersonalAccessToken pat = new PersonalAccessToken(
                tokenId, issued.claims().jwtId(), principal.principalId(), name.trim(),
                patScopes, PatStatus.ACTIVE, now, issued.claims().expiresAt(), null);
        patRepository.save(pat);

        return CreatedPatView.from(pat, issued.token());
    }

    /** 列出当前用户的 PAT。 */
    @Transactional(readOnly = true)
    public List<PatView> listOwnPats() {
        authorizationService.requirePermission(Permissions.TOKEN_CREATE);
        String principalId = PrincipalContextHolder.require().principalId();
        return PatView.fromList(patRepository.listByPrincipalId(principalId));
    }

    /** 吊销当前用户的 PAT。 */
    @Transactional
    public void revokePat(String tokenId) {
        authorizationService.requirePermission(Permissions.TOKEN_CREATE);
        String principalId = PrincipalContextHolder.require().principalId();
        int updated = patRepository.revoke(tokenId, principalId);
        if (updated == 0) {
            throw new NotFoundException("PAT not found or already revoked");
        }
    }

    private Set<String> resolvePatScopes(Set<String> requested, Set<String> effective) {
        Set<String> scopes = requested == null || requested.isEmpty()
                ? effective
                : new LinkedHashSet<>(requested);
        for (String scope : scopes) {
            if (!effective.contains(scope)) {
                throw new ValidationException("requested scope not granted: " + scope);
            }
        }
        return Set.copyOf(scopes);
    }

    private Duration resolveTtl(Integer ttlDays) {
        if (ttlDays == null) {
            return jwtProperties.patTokenTtl();
        }
        if (ttlDays < MIN_PAT_TTL.toDays() || ttlDays > MAX_PAT_TTL.toDays()) {
            throw new ValidationException("ttlDays must be between 1 and 365");
        }
        return Duration.ofDays(ttlDays);
    }
}
