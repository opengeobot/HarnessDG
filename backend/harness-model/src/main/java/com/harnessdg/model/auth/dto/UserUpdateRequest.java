/**
 * 功能：用户更新请求 DTO
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.model.auth.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class UserUpdateRequest {

    private String displayName;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String phone;

    private String avatar;

    private String preferredLocale;
}
