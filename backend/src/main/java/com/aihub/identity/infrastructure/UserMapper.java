/*
 * 功能: 本地用户单表 Mapper（MyBatis-Plus BaseMapper）。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.identity.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 本地用户单表 Mapper（{@code iam_user} 表）。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
