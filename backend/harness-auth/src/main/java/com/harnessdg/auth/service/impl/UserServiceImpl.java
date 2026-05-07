/**
 * 功能：用户管理服务实现
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.harnessdg.auth.mapper.SysRoleMapper;
import com.harnessdg.auth.mapper.SysUserMapper;
import com.harnessdg.auth.mapper.SysUserRoleMapper;
import com.harnessdg.auth.service.UserService;
import com.harnessdg.common.exception.BizException;
import com.harnessdg.common.page.PageRequest;
import com.harnessdg.common.page.PageResult;
import com.harnessdg.common.response.ErrorCode;
import com.harnessdg.model.auth.dto.ChangePasswordRequest;
import com.harnessdg.model.auth.dto.UserCreateRequest;
import com.harnessdg.model.auth.dto.UserDTO;
import com.harnessdg.model.auth.dto.UserUpdateRequest;
import com.harnessdg.model.auth.entity.SysRole;
import com.harnessdg.model.auth.entity.SysUser;
import com.harnessdg.model.auth.entity.SysUserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PageResult<UserDTO> listUsers(String keyword, String status, PageRequest pageRequest) {
        Page<SysUser> page = new Page<>(pageRequest.getPage(), pageRequest.getPageSize());
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getDisplayName, keyword)
                    .or().like(SysUser::getEmail, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SysUser::getStatus, status);
        }
        wrapper.orderByDesc(SysUser::getCreatedAt);
        Page<SysUser> result = userMapper.selectPage(page, wrapper);

        List<UserDTO> items = result.getRecords().stream().map(this::toDTO).toList();
        return PageResult.of(items, result.getTotal(), pageRequest.getPage(), pageRequest.getPageSize());
    }

    @Override
    public UserDTO getUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return toDTO(user);
    }

    @Override
    @Transactional
    public UserDTO createUser(UserCreateRequest request) {
        LambdaQueryWrapper<SysUser> uw = new LambdaQueryWrapper<>();
        uw.eq(SysUser::getUsername, request.getUsername());
        if (userMapper.selectCount(uw) > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
        if (StringUtils.hasText(request.getEmail())) {
            LambdaQueryWrapper<SysUser> ew = new LambdaQueryWrapper<>();
            ew.eq(SysUser::getEmail, request.getEmail());
            if (userMapper.selectCount(ew) > 0) {
                throw new BizException(ErrorCode.EMAIL_EXISTS);
            }
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setAvatar(request.getAvatar());
        user.setPreferredLocale(StringUtils.hasText(request.getPreferredLocale())
                ? request.getPreferredLocale() : "zh_CN");
        user.setStatus("active");
        userMapper.insert(user);

        if (request.getRoleIds() != null && !request.getRoleIds().isEmpty()) {
            saveUserRoles(user.getId(), request.getRoleIds());
        }

        return toDTO(userMapper.selectById(user.getId()));
    }

    @Override
    @Transactional
    public UserDTO updateUser(Long id, UserUpdateRequest request) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (StringUtils.hasText(request.getEmail()) && !request.getEmail().equals(user.getEmail())) {
            LambdaQueryWrapper<SysUser> ew = new LambdaQueryWrapper<>();
            ew.eq(SysUser::getEmail, request.getEmail()).ne(SysUser::getId, id);
            if (userMapper.selectCount(ew) > 0) {
                throw new BizException(ErrorCode.EMAIL_EXISTS);
            }
        }
        user.setDisplayName(request.getDisplayName());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setAvatar(request.getAvatar());
        if (StringUtils.hasText(request.getPreferredLocale())) {
            user.setPreferredLocale(request.getPreferredLocale());
        }
        userMapper.updateById(user);
        return toDTO(user);
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        if (id != null && id == 1L) {
            throw new BizException(ErrorCode.FORBIDDEN, "Default admin cannot be deleted");
        }
        int rows = userMapper.deleteById(id);
        if (rows == 0) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        // 同步软删用户-角色关联
        LambdaUpdateWrapper<SysUserRole> uw = new LambdaUpdateWrapper<>();
        uw.eq(SysUserRole::getUserId, id);
        SysUserRole del = new SysUserRole();
        del.setIsDeleted(true);
        userRoleMapper.update(del, uw);
    }

    @Override
    public void updateStatus(Long id, String status) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
    }

    @Override
    public void changeMyPassword(String username, ChangePasswordRequest request) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        SysUser user = userMapper.selectOne(wrapper);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.OLD_PASSWORD_INCORRECT);
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    public void assignRoles(Long id, List<Long> roleIds) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        // 先软删旧的用户-角色关联
        LambdaUpdateWrapper<SysUserRole> uw = new LambdaUpdateWrapper<>();
        uw.eq(SysUserRole::getUserId, id);
        SysUserRole del = new SysUserRole();
        del.setIsDeleted(true);
        userRoleMapper.update(del, uw);

        saveUserRoles(id, roleIds == null ? Collections.emptyList() : roleIds);
    }

    private void saveUserRoles(Long userId, List<Long> roleIds) {
        for (Long roleId : roleIds) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    private UserDTO toDTO(SysUser user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setDisplayName(user.getDisplayName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setAvatar(user.getAvatar());
        dto.setPreferredLocale(user.getPreferredLocale());
        dto.setStatus(user.getStatus());
        dto.setLastLoginAt(user.getLastLoginAt());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUpdatedAt(user.getUpdatedAt());

        List<Long> roleIds = userRoleMapper.selectRoleIdsByUserId(user.getId());
        dto.setRoleIds(roleIds);
        if (!roleIds.isEmpty()) {
            LambdaQueryWrapper<SysRole> rw = new LambdaQueryWrapper<>();
            rw.in(SysRole::getId, roleIds);
            List<SysRole> roles = roleMapper.selectList(rw);
            dto.setRoleCodes(roles.stream().map(SysRole::getCode).toList());
        } else {
            dto.setRoleCodes(Collections.emptyList());
        }
        return dto;
    }
}
