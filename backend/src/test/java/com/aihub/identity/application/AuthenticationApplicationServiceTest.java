/*
 * 功能: AuthenticationApplicationService 审计埋点单元测试——验证登录/刷新/改密等事件正确记录且不含敏感明文。
 * 时间: 2026-07-02
 * 作者: AxeXie
 */
package com.aihub.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aihub.audit.domain.AuditResult;
import com.aihub.identity.domain.AgentIdentityRepository;
import com.aihub.identity.domain.LocalUser;
import com.aihub.identity.domain.LocalUserRepository;
import com.aihub.identity.domain.RefreshTokenRecord;
import com.aihub.identity.domain.RefreshTokenRepository;
import com.aihub.shared.error.AuthenticationException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.identity.PrincipalType;
import com.aihub.shared.security.IssuedToken;
import com.aihub.shared.security.JwtClaims;
import com.aihub.shared.security.PasswordHasher;
import com.aihub.shared.security.PasswordPolicy;
import com.aihub.shared.security.TokenSigner;
import com.aihub.shared.security.TokenType;
import com.aihub.shared.security.TokenVerifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class AuthenticationApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-02T00:00:00Z");

    @Mock
    private LocalUserRepository userRepository;
    @Mock
    private AgentIdentityRepository agentRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private TokenSigner tokenSigner;
    @Mock
    private TokenVerifier tokenVerifier;
    @Mock
    private PasswordHasher passwordHasher;
    @Mock
    private PasswordPolicy passwordPolicy;
    @Mock
    private IdGenerator idGenerator;
    @Mock
    private AuditPort auditPort;
    @Mock
    private PlatformTransactionManager transactionManager;

    private AuthenticationApplicationService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        // 失败/拒绝副作用走独立事务：mock 的事务模板须直接执行回调，
        // 使单元测试仍能观察到「事务内做了什么」。
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new AuthenticationApplicationService(userRepository, agentRepository, refreshTokenRepository,
                tokenSigner, tokenVerifier, passwordHasher, passwordPolicy, idGenerator, clock, auditPort,
                transactionManager);
    }

    private LocalUser activeUser() {
        return new LocalUser.Builder()
                .userId("usr_1").principalId("prn_1").username("alice")
                .displayName("Alice").passwordHash("$hash").scopes(Set.of("asset:read"))
                .build();
    }

    private void stubTokenIssuance() {
        JwtClaims claims = new JwtClaims("iss", "aud", "prn_1", PrincipalType.USER, "jti_1",
                0L, Set.of("asset:read"), NOW, NOW.plusSeconds(900), TokenType.ACCESS);
        lenient().when(tokenSigner.issue(any())).thenReturn(new IssuedToken("token", claims));
        lenient().when(idGenerator.generate(any(com.aihub.shared.id.IdPrefix.class))).thenReturn("tkf_1");
    }

    @Test
    void loginSuccessRecordsSucceededEvent() {
        LocalUser user = activeUser();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("pw", "$hash")).thenReturn(true);
        stubTokenIssuance();

        service.login("alice", "pw");

        ArgumentCaptor<Map<String, Object>> attrs = ArgumentCaptor.forClass(Map.class);
        verify(auditPort).record(eq("AUTH_LOGIN_SUCCEEDED"), eq(AuditResult.SUCCEEDED),
                eq("prn_1"), eq("prn_1"), eq(null), attrs.capture());
        assertNoSensitive(attrs.getValue());
    }

    @Test
    void loginWrongPasswordRecordsFailedEventWithoutPassword() {
        LocalUser user = activeUser();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("bad", "$hash")).thenReturn(false);
        // 失败计数走独立事务的原子递增，返回更新后的用户。
        when(userRepository.recordLoginFailureAtomically(eq("usr_1"), any()))
                .thenReturn(Optional.of(activeUser()));

        assertThatThrownBy(() -> service.login("alice", "bad"))
                .isInstanceOf(AuthenticationException.class);

        verify(userRepository).recordLoginFailureAtomically(eq("usr_1"), any());

        ArgumentCaptor<Map<String, Object>> attrs = ArgumentCaptor.forClass(Map.class);
        verify(auditPort).record(eq("AUTH_LOGIN_FAILED"), eq(AuditResult.FAILED),
                eq("prn_1"), eq("prn_1"), eq("AUTH_INVALID_CREDENTIALS"), attrs.capture());
        assertNoSensitive(attrs.getValue());
    }

    @Test
    void loginFailureReachingThresholdReportsLocked() {
        LocalUser user = activeUser();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("bad", "$hash")).thenReturn(false);
        // 已达阈值：仓储回读的用户处于锁定中。
        LocalUser lockedUser = activeUser();
        for (int i = 0; i < LocalUser.MAX_FAILED_ATTEMPTS; i++) {
            lockedUser.recordLoginFailure(NOW);
        }
        when(userRepository.recordLoginFailureAtomically(eq("usr_1"), any()))
                .thenReturn(Optional.of(lockedUser));

        assertThatThrownBy(() -> service.login("alice", "bad"))
                .isInstanceOfSatisfying(AuthenticationException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_ACCOUNT_LOCKED));

        // 计入锁定必须记为 DENIED 且带 AUTH_ACCOUNT_LOCKED 错误码。
        verify(auditPort).record(eq("AUTH_LOGIN_FAILED"), eq(AuditResult.DENIED),
                eq("prn_1"), eq("prn_1"), eq("AUTH_ACCOUNT_LOCKED"), any());
    }

    @Test
    void loginUnknownUserRecordsFailedEvent() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("ghost", "pw"))
                .isInstanceOf(AuthenticationException.class);

        verify(auditPort).record(eq("AUTH_LOGIN_FAILED"), eq(AuditResult.FAILED),
                eq(null), eq(null), eq("AUTH_INVALID_CREDENTIALS"), any());
    }

    @Test
    void loginDisabledUserRecordsDeniedEvent() {
        LocalUser user = activeUser();
        user.disable(NOW);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login("alice", "pw"))
                .isInstanceOf(AuthenticationException.class);

        verify(auditPort).record(eq("AUTH_LOGIN_FAILED"), eq(AuditResult.DENIED),
                eq("prn_1"), eq("prn_1"), eq("AUTH_ACCOUNT_DISABLED"), any());
    }

    @Test
    void refreshRotationRecordsSucceededEventAndMarksOldJtiRotated() {
        JwtClaims refreshClaims = new JwtClaims("iss", "aud", "prn_1", PrincipalType.USER, "jti_1",
                0L, Set.of("asset:read"), NOW, NOW.plusSeconds(3600), TokenType.REFRESH);
        when(tokenVerifier.verify("rt")).thenReturn(refreshClaims);
        // markRotated 需要解析新签发的 refresh 以取其后继 jti。
        lenient().when(tokenVerifier.verify("token")).thenReturn(refreshClaims);
        when(refreshTokenRepository.findByJti("jti_1")).thenReturn(Optional.of(new RefreshTokenRecord.Builder()
                .tokenId("tk_1").jti("jti_1").tokenFamily("tkf_1").principalId("prn_1")
                .principalType(PrincipalType.USER).scopes(Set.of("asset:read"))
                .issuedAt(NOW).expiresAt(NOW.plusSeconds(3600)).build()));
        // 原子 CAS 抢占成功 → 本请求为唯一胜者。
        when(refreshTokenRepository.claimForRotation("jti_1")).thenReturn(true);
        when(userRepository.findByPrincipalId("prn_1")).thenReturn(Optional.of(activeUser()));
        stubTokenIssuance();

        TokenPairResult result = service.refresh("rt");

        assertThat(result.accessToken()).isNotNull();
        // 正常路径不得触发整族吊销——否则合法刷新会把用户自己踢下线。
        verify(refreshTokenRepository, never()).revokeFamily(anyString());
        verify(refreshTokenRepository).markRotated("jti_1", "jti_1");
        verify(auditPort).record(eq("AUTH_TOKEN_REFRESHED"), eq(AuditResult.SUCCEEDED),
                eq("prn_1"), eq("tkf_1"), eq(null), any());
    }

    @Test
    void refreshReplayRecordsDeniedEvent() {
        JwtClaims refreshClaims = new JwtClaims("iss", "aud", "prn_1", PrincipalType.USER, "jti_old",
                0L, Set.of(), NOW, NOW.plusSeconds(3600), TokenType.REFRESH);
        when(tokenVerifier.verify("rt")).thenReturn(refreshClaims);
        RefreshTokenRecord rotated = new RefreshTokenRecord.Builder()
                .tokenId("tk_1").jti("jti_old").tokenFamily("tkf_1").principalId("prn_1")
                .principalType(PrincipalType.USER).scopes(Set.of())
                .issuedAt(NOW).expiresAt(NOW.plusSeconds(3600))
                .status(com.aihub.identity.domain.RefreshTokenStatus.ROTATED)
                .build();
        when(refreshTokenRepository.findByJti("jti_old")).thenReturn(Optional.of(rotated));
        // 原子 CAS 抢占失败 → 该 jti 已被消费，按重放处理。
        when(refreshTokenRepository.claimForRotation("jti_old")).thenReturn(false);

        assertThatThrownBy(() -> service.refresh("rt"))
                .isInstanceOfSatisfying(AuthenticationException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.AUTH_REFRESH_REPLAYED));

        // 整族吊销与拒绝审计必须发生（且位于独立事务，不随异常回滚）。
        verify(refreshTokenRepository).revokeFamily("tkf_1");
        verify(auditPort).record(eq("AUTH_TOKEN_REPLAY_REJECTED"), eq(AuditResult.DENIED),
                eq("prn_1"), eq("tkf_1"), eq("AUTH_REFRESH_REPLAYED"), any());
    }

    @Test
    void changePasswordSuccessRecordsEvent() {
        LocalUser user = activeUser();
        when(userRepository.findByPrincipalId("prn_1")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("old", "$hash")).thenReturn(true);
        when(passwordPolicy.validate("newPass123!")).thenReturn(java.util.List.of());
        when(passwordHasher.hash("newPass123!")).thenReturn("$newhash");

        service.changePassword("prn_1", "old", "newPass123!");

        verify(refreshTokenRepository).revokeAllByPrincipal("prn_1");
        ArgumentCaptor<Map<String, Object>> attrs = ArgumentCaptor.forClass(Map.class);
        verify(auditPort).record(eq("USER_PASSWORD_CHANGED"), eq(AuditResult.SUCCEEDED),
                eq("prn_1"), eq("prn_1"), eq(null), attrs.capture());
        assertNoSensitive(attrs.getValue());
    }

    @Test
    void loginSuccessDoesNotRecordFailure() {
        LocalUser user = activeUser();
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordHasher.matches("pw", "$hash")).thenReturn(true);
        stubTokenIssuance();

        service.login("alice", "pw");

        verify(auditPort, never()).record(eq("AUTH_LOGIN_FAILED"), any(), anyString(), anyString(), any(), any());
    }

    private void assertNoSensitive(Map<String, Object> attrs) {
        assertThat(attrs).doesNotContainKeys("password", "rawPassword", "newPassword",
                "refreshToken", "accessToken", "credential", "rawCredential");
        for (Object value : attrs.values()) {
            if (value instanceof String s) {
                assertThat(s).doesNotContain("$hash", "$newhash");
            }
        }
    }
}
