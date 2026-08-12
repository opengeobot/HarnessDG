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

/** 分类值（03 §4.2）：value_key 稳定，display_name 可本地化；被引用值只能 deprecated。 */
@Entity
@Table(name = "taxonomy_values")
public class TaxonomyValueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "taxonomy_id", nullable = false)
    private Long taxonomyId;

    @Column(name = "value_key", nullable = false)
    private String valueKey;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private String status;

    @Column(name = "valid_from")
    private OffsetDateTime validFrom;

    @Column(name = "valid_to")
    private OffsetDateTime validTo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String aliases;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String metadata;

    public Long getId() { return id; }
    public Long getTaxonomyId() { return taxonomyId; }
    public void setTaxonomyId(Long taxonomyId) { this.taxonomyId = taxonomyId; }
    public String getValueKey() { return valueKey; }
    public void setValueKey(String valueKey) { this.valueKey = valueKey; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getValidFrom() { return validFrom; }
    public void setValidFrom(OffsetDateTime validFrom) { this.validFrom = validFrom; }
    public OffsetDateTime getValidTo() { return validTo; }
    public void setValidTo(OffsetDateTime validTo) { this.validTo = validTo; }
    public String getAliases() { return aliases; }
    public void setAliases(String aliases) { this.aliases = aliases; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
}
