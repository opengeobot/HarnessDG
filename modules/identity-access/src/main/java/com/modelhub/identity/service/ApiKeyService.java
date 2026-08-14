package com.modelhub.identity.service;

import com.modelhub.identity.domain.ApiKeyEntity;
import com.modelhub.identity.domain.UserEntity;
import com.modelhub.identity.repo.ApiKeyRepository;
import com.modelhub.identity.repo.UserRepository;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import com.modelhub.shared.id.PublicIds;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * API Key CRUD + auth (03 sec 7).
 * Key format: mhk_<prefix>.<secret>  prefix=first 8 hex of hash, secret=32 random hex.
 */
@Service
public class ApiKeyService {

    private static final String KEY_PREFIX = "mhk_";
    private final ApiKeyRepository keys;
    private final UserRepository users;

    public ApiKeyService(ApiKeyRepository keys, UserRepository users) {
        this.keys = keys;
        this.users = users;
    }

    public record ApiKeyView(UUID id, String name, String keyPrefix, String scopes,
                             OffsetDateTime expiresAt, OffsetDateTime createdAt) {}

    public record CreatedApiKey(ApiKeyView view, String plaintextKey) {}

    @Transactional
    public CreatedApiKey create(CurrentPrincipal actor, String name, String scopes, OffsetDateTime expiresAt) {
        byte[] secretBytes = new byte[32];
        new SecureRandom().nextBytes(secretBytes);
        String secret = HexFormat.of().formatHex(secretBytes);
        String hash = sha256(secret);
        String prefix = hash.substring(0, 8);
        String plaintext = KEY_PREFIX + prefix + "." + secret;

        ApiKeyEntity k = new ApiKeyEntity();
        k.setPublicId(PublicIds.next());
        k.setUserId(actor.userId());
        k.setName(name);
        k.setKeyPrefix(prefix);
        k.setKeyHash(hash);
        k.setScopes(scopes == null ? "" : scopes);
        k.setExpiresAt(expiresAt);
        keys.save(k);
        ApiKeyView view = toView(k);
        return new CreatedApiKey(view, plaintext);
    }

    @Transactional(readOnly = true)
    public List<ApiKeyView> list(CurrentPrincipal actor) {
        return keys.findByUserIdAndRevokedAtIsNull(actor.userId()).stream()
                .map(this::toView).toList();
    }

    @Transactional
    public void revoke(CurrentPrincipal actor, UUID keyPublicId) {
        ApiKeyEntity k = keys.findByPublicId(keyPublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "API Key not found"));
        if (!k.getUserId().equals(actor.userId()) && !actor.isPlatformAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Not your API key");
        }
        k.setRevokedAt(OffsetDateTime.now());
        keys.save(k);
    }

    /** Auth path: resolve Bearer mhk_... to CurrentPrincipal. */
    @Transactional(readOnly = true)
    public CurrentPrincipal authenticate(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX) || rawKey.length() < 18) {
            return null;
        }
        String afterPrefix = rawKey.substring(4);
        int dot = afterPrefix.indexOf(".");
        if (dot < 0) return null;
        String prefix = afterPrefix.substring(0, dot);
        String secret = afterPrefix.substring(dot + 1);
        String hash = sha256(secret);
        List<ApiKeyEntity> candidates = keys.findByKeyPrefixAndRevokedAtIsNull(prefix);
        for (ApiKeyEntity k : candidates) {
            if (k.getKeyHash().equals(hash) && !k.isExpired()) {
                k.setLastUsedAt(OffsetDateTime.now());
                keys.save(k);
                UserEntity u = users.findById(k.getUserId()).orElse(null);
                if (u == null || !u.isActive()) return null;
                return new CurrentPrincipal(u.getId(), u.getPublicId(), u.getUsername(),
                        u.getAuthVersion(), "apikey:" + k.getPublicId(), Set.of());
            }
        }
        return null;
    }

    private ApiKeyView toView(ApiKeyEntity k) {
        return new ApiKeyView(k.getPublicId(), k.getName(), k.getKeyPrefix(),
                k.getScopes(), k.getExpiresAt(), k.getCreatedAt());
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}