/*
 * 功能: 数据集扩展持久化实体，映射 asset_dataset 表。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.infrastructure;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.List;

/**
 * 数据集扩展持久化实体（{@code asset_dataset} 表）。
 */
@TableName(value = "asset_dataset", autoResultMap = true)
public class AssetDatasetEntity {

    @TableId(value = "asset_id")
    private String assetId;

    @TableField("format")
    private String format;

    @TableField("modality")
    private String modality;

    @TableField(value = "task_codes", typeHandler = JsonbStringListTypeHandler.class)
    private List<String> taskCodes;

    @TableField(value = "modality_codes", typeHandler = JsonbStringListTypeHandler.class)
    private List<String> modalityCodes;

    @TableField(value = "format_codes", typeHandler = JsonbStringListTypeHandler.class)
    private List<String> formatCodes;

    @TableField(value = "language_codes", typeHandler = JsonbStringListTypeHandler.class)
    private List<String> languageCodes;

    @TableField("sensitivity_code")
    private String sensitivityCode;

    @TableField("sample_count")
    private Long sampleCount;

    @TableField("total_bytes")
    private Long totalBytes;

    @TableField("size_bucket_code")
    private String sizeBucketCode;

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

    public List<String> getTaskCodes() {
        return taskCodes;
    }

    public void setTaskCodes(List<String> taskCodes) {
        this.taskCodes = taskCodes;
    }

    public List<String> getModalityCodes() {
        return modalityCodes;
    }

    public void setModalityCodes(List<String> modalityCodes) {
        this.modalityCodes = modalityCodes;
    }

    public List<String> getFormatCodes() {
        return formatCodes;
    }

    public void setFormatCodes(List<String> formatCodes) {
        this.formatCodes = formatCodes;
    }

    public List<String> getLanguageCodes() {
        return languageCodes;
    }

    public void setLanguageCodes(List<String> languageCodes) {
        this.languageCodes = languageCodes;
    }

    public String getSensitivityCode() {
        return sensitivityCode;
    }

    public void setSensitivityCode(String sensitivityCode) {
        this.sensitivityCode = sensitivityCode;
    }

    public Long getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(Long sampleCount) {
        this.sampleCount = sampleCount;
    }

    public Long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(Long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public String getSizeBucketCode() {
        return sizeBucketCode;
    }

    public void setSizeBucketCode(String sizeBucketCode) {
        this.sizeBucketCode = sizeBucketCode;
    }
}
