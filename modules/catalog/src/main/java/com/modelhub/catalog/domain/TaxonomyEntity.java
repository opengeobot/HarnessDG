package com.modelhub.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** 分类定义（03 §4.1）。 */
@Entity
@Table(name = "taxonomies")
public class TaxonomyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "taxonomy_key", nullable = false, unique = true)
    private String taxonomyKey;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public Long getId() { return id; }
    public String getTaxonomyKey() { return taxonomyKey; }
    public void setTaxonomyKey(String taxonomyKey) { this.taxonomyKey = taxonomyKey; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
