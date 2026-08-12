package com.modelhub.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 数据集类型扩展（03 §3.2）：gated 是通用仓库策略，不在此重复。 */
@Entity
@Table(name = "dataset_profiles")
public class DatasetProfileEntity {

    @Id
    @Column(name = "repository_id")
    private Long repositoryId;

    @Column(name = "task_value_id")
    private Long taskValueId;

    @Column(name = "estimated_rows")
    private Long estimatedRows;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_formats", nullable = false)
    private String dataFormats;

    @Column(name = "sensitivity_level")
    private String sensitivityLevel;

    @Column(name = "preview_policy")
    private String previewPolicy;

    public Long getRepositoryId() { return repositoryId; }
    public void setRepositoryId(Long repositoryId) { this.repositoryId = repositoryId; }
    public Long getTaskValueId() { return taskValueId; }
    public void setTaskValueId(Long taskValueId) { this.taskValueId = taskValueId; }
    public Long getEstimatedRows() { return estimatedRows; }
    public void setEstimatedRows(Long estimatedRows) { this.estimatedRows = estimatedRows; }
    public String getDataFormats() { return dataFormats; }
    public void setDataFormats(String dataFormats) { this.dataFormats = dataFormats; }
    public String getSensitivityLevel() { return sensitivityLevel; }
    public void setSensitivityLevel(String sensitivityLevel) { this.sensitivityLevel = sensitivityLevel; }
    public String getPreviewPolicy() { return previewPolicy; }
    public void setPreviewPolicy(String previewPolicy) { this.previewPolicy = previewPolicy; }
}
