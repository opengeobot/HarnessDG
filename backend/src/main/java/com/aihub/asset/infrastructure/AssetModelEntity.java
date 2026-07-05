/*
 * 功能: 模型扩展持久化实体，映射 asset_model 表。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.infrastructure;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.List;

/**
 * 模型扩展持久化实体（{@code asset_model} 表）。
 */
@TableName(value = "asset_model", autoResultMap = true)
public class AssetModelEntity {

    @TableId(value = "asset_id")
    private String assetId;

    @TableField("framework")
    private String framework;

    @TableField("task")
    private String task;

    @TableField("architecture")
    private String architecture;

    @TableField("parameter_scale")
    private String parameterScale;

    @TableField("`precision`")
    private String precision;

    @TableField("weight_format")
    private String weightFormat;

    @TableField("runtime")
    private String runtime;

    @TableField(value = "known_risks", typeHandler = JsonbStringListTypeHandler.class)
    private List<String> knownRisks;

    @TableField(value = "usage_restrictions", typeHandler = JsonbStringListTypeHandler.class)
    private List<String> usageRestrictions;

    @TableField("sensitivity_code")
    private String sensitivityCode;

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

    public String getParameterScale() {
        return parameterScale;
    }

    public void setParameterScale(String parameterScale) {
        this.parameterScale = parameterScale;
    }

    public String getPrecision() {
        return precision;
    }

    public void setPrecision(String precision) {
        this.precision = precision;
    }

    public String getWeightFormat() {
        return weightFormat;
    }

    public void setWeightFormat(String weightFormat) {
        this.weightFormat = weightFormat;
    }

    public String getRuntime() {
        return runtime;
    }

    public void setRuntime(String runtime) {
        this.runtime = runtime;
    }

    public List<String> getKnownRisks() {
        return knownRisks;
    }

    public void setKnownRisks(List<String> knownRisks) {
        this.knownRisks = knownRisks;
    }

    public List<String> getUsageRestrictions() {
        return usageRestrictions;
    }

    public void setUsageRestrictions(List<String> usageRestrictions) {
        this.usageRestrictions = usageRestrictions;
    }

    public String getSensitivityCode() {
        return sensitivityCode;
    }

    public void setSensitivityCode(String sensitivityCode) {
        this.sensitivityCode = sensitivityCode;
    }
}
