/**
 * 功能：系统用户实体，对应 sys_user 表
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.model.auth.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.harnessdg.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.OffsetDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    private String username;

    private String passwordHash;

    private String displayName;

    private String email;

    private String phone;

    /** 头像 URL，DB 列名为 avatar_url */
    @TableField("avatar_url")
    private String avatar;

    /** 首选语言，DB 列名为 locale */
    @TableField("locale")
    private String preferredLocale;

    private String status;

    private OffsetDateTime lastLoginAt;
}
