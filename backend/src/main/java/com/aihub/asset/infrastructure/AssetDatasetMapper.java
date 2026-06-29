/*
 * 功能: 数据集扩展单表 Mapper（MyBatis-Plus BaseMapper）。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据集扩展单表 Mapper（{@code asset_dataset}）。
 */
@Mapper
public interface AssetDatasetMapper extends BaseMapper<AssetDatasetEntity> {
}
