/**
 * 功能：权限 DTO
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.model.auth.dto;

import lombok.Data;

@Data
public class PermissionDTO {

    private Long id;

    private Long roleId;

    private String resourceType;

    private String resourceId;

    private String action;

    private String effect;
}
