package com.harnessdg.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.harnessdg.auth.jwt.JwtTokenProvider;
import com.harnessdg.auth.mapper.SysRoleMapper;
import com.harnessdg.auth.mapper.SysUserMapper;
import com.harnessdg.auth.service.AuthService;
import com.harnessdg.common.exception.BizException;
import com.harnessdg.common.response.ErrorCode;
import com.harnessdg.model.auth.dto.LoginRequest;
import com.harnessdg.model.auth.dto.LoginResponse;
import com.harnessdg.model.auth.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Override
    public LoginResponse login(LoginRequest request) {
        SysUser user = findUserByUsername(request.getUsername());

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }

        if (!"active".equals(user.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }

        List<String> roles = roleMapper.selectRoleCodesByUserId(user.getId());

        String accessToken = tokenProvider.generateAccessToken(user.getId(), user.getUsername(), roles);
        String refreshToken = tokenProvider.generateRefreshToken(user.getId(), user.getUsername());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(tokenProvider.getAccessTokenExpirationSeconds())
                .userInfo(buildUserInfo(user, roles))
                .build();
    }

    @Override
    public LoginResponse refreshToken(String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED);
        }

        String username = tokenProvider.getUsernameFromToken(refreshToken);
        SysUser user = findUserByUsername(username);
        List<String> roles = roleMapper.selectRoleCodesByUserId(user.getId());

        String newAccessToken = tokenProvider.generateAccessToken(user.getId(), user.getUsername(), roles);
        String newRefreshToken = tokenProvider.generateRefreshToken(user.getId(), user.getUsername());

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(tokenProvider.getAccessTokenExpirationSeconds())
                .userInfo(buildUserInfo(user, roles))
                .build();
    }

    @Override
    public LoginResponse.UserInfo getCurrentUser(String username) {
        SysUser user = findUserByUsername(username);
        List<String> roles = roleMapper.selectRoleCodesByUserId(user.getId());
        return buildUserInfo(user, roles);
    }

    private SysUser findUserByUsername(String username) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        SysUser user = userMapper.selectOne(wrapper);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    private LoginResponse.UserInfo buildUserInfo(SysUser user, List<String> roles) {
        return LoginResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .preferredLocale(user.getPreferredLocale())
                .roles(roles)
                .build();
    }
}
