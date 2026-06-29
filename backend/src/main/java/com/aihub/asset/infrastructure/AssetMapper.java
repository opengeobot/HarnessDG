/*
 * 功能: 资产单表 Mapper（MyBatis-Plus BaseMapper）。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资产单表 Mapper。仅承载 {@code asset} 表的单表 CRUD、条件构造与逻辑删除，复杂检索见显式 SQL DAO。
 */
@Mapper
public interface AssetMapper extends BaseMapper<AssetEntity> {
}
