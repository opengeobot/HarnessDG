/**
 * 功能：角色权限实体，对应 sys_permission 表
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.model.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.harnessdg.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    private Long roleId;

    private String resourceType;

    private String resourceId;

    private String action;

    /** allow / deny */
    private String effect;
}
