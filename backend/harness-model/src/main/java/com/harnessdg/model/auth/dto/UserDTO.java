/**
 * 功能：用户信息返回 DTO
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.model.auth.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class UserDTO {

    private Long id;

    private String username;

    private String displayName;

    private String email;

    private String phone;

    private String avatar;

    private String preferredLocale;

    private String status;

    private OffsetDateTime lastLoginAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private List<String> roleCodes;

    private List<Long> roleIds;
}
