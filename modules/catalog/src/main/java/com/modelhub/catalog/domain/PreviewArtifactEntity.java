package com.modelhub.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * 预览产物（05 §5）：repositoryId + refName 下按 version 递增；
 * 版本键 sourceCommitSha + manifestHash + policyVersion，禁止仅按 repositoryId 覆盖。
 * 迟到旧任务保护：只有 (repo, ref) 最新行的 job_id 拥有写入权（05 §5.1）。
 */
@Entity
@Table(name = "preview_artifacts")
public class PreviewArtifactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repository_id", nullable = false)
    private Long repositoryId;

    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "artifact_key", nullable = false)
    private String artifactKey;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "source_commit_sha")
    private String sourceCommitSha;

    @Column(name = "manifest_hash")
    private String manifestHash;

    @Column(name = "policy_version", nullable = false)
    private int policyVersion = 1;

    @Column(nullable = false)
    private String status = "pending";

    @Column(name = "row_count")
    private Long rowCount;

    @Column(name = "sample_count")
    private Long sampleCount;

    @Column(name = "sample_strategy")
    private String sampleStrategy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String sample;

    @Column(name = "ref_name")
    private String refName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public Long getRepositoryId() { return repositoryId; }
    public void setRepositoryId(Long repositoryId) { this.repositoryId = repositoryId; }
    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }
    public String getArtifactKey() { return artifactKey; }
    public void setArtifactKey(String artifactKey) { this.artifactKey = artifactKey; }
    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }
    public String getSourceCommitSha() { return sourceCommitSha; }
    public void setSourceCommitSha(String sourceCommitSha) { this.sourceCommitSha = sourceCommitSha; }
    public String getManifestHash() { return manifestHash; }
    public void setManifestHash(String manifestHash) { this.manifestHash = manifestHash; }
    public int getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(int policyVersion) { this.policyVersion = policyVersion; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getRowCount() { return rowCount; }
    public void setRowCount(Long rowCount) { this.rowCount = rowCount; }
    public Long getSampleCount() { return sampleCount; }
    public void setSampleCount(Long sampleCount) { this.sampleCount = sampleCount; }
    public String getSampleStrategy() { return sampleStrategy; }
    public void setSampleStrategy(String sampleStrategy) { this.sampleStrategy = sampleStrategy; }
    public String getSample() { return sample; }
    public void setSample(String sample) { this.sample = sample; }
    public String getRefName() { return refName; }
    public void setRefName(String refName) { this.refName = refName; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
