/**
 * 功能：用户管理服务接口
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.auth.service;

import com.harnessdg.common.page.PageRequest;
import com.harnessdg.common.page.PageResult;
import com.harnessdg.model.auth.dto.ChangePasswordRequest;
import com.harnessdg.model.auth.dto.UserCreateRequest;
import com.harnessdg.model.auth.dto.UserDTO;
import com.harnessdg.model.auth.dto.UserUpdateRequest;

import java.util.List;

public interface UserService {

    PageResult<UserDTO> listUsers(String keyword, String status, PageRequest pageRequest);

    UserDTO getUser(Long id);

    UserDTO createUser(UserCreateRequest request);

    UserDTO updateUser(Long id, UserUpdateRequest request);

    void deleteUser(Long id);

    void updateStatus(Long id, String status);

    void resetPassword(Long id, String newPassword);

    void changeMyPassword(String username, ChangePasswordRequest request);

    void assignRoles(Long id, List<Long> roleIds);
}
