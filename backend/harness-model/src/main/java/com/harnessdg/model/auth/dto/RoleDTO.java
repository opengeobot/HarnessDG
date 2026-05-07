/**
 * 功能：角色 DTO
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.model.auth.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RoleDTO {

    private Long id;

    private String code;

    private Map<String, String> name;

    private Map<String, String> description;

    private Boolean isSystem;

    private String status;

    private Integer userCount;

    private Integer permissionCount;

    private List<PermissionDTO> permissions;
}
