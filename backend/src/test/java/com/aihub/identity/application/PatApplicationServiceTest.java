/*
 * 功能: PAT 应用服务单元测试。
 * 时间: 2026-07-11
 * 作者: AxeXie
 */
package com.aihub.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.authorization.application.AuthorizationService;
import com.aihub.authorization.application.EffectiveScopeResolver;
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
import com.aihub.shared.security.JwtClaims;
import com.aihub.shared.security.TokenSigner;
import com.aihub.shared.security.TokenType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PatApplicationServiceTest {

    @Mock private PatRepository patRepository;
    @Mock private LocalUserRepository userRepository;
    @Mock private TokenSigner tokenSigner;
    @Mock private AuthorizationService authorizationService;
    @Mock private EffectiveScopeResolver effectiveScopeResolver;
    @Mock private IdGenerator idGenerator;

    private PatApplicationService service;
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new PatApplicationService(
                patRepository, userRepository, tokenSigner, authorizationService,
                effectiveScopeResolver, idGenerator,
                new JwtProperties(null, null, null, null, null, null,
                        Duration.ofMinutes(15), Duration.ofDays(7), Duration.ofDays(90)),
                clock);
        PrincipalContextHolder.set(new PrincipalContext(
                "prn_user1", PrincipalType.USER, "prn_user1", "User One",
                List.of(), Set.of(), Set.of("asset:read"), 0, "zh-CN", "req_1", "trace_1"));
    }

    @AfterEach
    void tearDown() {
        PrincipalContextHolder.clear();
    }

    @Test
    void createPatReturnsTokenOnce() {
        LocalUser user = new LocalUser.Builder()
                .userId("usr_1")
                .principalId("prn_user1")
                .username("user1")
                .passwordHash("hash")
                .scopes(Set.of("asset:read"))
                .tokenVersion(1L)
                .build();
        when(userRepository.findByPrincipalId("prn_user1")).thenReturn(java.util.Optional.of(user));
        when(effectiveScopeResolver.resolve("prn_user1", user.scopes()))
                .thenReturn(Set.of("asset:read", "asset:manage"));
        when(idGenerator.generate(IdPrefix.TOKEN)).thenReturn("tok_pat1");
        JwtClaims claims = new JwtClaims("iss", "aud", "prn_user1", PrincipalType.USER,
                "jti_pat1", 1L, Set.of("asset:read"), clock.instant(),
                clock.instant().plus(Duration.ofDays(90)), TokenType.ACCESS);
        when(tokenSigner.issueWithTtl(any(), eq(Duration.ofDays(90))))
                .thenReturn(new IssuedToken("raw-token-value", claims));

        CreatedPatView view = service.createPat("CI token", Set.of("asset:read"), null);

        assertThat(view.token()).isEqualTo("raw-token-value");
        assertThat(view.tokenId()).isEqualTo("tok_pat1");
        ArgumentCaptor<PersonalAccessToken> captor = ArgumentCaptor.forClass(PersonalAccessToken.class);
        verify(patRepository).save(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(PatStatus.ACTIVE);
        verify(authorizationService).requirePermission("token:create");
    }

    @Test
    void createPatRejectsUnauthorizedScope() {
        LocalUser user = new LocalUser.Builder()
                .userId("usr_1")
                .principalId("prn_user1")
                .username("user1")
                .passwordHash("hash")
                .scopes(Set.of("asset:read"))
                .tokenVersion(1L)
                .build();
        when(userRepository.findByPrincipalId("prn_user1")).thenReturn(java.util.Optional.of(user));
        when(effectiveScopeResolver.resolve("prn_user1", user.scopes()))
                .thenReturn(Set.of("asset:read"));

        assertThatThrownBy(() -> service.createPat("bad", Set.of("admin:all"), null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void revokePatThrowsWhenNotFound() {
        when(patRepository.revoke("tok_missing", "prn_user1")).thenReturn(0);

        assertThatThrownBy(() -> service.revokePat("tok_missing"))
                .isInstanceOf(NotFoundException.class);
    }
}
