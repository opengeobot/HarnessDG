/**
 * 功能：认证服务单元测试
 * 时间：2026-05-12
 * 作者：AxeXie
 */
package com.harnessdg.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.harnessdg.auth.jwt.JwtTokenProvider;
import com.harnessdg.auth.mapper.SysRoleMapper;
import com.harnessdg.auth.mapper.SysUserMapper;
import com.harnessdg.common.exception.BizException;
import com.harnessdg.model.auth.dto.LoginRequest;
import com.harnessdg.model.auth.dto.LoginResponse;
import com.harnessdg.model.auth.entity.SysUser;
import com.harnessdg.auth.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private SysUserMapper userMapper;
    @Mock private SysRoleMapper roleMapper;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private AuthServiceImpl authService;

    private SysUser testUser;

    @BeforeEach
    void setUp() {
        testUser = new SysUser();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setPasswordHash("$2a$10$encoded_password_hash");
        testUser.setDisplayName("测试用户");
        testUser.setEmail("test@example.com");
        testUser.setStatus("active");
        testUser.setPreferredLocale("zh_CN");
        testUser.setCreatedAt(OffsetDateTime.now());
        testUser.setUpdatedAt(OffsetDateTime.now());
    }

    /**
     * 测试：登录成功
     */
    @Test
    void testLogin_success() {
        // 准备
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("correct_password");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);
        when(passwordEncoder.matches("correct_password", testUser.getPasswordHash())).thenReturn(true);
        when(roleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("USER", "ADMIN"));
        when(tokenProvider.generateAccessToken(1L, "testuser", List.of("USER", "ADMIN"))).thenReturn("access_token");
        when(tokenProvider.generateRefreshToken(1L, "testuser")).thenReturn("refresh_token");
        when(tokenProvider.getAccessTokenExpirationSeconds()).thenReturn(3600L);

        // 执行
        LoginResponse response = authService.login(request);

        // 验证
        assertNotNull(response);
        assertEquals("access_token", response.getAccessToken());
        assertEquals("refresh_token", response.getRefreshToken());
        assertEquals(3600L, response.getExpiresIn());
        assertNotNull(response.getUserInfo());
        assertEquals("testuser", response.getUserInfo().getUsername());
        assertEquals("测试用户", response.getUserInfo().getDisplayName());
        assertEquals(2, response.getUserInfo().getRoles().size());
    }

    /**
     * 测试：密码错误
     */
    @Test
    void testLogin_wrongPassword() {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("wrong_password");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);
        when(passwordEncoder.matches("wrong_password", testUser.getPasswordHash())).thenReturn(false);

        // 执行并验证异常
        assertThrows(BizException.class, () -> authService.login(request));
        verify(tokenProvider, never()).generateAccessToken(any(), any(), any());
    }

    /**
     * 测试：用户不存在
     */
    @Test
    void testLogin_userNotFound() {
        LoginRequest request = new LoginRequest();
        request.setUsername("nonexistent");
        request.setPassword("password");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // 执行并验证异常
        assertThrows(BizException.class, () -> authService.login(request));
    }

    /**
     * 测试：用户状态非活跃
     */
    @Test
    void testLogin_inactiveUser() {
        testUser.setStatus("disabled");

        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("correct_password");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);
        when(passwordEncoder.matches("correct_password", testUser.getPasswordHash())).thenReturn(true);

        // 执行并验证异常
        assertThrows(BizException.class, () -> authService.login(request));
    }

    /**
     * 测试：刷新 Token 成功
     */
    @Test
    void testRefreshToken_success() {
        String refreshToken = "valid_refresh_token";

        when(tokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(tokenProvider.getUsernameFromToken(refreshToken)).thenReturn("testuser");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);
        when(roleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("USER"));
        when(tokenProvider.generateAccessToken(1L, "testuser", List.of("USER"))).thenReturn("new_access_token");
        when(tokenProvider.generateRefreshToken(1L, "testuser")).thenReturn("new_refresh_token");
        when(tokenProvider.getAccessTokenExpirationSeconds()).thenReturn(3600L);

        // 执行
        LoginResponse response = authService.refreshToken(refreshToken);

        // 验证
        assertNotNull(response);
        assertEquals("new_access_token", response.getAccessToken());
        assertEquals("new_refresh_token", response.getRefreshToken());
    }

    /**
     * 测试：刷新 Token 失败（Token 无效）
     */
    @Test
    void testRefreshToken_invalidToken() {
        String invalidToken = "invalid_token";

        when(tokenProvider.validateToken(invalidToken)).thenReturn(false);

        // 执行并验证异常
        assertThrows(BizException.class, () -> authService.refreshToken(invalidToken));
    }

    /**
     * 测试：获取当前用户信息
     */
    @Test
    void testGetCurrentUser() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);
        when(roleMapper.selectRoleCodesByUserId(1L)).thenReturn(List.of("USER", "VIEWER"));

        // 执行
        LoginResponse.UserInfo userInfo = authService.getCurrentUser("testuser");

        // 验证
        assertNotNull(userInfo);
        assertEquals(1L, userInfo.getId());
        assertEquals("testuser", userInfo.getUsername());
        assertEquals("测试用户", userInfo.getDisplayName());
        assertEquals("test@example.com", userInfo.getEmail());
        assertEquals(2, userInfo.getRoles().size());
    }

    /**
     * 测试：获取当前用户信息 - 用户不存在
     */
    @Test
    void testGetCurrentUser_notFound() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // 执行并验证异常
        assertThrows(BizException.class, () -> authService.getCurrentUser("nonexistent"));
    }
}
