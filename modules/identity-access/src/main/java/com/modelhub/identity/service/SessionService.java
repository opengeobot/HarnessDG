package com.modelhub.identity.service;

import com.modelhub.identity.config.IdentityProperties;
import com.modelhub.identity.domain.RefreshSessionEntity;
import com.modelhub.identity.domain.UserEntity;
import com.modelhub.identity.repo.RefreshSessionRepository;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Refresh 会话管理（02 §6.1）：
 * - 服务端只保存 token/CSRF 的 SHA-256 哈希；
 * - refresh 一次即轮换（同 family 新会话，旧会话标记 revoked+replaced_by）；
 * - 宽限期内重用旧 token 视为并发刷新（发新会话）；超过宽限期视为重放攻击，吊销整个 family。
 */
@Service
public class SessionService {

    /** 一次会话签发的完整凭据（明文只在响应中出现一次）。rowId 仅服务端内部使用。 */
    public record IssuedSession(Long userId, Long rowId, String accessToken, String refreshToken,
                                String csrfToken, UUID sessionId, OffsetDateTime expiresAt) {}

    private final RefreshSessionRepository sessions;
    private final JwtService jwtService;
    private final IdentityProperties props;
    private final AuditService auditService;

    public SessionService(RefreshSessionRepository sessions, JwtService jwtService,
                          IdentityProperties props, AuditService auditService) {
        this.sessions = sessions;
        this.jwtService = jwtService;
        this.props = props;
        this.auditService = auditService;
    }

    @Transactional
    public IssuedSession issue(UserEntity user, UUID familyId, String clientMeta) {
        UUID family = familyId != null ? familyId : UUID.randomUUID();
        return createSession(user, family, clientMeta);
    }

    /** refresh 轮换：校验旧 token+CSRF，返回新凭据。重放超过宽限期则吊销 family 并 401。 */
    @Transactional
    public IssuedSession rotate(String refreshToken, String csrfToken, String clientMeta) {
        RefreshSessionEntity session = sessions.findByTokenHashForUpdate(sha256(refreshToken))
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED, "Refresh Token 无效"));
        OffsetDateTime now = OffsetDateTime.now();
        if (session.isExpired(now)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Refresh Token 已过期，请重新登录");
        }
        if (!sha256(csrfToken).equals(session.getCsrfTokenHash())) {
            throw new ApiException(ErrorCode.CSRF_INVALID, "CSRF Token 与会话不匹配");
        }
        if (session.isRevoked()) {
            long ageSeconds = Duration.between(session.getRevokedAt(), now).toSeconds();
            if (ageSeconds < props.session().replayGraceSeconds()) {
                // 并发刷新（同一客户端重试）：不吊销 family，补发新会话
                auditService.appendSimple(String.valueOf(session.getUserId()), "session.refresh_concurrent",
                        "session:" + session.getSessionId(), "accepted");
                return createSessionRaw(session.getUserId(), session.getAuthVersion(),
                        session.getFamilyId(), clientMeta);
            }
            sessions.revokeFamily(session.getFamilyId(), now);
            auditService.appendSimple(String.valueOf(session.getUserId()), "session.replay_detected",
                    "family:" + session.getFamilyId(), "family_revoked");
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "检测到 Refresh Token 重用，会话已全部吊销");
        }
        IssuedSession issued = createSessionRaw(session.getUserId(), session.getAuthVersion(),
                session.getFamilyId(), clientMeta);
        sessions.rotateIfActive(session.getId(), now, issued.rowId());
        return issued;
    }

    @Transactional
    public void revokeByToken(String refreshToken) {
        sessions.findByTokenHash(sha256(refreshToken)).ifPresent(s -> {
            if (!s.isRevoked()) {
                OffsetDateTime now = OffsetDateTime.now();
                s.setRevokedAt(now);
                // 显式 logout 立即失效：同时置过期，避免宽限窗内被 refresh 补发
                s.setExpiresAt(now);
                sessions.save(s);
            }
        });
    }

    @Transactional
    public int revokeAllForUser(Long userId) {
        return sessions.revokeAllForUser(userId, OffsetDateTime.now());
    }

    private IssuedSession createSession(UserEntity user, UUID family, String clientMeta) {
        return createSessionRaw(user.getId(), user.getAuthVersion(), family, clientMeta);
    }

    private IssuedSession createSessionRaw(Long userId, long authVersion, UUID family, String clientMeta) {
        String refreshToken = JwtService.randomTokenUrlSafe(48);
        String csrfToken = JwtService.randomTokenUrlSafe(32);
        UUID sessionId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusSeconds(props.session().refreshTtlSeconds());

        RefreshSessionEntity entity = new RefreshSessionEntity();
        entity.setSessionId(sessionId);
        entity.setUserId(userId);
        entity.setFamilyId(family);
        entity.setTokenHash(sha256(refreshToken));
        entity.setCsrfTokenHash(sha256(csrfToken));
        entity.setAuthVersion(authVersion);
        entity.setExpiresAt(expiresAt);
        entity.setClientMeta(clientMeta);
        RefreshSessionEntity saved = sessions.save(entity);

        String accessToken = jwtService.issueAccessToken(userId, null, authVersion, sessionId.toString());
        return new IssuedSession(userId, saved.getId(), accessToken, refreshToken, csrfToken,
                sessionId, expiresAt);
    }

    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
