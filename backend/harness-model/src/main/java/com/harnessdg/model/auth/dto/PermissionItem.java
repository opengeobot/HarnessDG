/**
 * 功能：权限项上送请求
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.model.auth.dto;

import lombok.Data;

@Data
public class PermissionItem {

    private String resourceType;

    private String resourceId;

    private String action;

    private String effect = "allow";
}
