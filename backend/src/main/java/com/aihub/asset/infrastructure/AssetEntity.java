/*
 * 功能: 资产持久化实体，映射 asset 表（MyBatis-Plus 单表 CRUD/逻辑删除）。
 * 时间: 2026-06-29
 * 作者: AxeXie
 */
package com.aihub.asset.infrastructure;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import java.util.List;

/**
 * 资产持久化实体（{@code asset} 表）。
 *
 * <p>仅用于 infrastructure 与数据库映射，绝不跨越模块边界对外返回。{@code owners}/{@code tags}
 * 经 {@link JsonbStringListTypeHandler} 与 jsonb 列互转，故开启 {@code autoResultMap}。
 */
@TableName(value = "asset", autoResultMap = true)
public class AssetEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("asset_id")
    private String assetId;

    @TableField("type")
    private String type;

    @TableField("namespace")
    private String namespace;

    @TableField("name")
    private String name;

    @TableField("display_name")
    private String displayName;

    @TableField("description")
    private String description;

    @TableField("visibility")
    private String visibility;

    @TableField("status")
    private String status;

    @TableField(value = "owners", typeHandler = JsonbStringListTypeHandler.class)
    private List<String> owners;

    @TableField(value = "tags", typeHandler = JsonbStringListTypeHandler.class)
    private List<String> tags;

    @TableField("license")
    private String license;

    @TableField("repo_full_name")
    private String repoFullName;

    @TableField("repo_html_url")
    private String repoHtmlUrl;

    @TableField("repo_clone_url")
    private String repoCloneUrl;

    @TableField("row_version")
    private Long rowVersion;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;

    @TableLogic
    @TableField("deleted")
    private Integer deleted;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<String> getOwners() {
        return owners;
    }

    public void setOwners(List<String> owners) {
        this.owners = owners;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getLicense() {
        return license;
    }

    public void setLicense(String license) {
        this.license = license;
    }

    public String getRepoFullName() {
        return repoFullName;
    }

    public void setRepoFullName(String repoFullName) {
        this.repoFullName = repoFullName;
    }

    public String getRepoHtmlUrl() {
        return repoHtmlUrl;
    }

    public void setRepoHtmlUrl(String repoHtmlUrl) {
        this.repoHtmlUrl = repoHtmlUrl;
    }

    public String getRepoCloneUrl() {
        return repoCloneUrl;
    }

    public void setRepoCloneUrl(String repoCloneUrl) {
        this.repoCloneUrl = repoCloneUrl;
    }

    public Long getRowVersion() {
        return rowVersion;
    }

    public void setRowVersion(Long rowVersion) {
        this.rowVersion = rowVersion;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
