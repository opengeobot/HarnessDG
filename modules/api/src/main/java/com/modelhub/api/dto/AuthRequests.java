package com.modelhub.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 请求 DTO（04 §1：camelCase；未定义字段由 Jackson FAIL_ON_UNKNOWN_PROPERTIES 拒绝）。
 */
public final class AuthRequests {

    private AuthRequests() {}

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 31) String username,
            @NotBlank @Size(min = 8, max = 128) String password,
            @Size(max = 128) String nickname) {}

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {}

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 128) String nickname) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 128) String newPassword) {}
}
