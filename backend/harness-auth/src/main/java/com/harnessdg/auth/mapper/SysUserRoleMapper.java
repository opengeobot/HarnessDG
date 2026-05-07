/**
 * 功能：用户-角色关联 Mapper
 * 时间：2026-05-07
 * 作者：AxeXie
 */
package com.harnessdg.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harnessdg.model.auth.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {

    @Select("""
            SELECT role_id FROM sys_user_role
            WHERE user_id = #{userId} AND is_deleted = false
            """)
    List<Long> selectRoleIdsByUserId(Long userId);
}
