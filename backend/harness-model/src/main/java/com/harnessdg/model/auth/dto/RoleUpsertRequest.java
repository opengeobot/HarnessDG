/**
 * 功能：角色创建/更新请求 DTO
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.model.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class RoleUpsertRequest {

    @NotBlank(message = "角色编码不能为空")
    private String code;

    @NotNull(message = "角色名称不能为空")
    private Map<String, String> name;

    private Map<String, String> description;

    private String status = "active";
}
