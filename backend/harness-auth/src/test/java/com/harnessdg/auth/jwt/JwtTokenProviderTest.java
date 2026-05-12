package com.harnessdg.auth.jwt;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 功能：JWT Token 提供者单元测试
 * 时间：2026-05-12
 * 作者：AxeXie
 */
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    private static final String TEST_SECRET = "harnessdg-test-secret-key-must-be-at-least-256-bits-long!";
    private static final long ACCESS_EXPIRE = 3600;
    private static final long REFRESH_EXPIRE = 604800;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(TEST_SECRET, ACCESS_EXPIRE, REFRESH_EXPIRE);
    }

    @Test
    void testGenerateAccessToken() {
        Long userId = 1L;
        String username = "testuser";
        List<String> roles = List.of("ADMIN", "USER");

        String token = jwtTokenProvider.generateAccessToken(userId, username, roles);

        assertNotNull(token);
        assertTrue(token.length() > 0);
    }

    @Test
    void testGenerateRefreshToken() {
        Long userId = 1L;
        String username = "testuser";

        String token = jwtTokenProvider.generateRefreshToken(userId, username);

        assertNotNull(token);
        assertTrue(token.length() > 0);
    }

    @Test
    void testParseAccessToken() {
        Long userId = 1L;
        String username = "testuser";
        List<String> roles = List.of("ADMIN", "USER");

        String token = jwtTokenProvider.generateAccessToken(userId, username, roles);
        Claims claims = jwtTokenProvider.parseToken(token);

        assertEquals(username, claims.getSubject());
        assertEquals(userId, claims.get("userId", Long.class));
    }

    @Test
    void testValidateToken_ValidToken() {
        String token = jwtTokenProvider.generateAccessToken(1L, "testuser", List.of("USER"));

        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    void testValidateToken_InvalidToken() {
        assertFalse(jwtTokenProvider.validateToken("invalid.token.here"));
    }

    @Test
    void testValidateToken_EmptyToken() {
        assertFalse(jwtTokenProvider.validateToken(null));
        assertFalse(jwtTokenProvider.validateToken(""));
    }

    @Test
    void testGetUsernameFromToken() {
        String username = "testuser";
        String token = jwtTokenProvider.generateAccessToken(1L, username, List.of("USER"));

        assertEquals(username, jwtTokenProvider.getUsernameFromToken(token));
    }

    @Test
    void testGetUserIdFromToken() {
        Long userId = 42L;
        String token = jwtTokenProvider.generateAccessToken(userId, "testuser", List.of("USER"));

        assertEquals(userId, jwtTokenProvider.getUserIdFromToken(token));
    }

    @Test
    void testGetRolesFromToken() {
        List<String> roles = List.of("ADMIN", "USER", "VIEWER");
        String token = jwtTokenProvider.generateAccessToken(1L, "testuser", roles);

        List<String> extractedRoles = jwtTokenProvider.getRolesFromToken(token);

        assertEquals(3, extractedRoles.size());
        assertTrue(extractedRoles.contains("ADMIN"));
        assertTrue(extractedRoles.contains("USER"));
    }

    @Test
    void testGetAccessTokenExpirationSeconds() {
        assertEquals(ACCESS_EXPIRE, jwtTokenProvider.getAccessTokenExpirationSeconds());
    }

    @Test
    void testTokenWithTamperedPayload() {
        String validToken = jwtTokenProvider.generateAccessToken(1L, "testuser", List.of("USER"));
        String tamperedToken = validToken.substring(0, validToken.length() - 5) + "XXXXX";

        assertFalse(jwtTokenProvider.validateToken(tamperedToken));
    }
}
