/*
 * 功能: 资产-标签关联单表 Mapper（MyBatis-Plus BaseMapper）。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.asset.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资产-标签关联单表 Mapper（{@code asset_tag}）。
 *
 * <p>承载 asset_tag 表的关联读写：按 asset_id 查询 tagIds、按 asset_id 删除关联、批量插入关联。
 */
@Mapper
public interface AssetTagMapper extends BaseMapper<AssetTagEntity> {
}
