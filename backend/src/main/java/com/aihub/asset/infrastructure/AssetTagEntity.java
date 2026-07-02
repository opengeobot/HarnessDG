/*
 * 功能: 资产-标签关联持久化实体，映射 asset_tag 表。
 * 时间: 2026-07-01
 * 作者: AxeXie
 */
package com.aihub.asset.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;

/**
 * 资产-标签关联持久化实体（{@code asset_tag} 表）。
 *
 * <p>仅用于 infrastructure 与数据库映射，承载受控标签 tagIds 的关联读写。
 */
@TableName("asset_tag")
public class AssetTagEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("asset_id")
    private String assetId;

    @TableField("tag_id")
    private String tagId;

    @TableField("created_at")
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public String getTagId() {
        return tagId;
    }

    public void setTagId(String tagId) {
        this.tagId = tagId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
