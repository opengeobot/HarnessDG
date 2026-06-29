/*
 * 功能: 模型扩展持久化实体，映射 asset_model 表。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.infrastructure;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 模型扩展持久化实体（{@code asset_model} 表）。
 */
@TableName("asset_model")
public class AssetModelEntity {

    @TableId(value = "asset_id")
    private String assetId;

    @TableField("framework")
    private String framework;

    @TableField("task")
    private String task;

    @TableField("architecture")
    private String architecture;

    public String getAssetId() {
        return assetId;
    }

    public void setAssetId(String assetId) {
        this.assetId = assetId;
    }

    public String getFramework() {
        return framework;
    }

    public void setFramework(String framework) {
        this.framework = framework;
    }

    public String getTask() {
        return task;
    }

    public void setTask(String task) {
        this.task = task;
    }

    public String getArchitecture() {
        return architecture;
    }

    public void setArchitecture(String architecture) {
        this.architecture = architecture;
    }
}
