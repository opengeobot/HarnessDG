/*
 * 功能: 数据集扩展持久化实体，映射 asset_dataset 表。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.infrastructure;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 数据集扩展持久化实体（{@code asset_dataset} 表）。
 */
@TableName("asset_dataset")
public class AssetDatasetEntity {

    @TableId(value = "asset_id")
    private String assetId;

    @TableField("format")
    private String format;

    @TableField("modality")
    private String modality;

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getModality() {
        return modality;
    }

    public void setModality(String modality) {
        this.modality = modality;
    }
}
