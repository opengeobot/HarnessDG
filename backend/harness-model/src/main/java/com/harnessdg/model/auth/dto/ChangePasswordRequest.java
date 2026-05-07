/**
 * 功能：修改密码请求
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.model.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在 6-100 之间")
    private String newPassword;
}
