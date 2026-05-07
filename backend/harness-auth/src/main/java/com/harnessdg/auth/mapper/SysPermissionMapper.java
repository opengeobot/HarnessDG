/**
 * 功能：权限 Mapper
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harnessdg.model.auth.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {
}
