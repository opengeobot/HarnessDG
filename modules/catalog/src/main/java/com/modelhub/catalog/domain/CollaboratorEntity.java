package com.modelhub.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;

/** 仓库协作者（02 §2/§3.3）：显式、可审计、可过期；subject 可为 user 或 organization。 */
@Entity
@Table(name = "repository_collaborators")
public class CollaboratorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repository_id", nullable = false)
    private Long repositoryId;

    @Column(name = "subject_type", nullable = false)
    private String subjectType;

    @Column(name = "subject_user_id")
    private Long subjectUserId;

    @Column(name = "subject_organization_id")
    private Long subjectOrganizationId;

    @Column(nullable = false)
    private String role;

    @Column(name = "granted_by")
    private Long grantedBy;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public Long getRepositoryId() { return repositoryId; }
    public void setRepositoryId(Long repositoryId) { this.repositoryId = repositoryId; }
    public String getSubjectType() { return subjectType; }
    public void setSubjectType(String subjectType) { this.subjectType = subjectType; }
    public Long getSubjectUserId() { return subjectUserId; }
    public void setSubjectUserId(Long subjectUserId) { this.subjectUserId = subjectUserId; }
    public Long getSubjectOrganizationId() { return subjectOrganizationId; }
    public void setSubjectOrganizationId(Long subjectOrganizationId) { this.subjectOrganizationId = subjectOrganizationId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Long getGrantedBy() { return grantedBy; }
    public void setGrantedBy(Long grantedBy) { this.grantedBy = grantedBy; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public Long getVersion() { return version; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
